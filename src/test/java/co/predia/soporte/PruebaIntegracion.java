package co.predia.soporte;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.HipotecaVigente;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.NegocioService;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.clientes.Intencion;
import co.predia.comun.ConfiguracionReloj;
import co.predia.comun.seguridad.ServicioJwt;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.credito.TasaCredito;
import co.predia.credito.TasaCreditoRepository;
import co.predia.credito.TipoTasa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import co.predia.inventario.TipoInmueble;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Base de las pruebas de integración: PostgreSQL 16 + pgvector real en un contenedor desechable
 * (compartido por todas las clases de prueba). Requiere Docker en ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaIntegracion {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    public static final String PASSWORD_PRUEBA = "ClaveDePrueba123";

    @Autowired protected MockMvc mvc;
    @Autowired protected InmobiliariaRepository inmobiliarias;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected ParametrosInmobiliariaRepository parametros;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ServicioJwt servicioJwt;
    @Autowired protected InmuebleRepository inmuebles;
    @Autowired protected ClienteRepository clientes;
    @Autowired protected TasaCreditoRepository tasas;
    @Autowired protected PropietarioRepository propietarios;
    @Autowired protected NegocioRepository negocios;
    @Autowired protected NegocioService negocioService;

    /** Crea una inmobiliaria nueva (NIT único) con sus parámetros por defecto. */
    protected Inmobiliaria crearInmobiliaria(String nombre) {
        Inmobiliaria i = inmobiliarias.save(new Inmobiliaria(nombre, "T-" + UUID.randomUUID().toString().substring(0, 15), "Bucaramanga"));
        parametros.save(new ParametrosInmobiliaria(i.getId()));
        return i;
    }

    protected Usuario crearUsuario(Inmobiliaria inmobiliaria, Rol rol) {
        String email = rol.name().toLowerCase() + "-" + UUID.randomUUID() + "@prueba.test";
        return usuarios.save(new Usuario(inmobiliaria.getId(), "Usuario " + rol, email,
                passwordEncoder.encode(PASSWORD_PRUEBA), rol));
    }

    /** Cabecera Authorization con un JWT válido para el usuario. */
    protected String bearer(Usuario usuario) {
        return "Bearer " + servicioJwt.emitir(usuario).token();
    }

    protected Inmueble crearInmueble(Inmobiliaria inmobiliaria, String codigo) {
        Inmueble i = new Inmueble(inmobiliaria.getId(), codigo, "Apto de prueba " + codigo, TipoInmueble.APARTAMENTO,
                Operacion.VENTA);
        i.setDireccion("Calle 1 # 2-3");
        i.setBarrio("Centro");
        i.setCiudad("Bucaramanga");
        i.setAreaM2(new BigDecimal("60"));
        i.setHabitaciones((short) 2);
        i.setPrecioVenta(new BigDecimal("200000000"));
        return inmuebles.save(i);
    }

    /** Un PDF válido, con texto que PDFBox puede extraer directamente (sin necesitar OCR). */
    protected static byte[] pdfConTexto(String... lineas) {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                contenido.newLineAtOffset(50, 720);
                for (String linea : lineas) {
                    contenido.showText(linea);
                    contenido.newLineAtOffset(0, -16);
                }
                contenido.endText();
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Varias líneas que, repetidas, dan un texto largo (para probar la fragmentación con un PDF real). */
    protected static String[] parrafoLargo(String tema, int lineas) {
        return java.util.stream.IntStream.range(0, lineas)
                .mapToObj(i -> "%s: el inmueble numero %d tiene balcon y vista a la montana.".formatted(tema, i))
                .toArray(String[]::new);
    }

    /** Un cliente con la autorización de datos ya registrada, como exige la base de datos. */
    protected Cliente crearCliente(Inmobiliaria inmobiliaria, Usuario registradoPor, String nombre, String ingreso,
                                   String ahorro) {
        Cliente c = new Cliente(inmobiliaria.getId(), registradoPor.getId());
        c.actualizarPerfil(nombre, null, null, new BigDecimal(ingreso), new BigDecimal(ahorro), BigDecimal.ZERO,
                Intencion.COMPRAR, (short) 3, List.of("Floridablanca"), "Apartamento con parqueadero");
        return clientes.save(c);
    }

    protected TasaCredito crearTasa(Inmobiliaria inmobiliaria, String banco, TipoTasa tipo, String tasaEa) {
        return tasas.save(new TasaCredito(inmobiliaria.getId(), banco, tipo, new BigDecimal(tasaEa),
                LocalDate.of(2026, 1, 1), "Fuente de prueba", true, null));
    }

    protected static Long idDe(MvcResult resultado) throws Exception {
        return ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(resultado.getResponse().getContentAsString(), java.util.Map.class).get("id")).longValue();
    }

    /** "Hoy" como lo ven las reglas del cierre: en la hora de Colombia. */
    protected static LocalDate hoy() {
        return LocalDate.now(ConfiguracionReloj.ZONA_COLOMBIA);
    }

    protected Propietario crearPropietario(Inmobiliaria inmobiliaria, Usuario registradoPor, String nombre,
                                           String email, TipoPersona tipo, EstadoCivil estadoCivil) {
        Propietario p = new Propietario(inmobiliaria.getId(), registradoPor.getId());
        p.actualizar(tipo, nombre, tipo == TipoPersona.NATURAL ? "CC" : "NIT", "1234567890", email, "3000000000",
                estadoCivil);
        return propietarios.save(p);
    }

    /** Un negocio con la lista de requisitos ya instanciada; el inmueble queda como propiedad horizontal. */
    protected Negocio crearNegocio(Inmobiliaria inmobiliaria, Usuario asesor, Inmueble inmueble,
                                   Propietario propietario, LocalDate fechaFirma) {
        inmueble.setPropiedadHorizontal(true);
        inmuebles.save(inmueble);
        return negocioService.crear(new UsuarioActual(asesor.getId(), inmobiliaria.getId(), asesor.getNombre(),
                        asesor.getEmail(), asesor.getRol()),
                new NegocioService.Datos(inmueble.getId(), propietario.getId(), null, fechaFirma, false,
                        HipotecaVigente.NO, null));
    }
}
