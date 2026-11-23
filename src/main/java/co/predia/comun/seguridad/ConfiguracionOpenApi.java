package co.predia.comun.seguridad;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API (Swagger). Declara el esquema de seguridad con el JWT de Predia: en
 * /swagger-ui.html aparece el botón «Authorize», se pega el token que devuelve POST /api/auth/login y todas las
 * pruebas desde Swagger salen con la sesión de ese usuario (con sus mismos permisos).
 */
@Configuration
public class ConfiguracionOpenApi {

    static final String ESQUEMA_JWT = "jwt";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Predia API")
                        .description("""
                                Asistente con IA para inmobiliarias. Para probar los endpoints protegidos: \
                                1) llama a POST /api/auth/login con un correo y una contraseña; \
                                2) copia el valor de «token» de la respuesta; \
                                3) pulsa «Authorize» (arriba a la derecha) y pégalo, sin la palabra «Bearer». \
                                Cada usuario tiene los permisos de su rol: ADMIN, ASESOR o PROPIETARIO."""))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("El token JWT que devuelve POST /api/auth/login.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
