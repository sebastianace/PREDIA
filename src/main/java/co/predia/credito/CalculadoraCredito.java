package co.predia.credito;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Fórmulas del crédito hipotecario con cuota fija (sistema francés). Todo número que ve el usuario
 * sale de aquí, con fórmulas explícitas: nunca lo calcula ni lo redondea el modelo de lenguaje.
 *
 * <pre>
 *   i_mensual = (1 + EA)^(1/12) − 1
 *   cuota     = P · i / (1 − (1 + i)^−n)
 * </pre>
 */
public final class CalculadoraCredito {

    private static final MathContext PRECISION = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final int ESCALA_TASA_MENSUAL = 12;

    private CalculadoraCredito() {
    }

    /** Tasa mensual equivalente a una tasa efectiva anual (fracción: 0.12 = 12 %). */
    public static BigDecimal tasaMensual(BigDecimal tasaEfectivaAnual) {
        if (tasaEfectivaAnual.signum() < 0) {
            throw new IllegalArgumentException("La tasa efectiva anual no puede ser negativa.");
        }
        if (tasaEfectivaAnual.signum() == 0) {
            return BigDecimal.ZERO;
        }
        // La raíz 12.ª no existe en BigDecimal: se calcula en double y se redondea a 12 decimales,
        // que sobra para que la cuota quede exacta al peso.
        double mensual = Math.pow(1.0 + tasaEfectivaAnual.doubleValue(), 1.0 / 12.0) - 1.0;
        return new BigDecimal(mensual).setScale(ESCALA_TASA_MENSUAL, RoundingMode.HALF_UP);
    }

    /** Cuota mensual fija, redondeada al peso (hacia arriba en la mitad: HALF_UP). */
    public static BigDecimal cuota(BigDecimal monto, BigDecimal tasaMensual, int cuotas) {
        validar(monto, cuotas);
        if (tasaMensual.signum() == 0) {
            return monto.divide(BigDecimal.valueOf(cuotas), 0, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(tasaMensual).pow(cuotas, PRECISION);
        BigDecimal cuota = monto.multiply(tasaMensual, PRECISION).multiply(factor, PRECISION)
                .divide(factor.subtract(BigDecimal.ONE), PRECISION);
        return cuota.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Cuánto dinero se puede pedir prestado pagando una cuota dada: el valor presente de la cuota.
     * Se redondea hacia abajo, para no prometer más de lo que se puede pagar.
     */
    public static BigDecimal montoQueSoportaLaCuota(BigDecimal cuota, BigDecimal tasaMensual, int cuotas) {
        validar(cuota, cuotas);
        if (tasaMensual.signum() == 0) {
            return cuota.multiply(BigDecimal.valueOf(cuotas)).setScale(0, RoundingMode.DOWN);
        }
        BigDecimal factor = BigDecimal.ONE.add(tasaMensual).pow(cuotas, PRECISION);
        BigDecimal monto = cuota.multiply(factor.subtract(BigDecimal.ONE), PRECISION)
                .divide(tasaMensual.multiply(factor, PRECISION), PRECISION);
        return monto.setScale(0, RoundingMode.DOWN);
    }

    private static void validar(BigDecimal valor, int cuotas) {
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("El monto no puede ser negativo.");
        }
        if (cuotas <= 0) {
            throw new IllegalArgumentException("El plazo debe ser de al menos una cuota.");
        }
    }
}
