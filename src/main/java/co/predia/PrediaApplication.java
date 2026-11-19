package co.predia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de Predia: asistente con IA para inmobiliarias.
 * Principio rector: la IA sugiere, las reglas deciden y la persona confirma.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PrediaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrediaApplication.class, args);
    }
}
