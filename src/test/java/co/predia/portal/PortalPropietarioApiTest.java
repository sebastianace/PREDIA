package co.predia.portal;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.HipotecaVigente;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioService;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.RequisitoNegocio;
import co.predia.cierre.negocio.RequisitoNegocioRepository;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.clientes.Cliente;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El portal del propietario: ve solo su venta, nada del resto de la inmobiliaria, y lo que envía queda en revisión.
 * La matriz de acceso comprueba que el propietario no puede salirse del portal ni el personal entrar a él.
 */
class PortalPropietarioApiTest extends PruebaIntegracion {

    @MockitoBean JavaMailSender correo;
    @Autowired RequisitoNegocioRepository requisitosNegocio;

    private record Escenario(Inmobiliaria inmobiliaria, Usuario asesor, Propietario propietario, Usuario usuario,
                             Negocio negocio, Inmueble inmueble) {
    }

    /** Un negocio abierto con comprador, y el propietario con su cuenta del portal ya activa. */
    private Escenario escenario(String nombre) {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Portal " + nombre);
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Propietario " + nombre,
                "propietario-" + java.util.UUID.randomUUID() + "@prueba.test", TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Inmueble inmueble = crearInmueble(inmobiliaria, "POR-" + nombre);
        inmueble.setPropiedadHorizontal(true);
        inmuebles.save(inmueble);
        Cliente comprador = crearCliente(inmobiliaria, asesor, "Comprador Secreto " + nombre, "8000000", "100000000");
        Negocio negocio = negocioService.crear(new UsuarioActual(asesor.getId(), inmobiliaria.getId(), asesor.getNombre(),
                        asesor.getEmail(), asesor.getRol()),
                new NegocioService.Datos(inmueble.getId(), propietario.getId(), comprador.getId(), hoy().plusDays(20),
                        false, HipotecaVigente.NO, null));
        Usuario usuario = Usuario.invitado(inmobiliaria.getId(), propietario.getNombre(), propietario.getEmail(),
                Rol.PROPIETARIO, propietario.getId(), asesor.getId());
        usuario.fijarContrasena(passwordEncoder.encode(PASSWORD_PRUEBA));
        usuario = usuarios.save(usuario);
        return new Escenario(inmobiliaria, asesor, propietario, usuario, negocio, inmueble);
    }

    private static MockMultipartFile pdf(String nombre) {
        return new MockMultipartFile("archivo", nombre, "application/pdf",
                pdfConTexto("PAZ Y SALVO DE ADMINISTRACION", "El inmueble esta a paz y salvo " + nombre + "."));
    }

    @Test
    void elPropietarioVeSuVentaYSusDocumentosPeroNoAlComprador() throws Exception {
        Escenario e = escenario("Ve");

        String lista = mvc.perform(get("/api/portal/negocios").header("Authorization", bearer(e.usuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(e.negocio().getId()))
                .andExpect(jsonPath("$[0].inmueble").value(e.inmueble().getTitulo()))
                .andExpect(jsonPath("$[0].asesorNombre").value(e.asesor().getNombre()))
                .andExpect(jsonPath("$[0].semaforo").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String detalle = mvc.perform(get("/api/portal/negocios/" + e.negocio().getId())
                        .header("Authorization", bearer(e.usuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos[0].nombre").isNotEmpty())
                .andExpect(jsonPath("$.documentos[0].descripcion").exists())
                .andExpect(jsonPath("$.aviso").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // El comprador es un dato personal de otra persona: no aparece en ninguna respuesta del portal.
        assertThat(lista).doesNotContain("Comprador Secreto");
        assertThat(detalle).doesNotContain("Comprador Secreto").doesNotContain("\"nota\"");
    }

    @Test
    void unPropietarioNoVeLaVentaDeOtroNiDeOtraInmobiliaria() throws Exception {
        Escenario a = escenario("A");
        Escenario b = escenario("B");
        // Otro propietario de la MISMA inmobiliaria que A, con su propia cuenta.
        Propietario vecino = crearPropietario(a.inmobiliaria(), a.asesor(), "Vecino", "vecino-"
                + java.util.UUID.randomUUID() + "@prueba.test", TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Usuario usuarioVecino = Usuario.invitado(a.inmobiliaria().getId(), "Vecino", vecino.getEmail(), Rol.PROPIETARIO,
                vecino.getId(), null);
        usuarioVecino.fijarContrasena(passwordEncoder.encode(PASSWORD_PRUEBA));
        usuarioVecino = usuarios.save(usuarioVecino);

        mvc.perform(get("/api/portal/negocios/" + a.negocio().getId()).header("Authorization", bearer(usuarioVecino)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/portal/negocios").header("Authorization", bearer(usuarioVecino)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/portal/negocios/" + a.negocio().getId()).header("Authorization", bearer(b.usuario())))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/portal/negocios/" + a.negocio().getId() + "/requisitos/1/documento").file(pdf("x.pdf"))
                        .header("Authorization", bearer(b.usuario())))
                .andExpect(status().isNotFound());
    }

    @Test
    void loQueEnviaElPropietarioQuedaEnRevisionNuncaRecibidoYNoSaleNingunCorreo() throws Exception {
        Escenario e = escenario("Envia");
        String detalle = mvc.perform(get("/api/portal/negocios/" + e.negocio().getId())
                        .header("Authorization", bearer(e.usuario())))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(detalle, "$.documentos[?(@.puedeEnviar == true)].id");
        Integer requisitoId = ids.getFirst();

        mvc.perform(multipart("/api/portal/negocios/" + e.negocio().getId() + "/requisitos/" + requisitoId + "/documento")
                        .file(pdf("paz-y-salvo.pdf")).param("fechaExpedicion", hoy().minusDays(2).toString())
                        .header("Authorization", bearer(e.usuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos[?(@.id == " + requisitoId + ")].estado").value("SUGERIDO"))
                .andExpect(jsonPath("$.documentos[?(@.id == " + requisitoId + ")].enviadoPorUsted").value(true));

        RequisitoNegocio fila = requisitosNegocio.findById(requisitoId.longValue()).orElseThrow();
        assertThat(fila.getEstadoRegistrado().name()).isEqualTo("SUGERIDO");
        assertThat(fila.getSugeridoPor().name()).isEqualTo("PROPIETARIO");
        assertThat(fila.getRecibidoPor()).isNull();

        // El asesor lo ve como sugerencia del propietario, para revisarlo.
        mvc.perform(get("/api/negocios/" + e.negocio().getId() + "/estado-documental")
                        .header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$.items[?(@.id == " + requisitoId + ")].sugeridoPor").value("PROPIETARIO"))
                .andExpect(jsonPath("$.items[?(@.id == " + requisitoId + ")].estadoRegistrado").value("SUGERIDO"));
        verifyNoInteractions(correo);
    }

    @Test
    void noPuedeReemplazarUnDocumentoRecibidoYVigente() throws Exception {
        Escenario e = escenario("Vigente");
        RequisitoNegocio cedula = requisitosNegocio.findByNegocioId(e.negocio().getId()).stream()
                .filter(r -> catalogoCodigo(r).equals("CEDULA_VENDEDOR")).findFirst().orElseThrow();
        cedula.recibir(null, null, e.asesor().getId(), null);
        requisitosNegocio.save(cedula);

        mvc.perform(multipart("/api/portal/negocios/" + e.negocio().getId() + "/requisitos/" + cedula.getId() + "/documento")
                        .file(pdf("cedula.pdf")).header("Authorization", bearer(e.usuario())))
                .andExpect(status().isUnprocessableEntity());
        assertThat(requisitosNegocio.findById(cedula.getId()).orElseThrow().getEstadoRegistrado().name())
                .isEqualTo("RECIBIDO");
    }

    @Autowired co.predia.cierre.catalogo.RequisitoCatalogoRepository catalogo;

    private String catalogoCodigo(RequisitoNegocio r) {
        return catalogo.findById(r.getRequisitoId()).orElseThrow().getCodigo();
    }

    @Test
    void soloVeLosRecordatoriosQueDeVerdadSeLeEnviaron() throws Exception {
        Escenario e = escenario("Recordatorios");
        String borrador = mvc.perform(post("/api/negocios/" + e.negocio().getId() + "/recordatorios")
                        .header("Authorization", bearer(e.asesor())))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(borrador, "$.id");

        mvc.perform(get("/api/portal/negocios/" + e.negocio().getId() + "/recordatorios")
                        .header("Authorization", bearer(e.usuario())))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(e.asesor())))
                .andExpect(status().isOk());
        verify(correo, times(1)).send(any(SimpleMailMessage.class));
        mvc.perform(get("/api/portal/negocios/" + e.negocio().getId() + "/recordatorios")
                        .header("Authorization", bearer(e.usuario())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].asunto").isNotEmpty())
                .andExpect(jsonPath("$[0].enviadoEn").isNotEmpty());
    }

    @Test
    void desactivarAlPropietarioCortaSuSesionAlInstante() throws Exception {
        Escenario e = escenario("Desactivar");
        String token = bearer(e.usuario());
        mvc.perform(get("/api/portal/negocios").header("Authorization", token)).andExpect(status().isOk());

        mvc.perform(put("/api/propietarios/" + e.propietario().getId() + "/acceso").header("Authorization", bearer(e.asesor()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DESACTIVADO"));

        mvc.perform(get("/api/portal/negocios").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void matrizDeAccesoElPropietarioNoSaleDelPortalYElPersonalNoEntra() throws Exception {
        Escenario e = escenario("Matriz");
        String propietario = bearer(e.usuario());
        String asesor = bearer(e.asesor());
        String admin = bearer(crearUsuario(e.inmobiliaria(), Rol.ADMIN));

        for (String ruta : List.of("/api/negocios", "/api/clientes", "/api/inmuebles", "/api/propietarios",
                "/api/recordatorios", "/api/resumen-diario", "/api/tasas", "/api/requisitos", "/api/usuarios",
                "/api/negocios/" + e.negocio().getId() + "/estado-documental",
                "/api/inmuebles/" + e.inmueble().getId() + "/foto", "/api/documentos?inmuebleId=" + e.inmueble().getId())) {
            mvc.perform(get(ruta).header("Authorization", propietario))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/consultas").header("Authorization", propietario).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\": \"¿Cuánto vale?\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/asistente/mensajes").header("Authorization", propietario)
                .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\": \"Hola\"}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/documentos").file(pdf("x.pdf")).param("inmuebleId", e.inmueble().getId().toString())
                .header("Authorization", propietario)).andExpect(status().isForbidden());

        // El personal no entra al portal, y el asesor no administra usuarios.
        mvc.perform(get("/api/portal/negocios").header("Authorization", asesor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/portal/negocios").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").header("Authorization", asesor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").header("Authorization", admin)).andExpect(status().isOk());
        // Sin sesión, ni el portal.
        mvc.perform(get("/api/portal/negocios")).andExpect(status().isUnauthorized());
        // El perfil sí lo puede consultar cualquiera con sesión.
        mvc.perform(get("/api/auth/yo").header("Authorization", propietario))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("PROPIETARIO"));
    }
}
