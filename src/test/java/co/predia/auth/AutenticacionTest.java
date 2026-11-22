package co.predia.auth;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AutenticacionTest extends PruebaIntegracion {

    @Test
    void loginCorrectoDevuelveTokenConLaInmobiliariaDelUsuario() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Login");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}""".formatted(asesor.getEmail().toUpperCase(), PASSWORD_PRUEBA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.rol").value("ASESOR"))
                .andExpect(jsonPath("$.usuario.inmobiliariaId").value(inmobiliaria.getId()))
                .andExpect(jsonPath("$.usuario.inmobiliaria").value("Inmobiliaria Login"));
    }

    @Test
    void contrasenaIncorrectaResponde401SinRevelarDetalles() throws Exception {
        Usuario asesor = crearUsuario(crearInmobiliaria("Inmobiliaria Clave"), Rol.ASESOR);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "otra-clave"}""".formatted(asesor.getEmail())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos."));
    }

    @Test
    void sinTokenNoSePuedeConsultarLaApi() throws Exception {
        mvc.perform(get("/api/inmuebles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inmuebles").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void yoDevuelveElUsuarioDelToken() throws Exception {
        Usuario admin = crearUsuario(crearInmobiliaria("Inmobiliaria Yo"), Rol.ADMIN);

        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void sinApiKeyElSistemaInformaModoManual() throws Exception {
        Usuario asesor = crearUsuario(crearInmobiliaria("Inmobiliaria IA"), Rol.ASESOR);

        mvc.perform(get("/api/sistema/estado").header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iaDisponible").value(false))
                .andExpect(jsonPath("$.aviso").isNotEmpty());
    }
}
