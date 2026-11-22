package co.predia.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** "Hoy" para las reglas de vigencia: siempre en la hora de Colombia, y inyectable para poder fijarlo. */
@Configuration
public class ConfiguracionReloj {

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    @Bean
    Clock reloj() {
        return Clock.system(ZONA_COLOMBIA);
    }
}
