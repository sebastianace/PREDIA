package co.predia.comun.demo;

import co.predia.cierre.catalogo.CatalogoService;
import co.predia.cierre.catalogo.RequisitoCatalogo;
import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.HipotecaVigente;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.NegocioService;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.cierre.negocio.RequisitoNegocio;
import co.predia.cierre.negocio.RequisitoNegocioRepository;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.cierre.recordatorios.ServicioRecordatorios;
import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.clientes.Intencion;
import co.predia.credito.TasaCredito;
import co.predia.credito.TasaCreditoRepository;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.credito.TipoTasa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.EstadoInmueble;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import co.predia.inventario.TipoInmueble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Siembra los datos FICTICIOS de la demo (Altos Inmobiliaria, Bucaramanga). Es idempotente, por partes:
 * crea lo que falte (inmobiliaria base, clientes, tasas) sin tocar lo que ya existe, así una base creada en una
 * fase anterior recibe los datos nuevos. Se desactiva con DEMO_HABILITADA=false.
 */
@Component
@ConditionalOnProperty(name = "predia.demo.habilitada", havingValue = "true")
public class DatosDemo implements ApplicationRunner {

    public static final String NIT_ALTOS = "900000001-1";
    public static final String EMAIL_ADMIN = "admin@altos.test";
    public static final String EMAIL_LAURA = "laura@altos.test";

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    private final InmobiliariaRepository inmobiliarias;
    private final UsuarioRepository usuarios;
    private final ParametrosInmobiliariaRepository parametros;
    private final InmuebleRepository inmuebles;
    private final ClienteRepository clientes;
    private final TasaCreditoRepository tasas;
    private final NegocioRepository negocios;
    private final NegocioService negocioService;
    private final PropietarioRepository propietarios;
    private final RequisitoNegocioRepository requisitosNegocio;
    private final CatalogoService catalogoService;
    private final ServicioRecordatorios recordatorios;
    private final Clock reloj;
    private final PasswordEncoder passwordEncoder;
    private final String passwordDemo;

    public DatosDemo(InmobiliariaRepository inmobiliarias, UsuarioRepository usuarios,
                     ParametrosInmobiliariaRepository parametros, InmuebleRepository inmuebles,
                     ClienteRepository clientes, TasaCreditoRepository tasas, NegocioRepository negocios,
                     NegocioService negocioService, PropietarioRepository propietarios,
                     RequisitoNegocioRepository requisitosNegocio, CatalogoService catalogoService,
                     ServicioRecordatorios recordatorios, Clock reloj,
                     PasswordEncoder passwordEncoder, @Value("${predia.demo.password:}") String passwordDemo) {
        this.inmobiliarias = inmobiliarias;
        this.usuarios = usuarios;
        this.parametros = parametros;
        this.inmuebles = inmuebles;
        this.clientes = clientes;
        this.tasas = tasas;
        this.negocios = negocios;
        this.negocioService = negocioService;
        this.propietarios = propietarios;
        this.requisitosNegocio = requisitosNegocio;
        this.catalogoService = catalogoService;
        this.recordatorios = recordatorios;
        this.reloj = reloj;
        this.passwordEncoder = passwordEncoder;
        this.passwordDemo = passwordDemo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Inmobiliaria altos = inmobiliarias.findByNit(NIT_ALTOS).orElse(null);
        if (altos == null) {
            if (passwordDemo == null || passwordDemo.isBlank()) {
                log.warn("No se sembraron los datos de demo: falta DEMO_PASSWORD en el archivo .env.");
                return;
            }
            altos = crearBase();
        }
        sembrarClientesYTasas(altos.getId());
        sembrarNegocioDelApto1102(altos.getId());
    }

    private Inmobiliaria crearBase() {
        Inmobiliaria altos = inmobiliarias.save(new Inmobiliaria("Altos Inmobiliaria", NIT_ALTOS, "Bucaramanga"));
        Long id = altos.getId();

        String hash = passwordEncoder.encode(passwordDemo);
        usuarios.save(new Usuario(id, "Administrador Altos", EMAIL_ADMIN, hash, Rol.ADMIN));
        usuarios.save(new Usuario(id, "Laura Gómez", EMAIL_LAURA, hash, Rol.ASESOR));

        ParametrosInmobiliaria p = new ParametrosInmobiliaria(id);
        p.actualizar(new BigDecimal("0.30"), new BigDecimal("0.30"), new BigDecimal("0.20"), 20, 7,
                List.of("Bucaramanga"), new BigDecimal("0.300"), "notificaciones@altos.test");
        parametros.save(p);

        sembrarInmuebles(id);
        log.info("Datos de demo sembrados para Altos Inmobiliaria (todos son ficticios).");
        return altos;
    }

    private void sembrarClientesYTasas(Long id) {
        Usuario laura = usuarios.findByEmailIgnoreCaseAndActivoTrue(EMAIL_LAURA).orElse(null);
        if (laura == null) {
            return;
        }
        if (clientes.countByInmobiliariaId(id) == 0) {
            // Paola y Juan: ingreso familiar $7.500.000 y ahorro $90.000.000 (con su autorización de datos).
            Cliente paolaYJuan = new Cliente(id, laura.getId());
            paolaYJuan.actualizarPerfil("Paola y Juan", "paolayjuan@ejemplo.test", "3000000000",
                    new BigDecimal("7500000"), new BigDecimal("90000000"), BigDecimal.ZERO, Intencion.COMPRAR,
                    (short) 3, List.of("Floridablanca"),
                    "Apartamento de tres habitaciones con parqueadero, que acepte mascotas y esté cerca de colegios.");
            clientes.save(paolaYJuan);
            log.info("Cliente de demo sembrado: Paola y Juan (ficticio).");
        }
        if (tasas.countByInmobiliariaId(id) == 0) {
            tasas.save(new TasaCredito(id, "Banco Ejemplo", TipoTasa.NO_VIS, new BigDecimal("0.12"),
                    LocalDate.of(2026, 1, 1), "Tasa de ejemplo para la demo; no es una tasa real", true, null));
            log.info("Tasa de ejemplo sembrada: Banco Ejemplo, 12 % E.A.");
        }
    }

    /**
     * El negocio de la demo: Apto 1102 de Cañaveral, con el certificado de tradición vencido y el paz y salvo de
     * administración pendiente. Lo demás ya está entregado, así el semáforo dice "Faltan 2 documentos".
     */
    private void sembrarNegocioDelApto1102(Long id) {
        if (negocios.countByInmobiliariaId(id) > 0) {
            return;
        }
        Usuario laura = usuarios.findByEmailIgnoreCaseAndActivoTrue(EMAIL_LAURA).orElse(null);
        Inmueble apto = inmuebles.findByInmobiliariaIdOrderByTituloAsc(id).stream()
                .filter(i -> "APT-1102".equals(i.getCodigo())).findFirst().orElse(null);
        Cliente comprador = clientes.findByInmobiliariaIdOrderByNombreAsc(id).stream().findFirst().orElse(null);
        if (laura == null || apto == null) {
            return;
        }
        LocalDate hoy = LocalDate.now(reloj);

        Propietario jorge = new Propietario(id, laura.getId());
        jorge.actualizar(TipoPersona.NATURAL, "Jorge Ramírez Pardo", "CC", "91234567", "jorge.ramirez@ejemplo.test",
                "3001112233", EstadoCivil.SOLTERO);
        propietarios.save(jorge);

        Negocio negocio = negocioService.crear(
                new UsuarioActual(laura.getId(), id, laura.getNombre(), laura.getEmail(), laura.getRol()),
                new NegocioService.Datos(apto.getId(), jorge.getId(), comprador == null ? null : comprador.getId(),
                        hoy.plusDays(12), false, HipotecaVigente.NO, null));

        Map<String, RequisitoNegocio> filas = filasPorCodigo(id, negocio.getId());
        filas.get("CEDULA_VENDEDOR").recibir(null, null, laura.getId(), null);
        filas.get("ESCRITURA_ADQUISICION").recibir(null, null, laura.getId(), null);
        filas.get("PREDIAL").recibir(null, hoy, laura.getId(), null);
        filas.get("PROMESA_COMPRAVENTA").recibir(null, null, laura.getId(), null);
        // Expedido hace 45 días: con 30 de vigencia ya está VENCIDO.
        filas.get("CERTIFICADO_TRADICION").recibir(null, hoy.minusDays(45), laura.getId(), null);
        requisitosNegocio.saveAll(filas.values());
        log.info("Negocio de demo sembrado: Apto 1102 Cañaveral (ficticio), con 2 documentos por resolver.");

        // Un borrador de recordatorio SUGERIDO ya espera en la bandeja del asesor (no se envía nada).
        recordatorios.generar(negocio);
    }

    private Map<String, RequisitoNegocio> filasPorCodigo(Long inmobiliariaId, Long negocioId) {
        Map<Long, String> codigos = catalogoService.asegurar(inmobiliariaId).stream()
                .collect(Collectors.toMap(RequisitoCatalogo::getId, RequisitoCatalogo::getCodigo));
        return requisitosNegocio.findByNegocioId(negocioId).stream()
                .collect(Collectors.toMap(f -> codigos.get(f.getRequisitoId()), f -> f));
    }

    private void sembrarInmuebles(Long id) {
        inmuebles.saveAll(List.of(
                venta(id, "APT-1102", "Apto 1102 Cañaveral", TipoInmueble.APARTAMENTO,
                        "Calle 30 # 26-15, Torre 2, Apto 1102", "Cañaveral", "Floridablanca", 5, "78", 3, 2, 1, true,
                        "320000", "300000000", true, EstadoInmueble.DISPONIBLE,
                        "Apartamento iluminado en piso 11 con balcón y vista a la ciudad. Cocina abierta, "
                                + "estudio pequeño, un parqueadero cubierto y depósito. Conjunto con piscina, gimnasio "
                                + "y zona infantil, a dos cuadras del centro comercial Cañaveral. Admite mascotas."),
                venta(id, "CAS-LAG2", "Casa Lagos II", TipoInmueble.CASA,
                        "Carrera 9 # 4-20", "Lagos II", "Floridablanca", 4, "120", 4, 3, 2, true,
                        "0", "330000000", false, EstadoInmueble.DISPONIBLE,
                        "Casa de dos pisos con patio y terraza, ideal para familia con mascotas. Garaje para "
                                + "dos carros, cuarto de servicio y calle tranquila cerca de colegios y parques."),
                venta(id, "APT-CAB", "Apto Cabecera", TipoInmueble.APARTAMENTO,
                        "Calle 48 # 35-40, Apto 702", "Cabecera del Llano", "Bucaramanga", 6, "95", 3, 2, 2, false,
                        "450000", "380000000", true, EstadoInmueble.VENDIDO,
                        "Apartamento exterior en zona comercial de Cabecera, cerca de restaurantes y bancos."),
                venta(id, "APT-PRO", "Apto Provenza", TipoInmueble.APARTAMENTO,
                        "Carrera 22 # 105-30, Apto 504", "Provenza", "Bucaramanga", 4, "68", 3, 2, 1, false,
                        "210000", "268000000", true, EstadoInmueble.DISPONIBLE,
                        "Apartamento remodelado con buena ventilación, parqueadero comunal asignado, cerca de "
                                + "transporte público, supermercados y la autopista a Floridablanca."),
                arriendo(id, "APT-RMI", "Apto Real de Minas", "Calle 57 # 4W-10, Apto 302", "Real de Minas",
                        "Bucaramanga", 4, "70", 3, 2, 1, "190000", "1800000", EstadoInmueble.ARRENDADO,
                        "Apartamento para arriendo en conjunto cerrado con zonas verdes."),
                venta(id, "APT-CIU", "Apto Ciudadela", TipoInmueble.APARTAMENTO,
                        "Carrera 15 # 60-12, Apto 801", "La Ciudadela", "Bucaramanga", 3, "62", 3, 2, 0, true,
                        "180000", "240000000", true, EstadoInmueble.DISPONIBLE,
                        "Apartamento esquinero con tres habitaciones, cerca de colegios y del parque de la "
                                + "Ciudadela. Sin parqueadero propio; hay parqueadero de visitantes. Admite mascotas.")
        ));
    }

    private static Inmueble venta(Long inmobiliariaId, String codigo, String titulo, TipoInmueble tipo,
                                  String direccion, String barrio, String ciudad, int estrato, String area,
                                  int habitaciones, int banos, int parqueaderos, boolean mascotas,
                                  String administracion, String precio, boolean ph, EstadoInmueble estado,
                                  String descripcion) {
        Inmueble i = base(inmobiliariaId, codigo, titulo, tipo, Operacion.VENTA, direccion, barrio, ciudad, estrato,
                area, habitaciones, banos, parqueaderos, mascotas, administracion, ph, estado, descripcion);
        i.setPrecioVenta(new BigDecimal(precio));
        return i;
    }

    private static Inmueble arriendo(Long inmobiliariaId, String codigo, String titulo, String direccion,
                                     String barrio, String ciudad, int estrato, String area, int habitaciones,
                                     int banos, int parqueaderos, String administracion, String canon,
                                     EstadoInmueble estado, String descripcion) {
        Inmueble i = base(inmobiliariaId, codigo, titulo, TipoInmueble.APARTAMENTO, Operacion.ARRIENDO, direccion,
                barrio, ciudad, estrato, area, habitaciones, banos, parqueaderos, false, administracion, true, estado,
                descripcion);
        i.setCanonArriendo(new BigDecimal(canon));
        return i;
    }

    private static Inmueble base(Long inmobiliariaId, String codigo, String titulo, TipoInmueble tipo,
                                 Operacion operacion, String direccion, String barrio, String ciudad, int estrato,
                                 String area, int habitaciones, int banos, int parqueaderos, boolean mascotas,
                                 String administracion, boolean ph, EstadoInmueble estado, String descripcion) {
        Inmueble i = new Inmueble(inmobiliariaId, codigo, titulo, tipo, operacion);
        i.setDireccion(direccion);
        i.setBarrio(barrio);
        i.setCiudad(ciudad);
        i.setEstrato((short) estrato);
        i.setAreaM2(new BigDecimal(area));
        i.setHabitaciones((short) habitaciones);
        i.setBanos((short) banos);
        i.setParqueaderos((short) parqueaderos);
        i.setAceptaMascotas(mascotas);
        i.setValorAdministracion(new BigDecimal(administracion));
        i.setPropiedadHorizontal(ph);
        i.setEstado(estado);
        i.setDescripcion(descripcion);
        return i;
    }
}
