package co.predia.credito.reduccion;

import co.predia.credito.CalculadoraCredito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Simula abonos extraordinarios a capital sobre un crédito hipotecario con cuota fija (sistema francés).
 * Todo número que ve el usuario sale de aquí, con fórmulas explícitas: nunca lo calcula el modelo.
 *
 * <p>El prepago sin penalidad lo permite la Ley 546 de 1999, y el deudor decide si el abono reduce el plazo
 * (la cuota se mantiene) o la cuota (el plazo se mantiene). Cada mes:
 *
 * <pre>
 *   intereses = saldo · i                  (i = (1 + EA)^(1/12) − 1, redondeado al peso)
 *   capital   = cuota − intereses
 *   saldo'    = saldo − capital − abono
 * </pre>
 *
 * Con {@link Modo#REDUCIR_CUOTA}, después de cada abono la cuota se recalcula para pagar el saldo nuevo en
 * las cuotas que faltan. La última cuota se ajusta para que el saldo quede exactamente en cero, de modo que
 * los totales siempre sean la suma de las filas de la tabla.
 */
public final class CalculadoraReduccion {

    public enum Frecuencia { MENSUAL, ANUAL, UNICO }

    public enum Modo { REDUCIR_PLAZO, REDUCIR_CUOTA }

    /** Una cuota de la tabla de amortización. {@code cuota} es lo que se paga por obligación (sin el abono). */
    public record Fila(int mes, BigDecimal cuota, BigDecimal intereses, BigDecimal capital, BigDecimal abono,
                       BigDecimal saldo, BigDecimal interesesAcumulados) {
    }

    public record Resultado(BigDecimal cuotaInicial, BigDecimal cuotaFinal, int cuotas, BigDecimal totalIntereses,
                            BigDecimal totalAbonos, BigDecimal totalPagado, List<Fila> filas) {
    }

    private CalculadoraReduccion() {
    }

    public static Resultado simular(BigDecimal saldo, BigDecimal tasaEfectivaAnual, int plazoMeses,
                                    BigDecimal abono, Frecuencia frecuencia, Modo modo) {
        validar(saldo, tasaEfectivaAnual, plazoMeses, abono);

        BigDecimal tasaMensual = CalculadoraCredito.tasaMensual(tasaEfectivaAnual);
        BigDecimal cuota = CalculadoraCredito.cuota(saldo, tasaMensual, plazoMeses);
        BigDecimal cuotaInicial = cuota;

        List<Fila> filas = new ArrayList<>();
        BigDecimal saldoActual = saldo;
        BigDecimal interesesAcumulados = BigDecimal.ZERO;
        BigDecimal totalAbonos = BigDecimal.ZERO;
        BigDecimal totalPagado = BigDecimal.ZERO;

        for (int mes = 1; mes <= plazoMeses && saldoActual.signum() > 0; mes++) {
            BigDecimal intereses = saldoActual.multiply(tasaMensual).setScale(0, RoundingMode.HALF_UP);
            boolean ultima = mes == plazoMeses || saldoActual.add(intereses).compareTo(cuota) <= 0;
            BigDecimal pagoCuota = ultima ? saldoActual.add(intereses) : cuota;
            BigDecimal capital = pagoCuota.subtract(intereses);

            BigDecimal saldoTrasCuota = saldoActual.subtract(capital);
            BigDecimal abonoDelMes = aplicaAbono(frecuencia, mes) ? abono.min(saldoTrasCuota) : BigDecimal.ZERO;
            BigDecimal saldoFinal = saldoTrasCuota.subtract(abonoDelMes);

            interesesAcumulados = interesesAcumulados.add(intereses);
            totalAbonos = totalAbonos.add(abonoDelMes);
            totalPagado = totalPagado.add(pagoCuota).add(abonoDelMes);
            filas.add(new Fila(mes, pagoCuota, intereses, capital, abonoDelMes, saldoFinal, interesesAcumulados));

            if (modo == Modo.REDUCIR_CUOTA && abonoDelMes.signum() > 0 && saldoFinal.signum() > 0 && mes < plazoMeses) {
                cuota = CalculadoraCredito.cuota(saldoFinal, tasaMensual, plazoMeses - mes);
            }
            saldoActual = saldoFinal;
        }
        return new Resultado(cuotaInicial, cuota, filas.size(), interesesAcumulados, totalAbonos, totalPagado, filas);
    }

    private static boolean aplicaAbono(Frecuencia frecuencia, int mes) {
        return switch (frecuencia) {
            case MENSUAL -> true;
            case ANUAL -> mes % 12 == 0;
            case UNICO -> mes == 1;
        };
    }

    private static void validar(BigDecimal saldo, BigDecimal tasa, int plazoMeses, BigDecimal abono) {
        if (saldo == null || saldo.signum() <= 0) {
            throw new IllegalArgumentException("El saldo del crédito debe ser mayor que cero.");
        }
        if (tasa == null || tasa.signum() < 0) {
            throw new IllegalArgumentException("La tasa efectiva anual no puede ser negativa.");
        }
        if (plazoMeses < 1) {
            throw new IllegalArgumentException("Debe faltar al menos una cuota.");
        }
        if (abono == null || abono.signum() < 0) {
            throw new IllegalArgumentException("El abono extraordinario no puede ser negativo.");
        }
    }
}
