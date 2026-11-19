package co.predia.credito;

import java.math.BigDecimal;

/**
 * Resultado de simular un crédito hipotecario. Todos los números salen de CalculadoraCredito.
 * Los campos del cliente y del inmueble van en null cuando la simulación se hizo solo con un monto.
 */
public record SimulacionCredito(
        BigDecimal montoFinanciado, int plazoAnios, int numeroCuotas, BigDecimal cuotaMensual,
        BigDecimal totalAPagar, BigDecimal totalIntereses, BigDecimal tasaMensual, TasaCreditoDto tasa,
        BigDecimal cuotaMaxima, BigDecimal porcentajeSobreIngreso, Boolean cumpleCuotaMaxima,
        BigDecimal precioInmueble, BigDecimal cuotaInicial, BigDecimal cuotaInicialMinima,
        Boolean cumpleCuotaInicial, String aviso) {

    public static final String AVISO = "Es una simulación; la aprobación es del banco.";
}
