package co.predia.clientes;

import co.predia.credito.TasaCreditoDto;

import java.math.BigDecimal;
import java.util.List;

/** Presupuesto de un cliente con las reglas, la tasa y el plazo usados, para que el asesor pueda explicarlo. */
public record PresupuestoCliente(
        Long clienteId, String nombre, int plazoAnios, boolean vis, BigDecimal pctCuotaMaximaIngreso,
        BigDecimal pctCuotaInicial, TasaCreditoDto tasa, BigDecimal ingresoFamiliar, BigDecimal ahorro,
        BigDecimal deudasMensuales, CalculadoraPresupuesto.Presupuesto presupuesto, List<String> avisos) {
}
