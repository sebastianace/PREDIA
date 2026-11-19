package co.predia.clientes;

import co.predia.clientes.CalculadoraPresupuesto.Entrada;
import co.predia.clientes.CalculadoraPresupuesto.Limitante;
import co.predia.clientes.CalculadoraPresupuesto.Presupuesto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraPresupuestoTest {

    private static Entrada paolaYJuan(String ahorro, String deudas) {
        return new Entrada(new BigDecimal("7500000"), new BigDecimal(ahorro), new BigDecimal(deudas),
                new BigDecimal("0.30"), new BigDecimal("0.30"), new BigDecimal("0.12"), 240);
    }

    @Test
    void ingreso7500000YAhorro90MillonesDanCuotaMaxima2250000YTopeDe300Millones() {
        Presupuesto p = CalculadoraPresupuesto.calcular(paolaYJuan("90000000", "0"));

        assertThat(p.cuotaMaxima()).isEqualByComparingTo("2250000");
        assertThat(p.topeDeCompra()).isBetween(new BigDecimal("299000000"), new BigDecimal("301000000"));
        // Con estos datos manda el ahorro: 90 M ÷ 30 % = 300 M, y la cuota soportaría 302,5 M.
        assertThat(p.limitante()).isEqualTo(Limitante.AHORRO);
        assertThat(p.topePorAhorro()).isEqualByComparingTo("300000000");
        assertThat(p.topePorCapacidadDePago()).isGreaterThan(new BigDecimal("300000000"));
    }

    @Test
    void conMuchoAhorroManda_laCapacidadDePago() {
        Presupuesto p = CalculadoraPresupuesto.calcular(paolaYJuan("300000000", "0"));

        assertThat(p.limitante()).isEqualTo(Limitante.CAPACIDAD_DE_PAGO);
        assertThat(p.topeDeCompra()).isEqualTo(p.topePorCapacidadDePago());
        assertThat(p.topeDeCompra()).isLessThan(p.topePorAhorro());
    }

    @Test
    void lasDeudasNoSeRestanDeLaCuotaMaximaPeroGeneranAlerta() {
        Presupuesto sinDeudas = CalculadoraPresupuesto.calcular(paolaYJuan("90000000", "0"));
        Presupuesto conDeudas = CalculadoraPresupuesto.calcular(paolaYJuan("90000000", "1500000"));

        assertThat(conDeudas.cuotaMaxima()).isEqualByComparingTo(sinDeudas.cuotaMaxima());
        assertThat(conDeudas.topeDeCompra()).isEqualByComparingTo(sinDeudas.topeDeCompra());
        assertThat(sinDeudas.alertaEndeudamiento()).isFalse();
        // (2.250.000 + 1.500.000) ÷ 7.500.000 = 50 % > 40 %
        assertThat(conDeudas.endeudamientoConCuotaMaxima()).isEqualByComparingTo("0.5000");
        assertThat(conDeudas.alertaEndeudamiento()).isTrue();
    }

    @Test
    void conUnaCuotaInicialMenorElTopePorAhorroSube() {
        Entrada vis = new Entrada(new BigDecimal("7500000"), new BigDecimal("90000000"), BigDecimal.ZERO,
                new BigDecimal("0.30"), new BigDecimal("0.20"), new BigDecimal("0.12"), 240);

        assertThat(CalculadoraPresupuesto.calcular(vis).topePorAhorro()).isEqualByComparingTo("450000000");
    }

    @Test
    void sinIngresoElTopeEsSoloLoQueAlcanzaElAhorro() {
        Entrada sinIngreso = new Entrada(BigDecimal.ZERO, new BigDecimal("30000000"), BigDecimal.ZERO,
                new BigDecimal("0.30"), new BigDecimal("0.30"), new BigDecimal("0.12"), 240);

        Presupuesto p = CalculadoraPresupuesto.calcular(sinIngreso);

        assertThat(p.cuotaMaxima()).isEqualByComparingTo("0");
        assertThat(p.topePorCapacidadDePago()).isEqualByComparingTo("30000000");
        assertThat(p.endeudamientoConCuotaMaxima()).isNull();
    }

    @Test
    void rechazaEntradasInvalidas() {
        assertThatThrownBy(() -> CalculadoraPresupuesto.calcular(new Entrada(new BigDecimal("-1"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.30"), new BigDecimal("0.30"),
                new BigDecimal("0.12"), 240))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculadoraPresupuesto.calcular(new Entrada(BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.30"),
                new BigDecimal("0.12"), 240))).isInstanceOf(IllegalArgumentException.class);
    }
}
