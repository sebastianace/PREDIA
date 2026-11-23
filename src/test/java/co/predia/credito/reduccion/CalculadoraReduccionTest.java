package co.predia.credito.reduccion;

import co.predia.credito.reduccion.CalculadoraReduccion.Frecuencia;
import co.predia.credito.reduccion.CalculadoraReduccion.Modo;
import co.predia.credito.reduccion.CalculadoraReduccion.Resultado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los valores de referencia salen del simulador original en JavaScript (sin redondeos), corrido con node:
 * esta versión redondea al peso cada mes, así que los totales difieren en unos pocos pesos.
 */
class CalculadoraReduccionTest {

    private static final BigDecimal SALDO = new BigDecimal("150000000");
    private static final BigDecimal ONCE_POR_CIENTO = new BigDecimal("0.11");
    private static final BigDecimal CERO = BigDecimal.ZERO;

    private static Resultado simular(String abono, Frecuencia f, Modo m) {
        return CalculadoraReduccion.simular(SALDO, ONCE_POR_CIENTO, 240, new BigDecimal(abono), f, m);
    }

    /** Invariantes que deben cumplirse siempre: la tabla cuadra con los totales y el saldo termina en cero. */
    private static void cuadra(Resultado r, BigDecimal saldoInicial) {
        BigDecimal capital = r.filas().stream().map(CalculadoraReduccion.Fila::capital).reduce(CERO, BigDecimal::add);
        BigDecimal abonos = r.filas().stream().map(CalculadoraReduccion.Fila::abono).reduce(CERO, BigDecimal::add);
        BigDecimal intereses = r.filas().stream().map(CalculadoraReduccion.Fila::intereses).reduce(CERO, BigDecimal::add);
        BigDecimal cuotas = r.filas().stream().map(CalculadoraReduccion.Fila::cuota).reduce(CERO, BigDecimal::add);

        assertThat(capital.add(abonos)).isEqualByComparingTo(saldoInicial);
        assertThat(intereses).isEqualByComparingTo(r.totalIntereses());
        assertThat(abonos).isEqualByComparingTo(r.totalAbonos());
        assertThat(cuotas.add(abonos)).isEqualByComparingTo(r.totalPagado());
        assertThat(r.totalPagado().subtract(r.totalIntereses())).isEqualByComparingTo(saldoInicial);
        assertThat(r.filas().getLast().saldo()).isEqualByComparingTo("0");
        assertThat(r.filas()).hasSize(r.cuotas());
    }

    @Test
    void sinAbonoLaCuotaYLosTotalesCoincidenConElSimuladorOriginal() {
        Resultado r = simular("0", Frecuencia.MENSUAL, Modo.REDUCIR_PLAZO);

        assertThat(r.cuotaInicial()).isEqualByComparingTo("1495708");   // 1.495.707,52
        assertThat(r.cuotas()).isEqualTo(240);
        assertThat(r.totalIntereses()).isBetween(new BigDecimal("208969000"), new BigDecimal("208971000")); // 208.969.805
        cuadra(r, SALDO);
    }

    @Test
    void unAbonoMensualDeMedioMillonReduceElPlazoA123Meses() {
        Resultado r = simular("500000", Frecuencia.MENSUAL, Modo.REDUCIR_PLAZO);

        assertThat(r.cuotas()).isEqualTo(123);
        assertThat(r.cuotaFinal()).isEqualByComparingTo(r.cuotaInicial());
        assertThat(r.totalIntereses()).isBetween(new BigDecimal("95216000"), new BigDecimal("95219000")); // 95.217.890
        cuadra(r, SALDO);
    }

    @Test
    void unAbonoAnualDeCincoMillonesReduceElPlazoA137Meses() {
        Resultado r = simular("5000000", Frecuencia.ANUAL, Modo.REDUCIR_PLAZO);

        assertThat(r.cuotas()).isEqualTo(137);
        assertThat(r.totalIntereses()).isBetween(new BigDecimal("109079000"), new BigDecimal("109083000")); // 109.080.652
        assertThat(r.filas().stream().filter(f -> f.abono().signum() > 0).map(CalculadoraReduccion.Fila::mes))
                .allMatch(mes -> mes % 12 == 0);
        cuadra(r, SALDO);
    }

    @Test
    void unAbonoUnicoDeVeinteMillonesSeAplicaEnLaPrimeraCuotaYReduceElPlazoA165Meses() {
        Resultado r = simular("20000000", Frecuencia.UNICO, Modo.REDUCIR_PLAZO);

        assertThat(r.cuotas()).isEqualTo(165);
        assertThat(r.filas().stream().filter(f -> f.abono().signum() > 0)).hasSize(1);
        assertThat(r.filas().getFirst().abono()).isEqualByComparingTo("20000000");
        assertThat(r.totalIntereses()).isBetween(new BigDecimal("115574000"), new BigDecimal("115578000")); // 115.575.843
        cuadra(r, SALDO);
    }

    @Test
    void otroCreditoDeReferencia() {
        Resultado r = CalculadoraReduccion.simular(new BigDecimal("80000000"), new BigDecimal("0.095"), 120,
                new BigDecimal("300000"), Frecuencia.MENSUAL, Modo.REDUCIR_PLAZO);

        assertThat(r.cuotaInicial()).isEqualByComparingTo("1018168");   // 1.018.167,96
        assertThat(r.cuotas()).isEqualTo(82);
        assertThat(r.totalIntereses()).isBetween(new BigDecimal("27634000"), new BigDecimal("27637000")); // 27.635.749
        cuadra(r, new BigDecimal("80000000"));
    }

    @Test
    void reducirLaCuotaMantieneElPlazoYBajaLaCuotaYLosIntereses() {
        Resultado base = simular("0", Frecuencia.MENSUAL, Modo.REDUCIR_CUOTA);
        Resultado r = simular("20000000", Frecuencia.UNICO, Modo.REDUCIR_CUOTA);

        assertThat(r.cuotas()).isEqualTo(240);
        assertThat(r.cuotaFinal()).isLessThan(r.cuotaInicial());
        // 130 millones a 239 cuotas: la cuota baja en proporción al saldo (cerca de 1.495.708 · 130/150).
        assertThat(r.cuotaFinal()).isBetween(new BigDecimal("1294000"), new BigDecimal("1298000"));
        assertThat(r.totalIntereses()).isLessThan(base.totalIntereses());
        cuadra(r, SALDO);
    }

    @Test
    void reducirElPlazoAhorraMasInteresesQueReducirLaCuotaConElMismoAbono() {
        Resultado plazo = simular("20000000", Frecuencia.UNICO, Modo.REDUCIR_PLAZO);
        Resultado cuota = simular("20000000", Frecuencia.UNICO, Modo.REDUCIR_CUOTA);

        assertThat(plazo.totalIntereses()).isLessThan(cuota.totalIntereses());
    }

    @Test
    void unAbonoMayorQueElSaldoLiquidaElCreditoEnLaPrimeraCuota() {
        Resultado r = CalculadoraReduccion.simular(new BigDecimal("5000000"), new BigDecimal("0.12"), 60,
                new BigDecimal("999999999"), Frecuencia.UNICO, Modo.REDUCIR_PLAZO);

        assertThat(r.cuotas()).isEqualTo(1);
        assertThat(r.totalAbonos()).isLessThan(new BigDecimal("5000000"));
        cuadra(r, new BigDecimal("5000000"));
    }

    @Test
    void conTasaCeroNoHayInteresesYElAbonoSoloAcortaElPlazo() {
        Resultado r = CalculadoraReduccion.simular(new BigDecimal("12000000"), CERO, 12, new BigDecimal("1000000"),
                Frecuencia.MENSUAL, Modo.REDUCIR_PLAZO);

        assertThat(r.totalIntereses()).isEqualByComparingTo("0");
        assertThat(r.cuotaInicial()).isEqualByComparingTo("1000000");
        assertThat(r.cuotas()).isEqualTo(6);
        cuadra(r, new BigDecimal("12000000"));
    }

    @Test
    void conAbonoCeroElModoNoCambiaNada() {
        Resultado plazo = simular("0", Frecuencia.MENSUAL, Modo.REDUCIR_PLAZO);
        Resultado cuota = simular("0", Frecuencia.MENSUAL, Modo.REDUCIR_CUOTA);

        assertThat(cuota.totalIntereses()).isEqualByComparingTo(plazo.totalIntereses());
        assertThat(cuota.cuotas()).isEqualTo(plazo.cuotas());
    }

    @Test
    void rechazaDatosInvalidos() {
        assertThatThrownBy(() -> CalculadoraReduccion.simular(CERO, ONCE_POR_CIENTO, 240, CERO, Frecuencia.UNICO,
                Modo.REDUCIR_PLAZO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraReduccion.simular(SALDO, ONCE_POR_CIENTO, 0, CERO, Frecuencia.UNICO,
                Modo.REDUCIR_PLAZO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraReduccion.simular(SALDO, new BigDecimal("-0.01"), 240, CERO,
                Frecuencia.UNICO, Modo.REDUCIR_PLAZO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraReduccion.simular(SALDO, ONCE_POR_CIENTO, 240, new BigDecimal("-1"),
                Frecuencia.UNICO, Modo.REDUCIR_PLAZO)).isInstanceOf(IllegalArgumentException.class);
    }
}
