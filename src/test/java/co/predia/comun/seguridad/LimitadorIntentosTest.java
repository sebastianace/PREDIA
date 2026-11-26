package co.predia.comun.seguridad;

import co.predia.comun.web.DemasiadosIntentosException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorIntentosTest {

    /** Reloj que se puede adelantar a mano. */
    private static class RelojManual extends Clock {
        Instant ahora = Instant.parse("2026-12-01T15:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final RelojManual reloj = new RelojManual();
    private final LimitadorIntentos limitador =
            new LimitadorIntentos(new PropiedadesSeguridad(3, 5, 2, 15), reloj);

    @Test
    void toleraLosFallosHastaElMaximoYLuegoBloquea() {
        for (int i = 0; i < 3; i++) {
            limitador.verificarLogin("ana@ejemplo.test", "1.1.1.1");
            limitador.fallaLogin("ana@ejemplo.test", "1.1.1.1");
        }

        assertThatThrownBy(() -> limitador.verificarLogin("ana@ejemplo.test", "1.1.1.1"))
                .isInstanceOf(DemasiadosIntentosException.class)
                .satisfies(e -> assertThat(((DemasiadosIntentosException) e).getSegundosEspera()).isBetween(1L, 15 * 60L + 1));
    }

    @Test
    void elBloqueoDeUnCorreoNoAfectaAOtro() {
        for (int i = 0; i < 3; i++) {
            limitador.fallaLogin("ana@ejemplo.test", "1.1.1.1");
        }

        assertThatCode(() -> limitador.verificarLogin("luis@ejemplo.test", "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void elCorreoSeComparaSinImportarMayusculasNiEspacios() {
        for (int i = 0; i < 3; i++) {
            limitador.fallaLogin(" ANA@Ejemplo.test ", "1.1.1.1");
        }

        assertThatThrownBy(() -> limitador.verificarLogin("ana@ejemplo.test", "9.9.9.9"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void unAciertoBorraLosFallosDelCorreo() {
        limitador.fallaLogin("ana@ejemplo.test", "1.1.1.1");
        limitador.fallaLogin("ana@ejemplo.test", "1.1.1.1");
        limitador.exitoLogin("ana@ejemplo.test");
        limitador.fallaLogin("ana@ejemplo.test", "1.1.1.1");
        limitador.fallaLogin("ana@ejemplo.test", "1.1.1.1");

        assertThatCode(() -> limitador.verificarLogin("ana@ejemplo.test", "3.3.3.3")).doesNotThrowAnyException();
    }

    @Test
    void despuesDeLaVentanaVuelveAEntrar() {
        for (int i = 0; i < 3; i++) {
            limitador.fallaLogin("ana@ejemplo.test", "1.1.1.1");
        }
        reloj.avanzar(Duration.ofMinutes(16));

        assertThatCode(() -> limitador.verificarLogin("ana@ejemplo.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void unaMismaIpQueProbaMuchosCorreosTambienSeBloquea() {
        for (int i = 0; i < 5; i++) {
            limitador.fallaLogin("usuario" + i + "@ejemplo.test", "7.7.7.7");
        }

        assertThatThrownBy(() -> limitador.verificarLogin("otro@ejemplo.test", "7.7.7.7"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void losEnlacesAdivinadosSeBloqueanPorIp() {
        limitador.fallaEnlace("5.5.5.5");
        limitador.fallaEnlace("5.5.5.5");

        assertThatThrownBy(() -> limitador.verificarEnlace("5.5.5.5")).isInstanceOf(DemasiadosIntentosException.class);
        assertThatCode(() -> limitador.verificarEnlace("6.6.6.6")).doesNotThrowAnyException();
    }
}
