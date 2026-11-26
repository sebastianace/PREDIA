package co.predia.comun.seguridad;

import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con Swagger apagado (el valor por defecto) la documentación no existe y la API lleva una CSP cerrada. */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class SwaggerApagadoTest extends PruebaIntegracion {

    @Test
    void ladocumentacionNoSePublica() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void laApiLlevaUnaPoliticaDeContenidoCerrada() throws Exception {
        mvc.perform(get("/api/inmuebles"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }
}
