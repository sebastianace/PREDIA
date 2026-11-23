package co.predia.usuarios;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gestión del personal, invitaciones (crear y recuperar contraseña) y la invitación del propietario a su portal. */
class UsuariosEInvitacionesApiTest extends PruebaIntegracion {

    @MockitoBean JavaMailSender correo;
    @Autowired JdbcTemplate jdbc;

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private SimpleMailMessage ultimoCorreo() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private static String token(SimpleMailMessage m) {
        Matcher matcher = TOKEN.matcher(m.getText());
        assertThat(matcher.find()).as("el correo trae el enlace").isTrue();
        return matcher.group(1);
    }

    private static String correoNuevo() {
        return "persona-" + UUID.randomUUID() + "@prueba.test";
    }

    private String activar(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, password)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void elAdminInvitaAUnAsesorQueCreaSuContrasenaYEntra() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Usuarios Invitacion");
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);
        String email = correoNuevo();

        mvc.perform(post("/api/usuarios").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Carlos Pérez\", \"email\": \"%s\", \"rol\": \"ASESOR\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.estado").value("INVITADO"))
                .andExpect(jsonPath("$.usuario.invitacionVence").isNotEmpty())
                .andExpect(jsonPath("$.correoEnviado").value(true));
        SimpleMailMessage mensaje = ultimoCorreo();
        assertThat(mensaje.getTo()).containsExactly(email);
        String token = token(mensaje);

        // Sin contraseña todavía no puede entrar.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"cualquiera123\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        // En la base queda el hash del token, nunca el token.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invitacion_acceso WHERE token_hash = ?", Integer.class,
                token)).isZero();

        mvc.perform(get("/api/auth/invitaciones/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Carlos Pérez"))
                .andExpect(jsonPath("$.inmobiliaria").value(inmobiliaria.getNombre()))
                .andExpect(jsonPath("$.yaTieneCuenta").value(false));
        mvc.perform(post("/api/auth/activar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"password\": \"corta\"}".formatted(token)))
                .andExpect(status().isBadRequest());

        String respuesta = activar(token, "UnaClaveSegura2026");
        assertThat(JsonPath.<String>read(respuesta, "$.usuario.rol")).isEqualTo("ASESOR");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"UnaClaveSegura2026\"}".formatted(email)))
                .andExpect(status().isOk());
        // El enlace ya se usó.
        mvc.perform(get("/api/auth/invitaciones/" + token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[?(@.email == '%s')].estado".formatted(email)).value("ACTIVO"));
    }

    @Test
    void unEnlaceReemplazadoOVencidoNoSirveYReenviarloRecuperaLaContrasena() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Usuarios Enlaces");
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);
        String email = correoNuevo();
        String creado = mvc.perform(post("/api/usuarios").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Ana\", \"email\": \"%s\", \"rol\": \"ASESOR\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(creado, "$.usuario.id");
        String primero = token(ultimoCorreo());

        mvc.perform(post("/api/usuarios/" + id + "/invitacion").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        String segundo = token(ultimoCorreo());
        assertThat(segundo).isNotEqualTo(primero);
        mvc.perform(get("/api/auth/invitaciones/" + primero)).andExpect(status().isNotFound());

        // Vencido: igual que uno inexistente.
        jdbc.update("UPDATE invitacion_acceso SET expira_en = now() - interval '1 minute' WHERE usuario_id = ?", id);
        mvc.perform(get("/api/auth/invitaciones/" + segundo)).andExpect(status().isNotFound());

        // Ya con cuenta, reenviar la invitación le sirve para elegir una contraseña nueva.
        mvc.perform(post("/api/usuarios/" + id + "/invitacion").header("Authorization", bearer(admin)));
        activar(token(ultimoCorreo()), "PrimeraClave2026");
        mvc.perform(post("/api/usuarios/" + id + "/invitacion").header("Authorization", bearer(admin)));
        SimpleMailMessage recuperar = ultimoCorreo();
        assertThat(recuperar.getSubject()).contains("Nueva contraseña");
        mvc.perform(get("/api/auth/invitaciones/" + token(recuperar)))
                .andExpect(jsonPath("$.yaTieneCuenta").value(true));
        activar(token(recuperar), "SegundaClave2026");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"PrimeraClave2026\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"SegundaClave2026\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void reglasDelAdministrador() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Usuarios Reglas");
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        String tokenAsesor = bearer(asesor);

        // No puede quitarse su propio acceso, ni dejar la inmobiliaria sin administrador.
        mvc.perform(put("/api/usuarios/" + admin.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/usuarios/" + admin.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\": \"ASESOR\"}"))
                .andExpect(status().isUnprocessableEntity());
        // Ni crear propietarios desde aquí, ni repetir un correo.
        mvc.perform(post("/api/usuarios").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"X\", \"email\": \"%s\", \"rol\": \"PROPIETARIO\"}".formatted(correoNuevo())))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/usuarios").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"X\", \"email\": \"%s\", \"rol\": \"ASESOR\"}".formatted(asesor.getEmail())))
                .andExpect(status().isUnprocessableEntity());

        // Desactivar al asesor corta su sesión al instante; cambiarle el rol también (su token ya no corresponde).
        mvc.perform(get("/api/negocios").header("Authorization", tokenAsesor)).andExpect(status().isOk());
        mvc.perform(put("/api/usuarios/" + asesor.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DESACTIVADO"));
        mvc.perform(get("/api/negocios").header("Authorization", tokenAsesor)).andExpect(status().isUnauthorized());

        mvc.perform(put("/api/usuarios/" + asesor.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\": true, \"rol\": \"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMIN"));
        mvc.perform(get("/api/negocios").header("Authorization", tokenAsesor)).andExpect(status().isUnauthorized());

        // Un usuario de otra inmobiliaria no existe para este ADMIN.
        Usuario ajeno = crearUsuario(crearInmobiliaria("Usuarios Ajena"), Rol.ASESOR);
        mvc.perform(put("/api/usuarios/" + ajeno.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAsesorInvitaAlPropietarioQueEntraASuPortal() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Usuarios Propietario");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "INV-1");
        Propietario sinCorreo = crearPropietario(inmobiliaria, asesor, "Sin Correo", null, TipoPersona.NATURAL,
                EstadoCivil.SOLTERO);
        String email = correoNuevo();
        Propietario jorge = crearPropietario(inmobiliaria, asesor, "Jorge Ramírez", email, TipoPersona.NATURAL,
                EstadoCivil.SOLTERO);
        crearNegocio(inmobiliaria, asesor, inmueble, jorge, hoy().plusDays(15));

        mvc.perform(post("/api/propietarios/" + sinCorreo.getId() + "/invitacion").header("Authorization", bearer(asesor)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/propietarios/" + jorge.getId() + "/acceso").header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.estado").value("SIN_INVITAR"));

        // El asesor ve el correo antes de enviarlo; la vista previa no envía nada.
        mvc.perform(get("/api/propietarios/" + jorge.getId() + "/invitacion/vista-previa")
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.para").value(email))
                .andExpect(jsonPath("$.asunto", org.hamcrest.Matchers.containsString(inmueble.getTitulo())))
                .andExpect(jsonPath("$.cuerpo", org.hamcrest.Matchers.containsString("Hola, Jorge Ramírez")))
                .andExpect(jsonPath("$.cuerpo", org.hamcrest.Matchers.containsString(CorreoInvitaciones.MARCADOR_ENLACE)));
        verify(correo, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        mvc.perform(post("/api/propietarios/" + jorge.getId() + "/invitacion").header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceso.estado").value("INVITADO"))
                .andExpect(jsonPath("$.correoEnviado").value(true));
        SimpleMailMessage mensaje = ultimoCorreo();
        assertThat(mensaje.getTo()).containsExactly(email);
        assertThat(mensaje.getReplyTo()).isEqualTo(asesor.getEmail());

        String sesion = activar(token(mensaje), "ClaveDelPropietario1");
        assertThat(JsonPath.<String>read(sesion, "$.usuario.rol")).isEqualTo("PROPIETARIO");
        mvc.perform(get("/api/portal/negocios").header("Authorization", "Bearer " + JsonPath.read(sesion, "$.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inmueble").value(inmueble.getTitulo()));
        mvc.perform(get("/api/propietarios/" + jorge.getId() + "/acceso").header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.ultimoIngreso").isNotEmpty());

        // Entra cuando quiera con su correo y contraseña.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"ClaveDelPropietario1\"}".formatted(email)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuario.rol").value("PROPIETARIO"));
    }
}
