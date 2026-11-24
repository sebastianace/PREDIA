package co.predia.comprador;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El rango de la cuota inicial del simulador de compra: reglas explícitas, sin IA ni navegador. */
class CalculadoraCompraTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void laMinimaEsElPorcentajeDelPrecioYElMaximoEsElAhorro() {
        CalculadoraCompra.Rango r = CalculadoraCompra.rango(n("300000000"), n("0.30"), n("120000000"));

        assertThat(r.minima()).isEqualByComparingTo("90000000");
        assertThat(r.maxima()).isEqualByComparingTo("120000000");
        assertThat(r.ahorroAlcanza()).isTrue();
        assertThat(r.faltante()).isEqualByComparingTo("0");
    }

    @Test
    void siElAhorroNoAlcanzaSeUsaLaMinimaYSeDiceCuantoFalta() {
        CalculadoraCompra.Rango r = CalculadoraCompra.rango(n("300000000"), n("0.30"), n("50000000"));

        assertThat(r.minima()).isEqualByComparingTo("90000000");
        assertThat(r.maxima()).isEqualByComparingTo("90000000");
        assertThat(r.ahorroAlcanza()).isFalse();
        assertThat(r.faltante()).isEqualByComparingTo("40000000");
    }

    @Test
    void laMinimaSeRedondeaHaciaArribaAlPeso() {
        // 10 % de 1.000.005 = 100.000,5: nunca se exige menos de lo que corresponde.
        CalculadoraCompra.Rango r = CalculadoraCompra.rango(n("1000005"), n("0.10"), n("2000000"));

        assertThat(r.minima()).isEqualByComparingTo("100001");
    }

    @Test
    void conMuchoAhorroNuncaSeFinanciaMenosDeUnPeso() {
        CalculadoraCompra.Rango r = CalculadoraCompra.rango(n("200000000"), n("0.30"), n("900000000"));

        assertThat(r.maxima()).isEqualByComparingTo("199999999");
    }

    @Test
    void sinAhorroRegistradoSeTrataComoCero() {
        CalculadoraCompra.Rango r = CalculadoraCompra.rango(n("200000000"), n("0.30"), null);

        assertThat(r.ahorroAlcanza()).isFalse();
        assertThat(r.faltante()).isEqualByComparingTo("60000000");
    }

    @Test
    void sinPrecioNoHayRango() {
        assertThatThrownBy(() -> CalculadoraCompra.rango(null, n("0.30"), n("1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCompra.rango(BigDecimal.ZERO, n("0.30"), n("1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elExcedenteSobreElTopeSoloExisteSiElPrecioLoPasa() {
        assertThat(CalculadoraCompra.excedenteSobreTope(n("330000000"), n("300000000"))).isEqualByComparingTo("30000000");
        assertThat(CalculadoraCompra.excedenteSobreTope(n("300000001"), n("300000000"))).isEqualByComparingTo("1");
        assertThat(CalculadoraCompra.excedenteSobreTope(n("300000000"), n("300000000"))).isNull();
        assertThat(CalculadoraCompra.excedenteSobreTope(n("268000000"), n("300000000"))).isNull();
        // Sin tope (no hay tasa cargada) o sin precio no hay nada que avisar.
        assertThat(CalculadoraCompra.excedenteSobreTope(n("330000000"), null)).isNull();
        assertThat(CalculadoraCompra.excedenteSobreTope(null, n("300000000"))).isNull();
    }

    @Test
    void validarRechazaLoQueQuedaFueraDelRango() {
        CalculadoraCompra.Rango r = CalculadoraCompra.rango(n("300000000"), n("0.30"), n("120000000"));

        CalculadoraCompra.validar(r, n("100000000"));
        assertThatThrownBy(() -> CalculadoraCompra.validar(r, n("89999999")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCompra.validar(r, n("120000001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraCompra.validar(r, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
