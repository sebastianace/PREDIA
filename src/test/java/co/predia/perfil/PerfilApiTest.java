package co.predia.perfil;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Mi perfil»: cambiar el propio nombre. */
class PerfilApiTest extends PruebaIntegracion {

    @Test
    void elAsesorCambiaSuNombreYRecibeUnaSesionNuevaConElNombreActualizado() throws Exception {
        Inmobiliaria inm = crearInmobiliaria("Perfil Nombre");
        Usuario laura = crearUsuario(inm, Rol.ASESOR);

        String respuesta = mvc.perform(put("/api/perfil").header("Authorization", bearer(laura))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"  Laura   Gómez   Pardo \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nombre").value("Laura Gómez Pardo"))      // sin espacios de más
                .andExpect(jsonPath("$.usuario.email").value(laura.getEmail()))           // el correo no cambia
                .andReturn().getResponse().getContentAsString();

        // El token nuevo ya lleva el nombre nuevo; el viejo también ve el nombre actual porque /yo lo lee de la base.
        String tokenNuevo = JsonPath.read(respuesta, "$.token");
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + tokenNuevo))
                .andExpect(jsonPath("$.nombre").value("Laura Gómez Pardo"));
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(laura)))
                .andExpect(jsonPath("$.nombre").value("Laura Gómez Pardo"));
    }

    @Test
    void elNombreNoPuedeEstarVacioNiSerDemasiadoLargo() throws Exception {
        Inmobiliaria inm = crearInmobiliaria("Perfil Validacion");
        Usuario laura = crearUsuario(inm, Rol.ASESOR);

        mvc.perform(put("/api/perfil").header("Authorization", bearer(laura)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\": \"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/perfil").header("Authorization", bearer(laura)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\": \"" + "x".repeat(151) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/perfil").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Sin sesión\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(laura.getNombre()).isNotBlank();
    }
}
