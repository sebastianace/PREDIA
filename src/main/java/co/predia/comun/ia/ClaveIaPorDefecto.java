package co.predia.comun.ia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Si OPENAI_API_KEY está vacía (caso típico de un .env recién copiado), la reemplaza por un marcador
 * para que Spring AI arranque. EstadoIa detecta el marcador y Predia trabaja en modo manual.
 */
public class ClaveIaPorDefecto implements EnvironmentPostProcessor {

    private static final String PROPIEDAD = "spring.ai.openai.api-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
        String clave = entorno.getProperty(PROPIEDAD);
        if (clave == null || clave.isBlank()) {
            entorno.getPropertySources().addFirst(
                    new MapPropertySource("predia-clave-ia", Map.of(PROPIEDAD, EstadoIa.SIN_CLAVE)));
        }
    }
}
