package co.predia.clientes;

import co.predia.credito.CalculadoraCredito;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Presupuesto de compra de un cliente, con reglas explícitas en Java (parámetros por inmobiliaria):
 * <ul>
 *   <li>cuota máxima = % del ingreso familiar (30 % por defecto);</li>
 *   <li>tope de compra = el menor entre (ahorro ÷ % de cuota inicial) y (ahorro + lo que soporta la cuota máxima
 *       con la tasa y el plazo elegidos).</li>
 * </ul>
 * Las deudas mensuales NO se restan de la cuota máxima (así lo define la regla del negocio): solo generan una
 * alerta de endeudamiento para que el asesor la tenga presente.
 */
public final class CalculadoraPresupuesto {

    /** Referencia para alertar: cuota máxima + deudas por encima de este % del ingreso. No es una regla legal. */
    public static final BigDecimal ENDEUDAMIENTO_DE_ALERTA = new BigDecimal("0.40");

    private CalculadoraPresupuesto() {
    }

    public enum Limitante { AHORRO, CAPACIDAD_DE_PAGO }

    public record Entrada(BigDecimal ingresoFamiliar, BigDecimal ahorro, BigDecimal deudasMensuales,
                          BigDecimal pctCuotaMaxima, BigDecimal pctCuotaInicial, BigDecimal tasaEfectivaAnual,
                          int plazoMeses) {
    }

    public record Presupuesto(BigDecimal cuotaMaxima, BigDecimal montoQueSoportaLaCuota, BigDecimal topePorAhorro,
                              BigDecimal topePorCapacidadDePago, BigDecimal topeDeCompra, Limitante limitante,
                              BigDecimal cuotaInicialMinimaDelTope, BigDecimal endeudamientoConCuotaMaxima,
                              boolean alertaEndeudamiento) {
    }

    public static Presupuesto calcular(Entrada e) {
        validar(e);
        BigDecimal cuotaMaxima = e.ingresoFamiliar().multiply(e.pctCuotaMaxima()).setScale(0, RoundingMode.DOWN);
        BigDecimal tasaMensual = CalculadoraCredito.tasaMensual(e.tasaEfectivaAnual());
        BigDecimal montoSoportado = CalculadoraCredito.montoQueSoportaLaCuota(cuotaMaxima, tasaMensual, e.plazoMeses());

        BigDecimal topePorAhorro = e.ahorro().divide(e.pctCuotaInicial(), 0, RoundingMode.DOWN);
        BigDecimal topePorCapacidad = e.ahorro().add(montoSoportado);
        boolean limitaElAhorro = topePorAhorro.compareTo(topePorCapacidad) <= 0;
        BigDecimal tope = limitaElAhorro ? topePorAhorro : topePorCapacidad;

        BigDecimal cuotaInicialMinima = tope.multiply(e.pctCuotaInicial()).setScale(0, RoundingMode.UP);

        BigDecimal endeudamiento = null;
        boolean alerta = false;
        if (e.ingresoFamiliar().signum() > 0) {
            endeudamiento = cuotaMaxima.add(e.deudasMensuales())
                    .divide(e.ingresoFamiliar(), 4, RoundingMode.HALF_UP);
            alerta = endeudamiento.compareTo(ENDEUDAMIENTO_DE_ALERTA) > 0;
        }
        return new Presupuesto(cuotaMaxima, montoSoportado, topePorAhorro, topePorCapacidad, tope,
                limitaElAhorro ? Limitante.AHORRO : Limitante.CAPACIDAD_DE_PAGO, cuotaInicialMinima, endeudamiento,
                alerta);
    }

    private static void validar(Entrada e) {
        if (e.ingresoFamiliar().signum() < 0 || e.ahorro().signum() < 0 || e.deudasMensuales().signum() < 0) {
            throw new IllegalArgumentException("Los valores del cliente no pueden ser negativos.");
        }
        if (e.pctCuotaMaxima().signum() <= 0 || e.pctCuotaMaxima().compareTo(BigDecimal.ONE) > 0
                || e.pctCuotaInicial().signum() <= 0 || e.pctCuotaInicial().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Los porcentajes deben estar entre 0 y 100 %.");
        }
        if (e.plazoMeses() <= 0) {
            throw new IllegalArgumentException("El plazo debe ser mayor a cero.");
        }
    }
}
