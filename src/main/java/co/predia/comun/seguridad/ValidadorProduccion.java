package co.predia.comun.seguridad;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Con el perfil «produccion» (SPRING_PROFILES_ACTIVE=produccion) Predia se niega a arrancar si quedó alguna
 * configuración de desarrollo: datos de demo, contraseña de base de datos de ejemplo, secreto del JWT de ejemplo,
 * Swagger encendido o enlaces sin HTTPS. Es mejor un arranque que falla con el motivo claro que un servidor expuesto
 * con la clave de la guía.
 */
@Component
@Profile("produccion")
public class ValidadorProduccion implements InitializingBean {

    /** Valores que aparecen en .env.example o en la documentación: nunca valen en producción. */
    private static final Set<String> CLAVES_DE_EJEMPLO = Set.of("predia", "postgres", "password", "123456", "admin");

    private final Environment entorno;

    public ValidadorProduccion(Environment entorno) {
        this.entorno = entorno;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problemas = problemas(entorno);
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Predia no arranca en producción con esta configuración:\n"
                    + problemas.stream().map(p -> " - " + p).collect(Collectors.joining("\n")));
        }
    }

    static List<String> problemas(Environment e) {
        List<String> problemas = new ArrayList<>();

        if (e.getProperty("predia.demo.habilitada", Boolean.class, true)) {
            problemas.add("DEMO_HABILITADA debe ser false: la demo siembra usuarios ficticios con una contraseña conocida.");
        }
        if (e.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)
                || e.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
            problemas.add("PREDIA_SWAGGER debe estar apagado: publica el mapa completo de la API.");
        }

        String clave = e.getProperty("spring.datasource.password", "");
        if (clave.length() < 12 || CLAVES_DE_EJEMPLO.contains(clave.toLowerCase())) {
            problemas.add("DB_PASSWORD es la de ejemplo o tiene menos de 12 caracteres. Usa una aleatoria "
                    + "(openssl rand -base64 24).");
        }

        String secreto = e.getProperty("predia.jwt.secreto", "");
        if (secreto.toLowerCase().contains("cambia-este-valor") || secreto.toLowerCase().contains("solo-para-pruebas")
                || secreto.chars().distinct().count() < 12) {
            problemas.add("JWT_SECRET parece de ejemplo o poco aleatorio. Genera uno con: openssl rand -base64 48");
        }

        String url = e.getProperty("predia.usuarios.url-publica", "");
        if (!url.startsWith("https://")) {
            problemas.add("PREDIA_URL_PUBLICA debe empezar por https:// (los enlaces de invitación llevan un token).");
        }
        return problemas;
    }
}
