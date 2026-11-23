package co.predia.comun.seguridad;

import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Swagger debe poder autorizarse con el JWT (botón «Authorize») y dejar sin candado lo que es público. */
class OpenApiTest extends PruebaIntegracion {

    @Test
    void laDocumentacionDeclaraElEsquemaBearerJwtYLoAplicaATodo() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.jwt.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.jwt.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.jwt.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].jwt", hasSize(0)));
    }

    @Test
    void elLoginYLaActivacionNoLlevanCandado() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security", hasSize(0)))
                .andExpect(jsonPath("$.paths['/api/auth/activar'].post.security", hasSize(0)))
                // Un endpoint protegido hereda la seguridad global (no declara una propia).
                .andExpect(jsonPath("$.paths['/api/negocios'].get.security").doesNotExist());
    }
}
