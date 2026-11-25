package co.predia.credito.reduccion;

import co.predia.credito.reduccion.CalculadoraReduccion.Fila;
import co.predia.credito.reduccion.CalculadoraReduccion.Frecuencia;
import co.predia.credito.reduccion.CalculadoraReduccion.Modo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de simular abonos extraordinarios sobre un crédito que el cliente ya tiene. Todos los números
 * salen de {@link CalculadoraReduccion}; el cliente va en null si la simulación no se hizo para uno.
 * {@code primeraCuotaCapital} y {@code primeraCuotaIntereses} son cómo se reparte la primera cuota del crédito tal como está
 * hoy (la misma en todos los escenarios, porque el primer abono se aplica después de pagarla); la hoja impresa las muestra.
 */
public record SimulacionReduccion(
        Long clienteId, String clienteNombre, String banco, BigDecimal saldo, BigDecimal tasaEa, int cuotasPendientes,
        BigDecimal seguroMensual, Modo modo, Frecuencia frecuencia, BigDecimal pctCuotaMaxIngreso,
        BigDecimal primeraCuotaCapital, BigDecimal primeraCuotaIntereses, Escenario actual, List<Escenario> escenarios, int escenarioDeLaTabla, List<Fila> tabla, String aviso) {

    public static final String AVISO =
            "Es una simulación con fines informativos; no es una oferta ni una aprobación del banco. "
                    + "Confirma con tu banco cómo aplica los abonos extraordinarios.";

    /**
     * Un escenario: «actual» (sin abono) o con un abono. {@code ahorro*}, {@code cuotasReducidas} y
     * {@code ingresoRequerido} son null en el escenario actual. {@code desembolsoMensual} es lo que se paga
     * cada mes (cuota + seguro, y el abono si es mensual).
     */
    public record Escenario(
            BigDecimal abono, BigDecimal cuotaInicial, BigDecimal cuotaFinal, int cuotas, BigDecimal totalIntereses,
            BigDecimal totalAbonos, BigDecimal totalPagado, BigDecimal totalSeguro, BigDecimal desembolsoMensual,
            BigDecimal ahorroIntereses, BigDecimal ahorroSeguro, BigDecimal ahorroTotal, Integer cuotasReducidas,
            BigDecimal ingresoRequerido, Boolean cumpleIngreso) {
    }
}
