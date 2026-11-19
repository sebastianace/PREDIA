package co.predia.credito;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraCreditoTest {

    private static final BigDecimal DOCE_POR_CIENTO = new BigDecimal("0.12");

    @Test
    void creditoDe210MillonesAl12PorCientoA240MesesDaUnaCuotaDeCercaDe2223000() {
        BigDecimal i = CalculadoraCredito.tasaMensual(DOCE_POR_CIENTO);

        BigDecimal cuota = CalculadoraCredito.cuota(new BigDecimal("210000000"), i, 240);

        assertThat(cuota).isBetween(new BigDecimal("2222000"), new BigDecimal("2224000"));
    }

    @Test
    void laTasaMensualEquivalenteEsLaRaizDoceavaDeUnoMasLaEfectivaAnual() {
        BigDecimal i = CalculadoraCredito.tasaMensual(DOCE_POR_CIENTO);

        // (1,12)^(1/12) − 1 = 0,009488792934582… (verificado con 40 dígitos), a 12 decimales: …935
        assertThat(i).isEqualByComparingTo("0.009488792935");
        // Capitalizada 12 veces vuelve a la efectiva anual.
        assertThat(BigDecimal.ONE.add(i).pow(12).subtract(BigDecimal.ONE).setScale(6, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("0.120000");
    }

    @Test
    void sinInteresLaCuotaEsElMontoDividoEnLasCuotas() {
        BigDecimal cuota = CalculadoraCredito.cuota(new BigDecimal("120000000"),
                CalculadoraCredito.tasaMensual(BigDecimal.ZERO), 240);

        assertThat(cuota).isEqualByComparingTo("500000");
    }

    @Test
    void elMontoQueSoportaUnaCuotaEsElInversoDeLaCuota() {
        BigDecimal i = CalculadoraCredito.tasaMensual(DOCE_POR_CIENTO);
        BigDecimal cuota = CalculadoraCredito.cuota(new BigDecimal("150000000"), i, 180);

        BigDecimal monto = CalculadoraCredito.montoQueSoportaLaCuota(cuota, i, 180);

        // Redondeos al peso en ambos sentidos: la diferencia es de unos pocos pesos.
        assertThat(monto).isBetween(new BigDecimal("149990000"), new BigDecimal("150010000"));
    }

    @Test
    void rechazaValoresInvalidos() {
        assertThatThrownBy(() -> CalculadoraCredito.cuota(BigDecimal.TEN, new BigDecimal("0.01"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCredito.cuota(new BigDecimal("-1"), new BigDecimal("0.01"), 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCredito.tasaMensual(new BigDecimal("-0.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
