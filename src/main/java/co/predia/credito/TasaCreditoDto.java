package co.predia.credito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Una tasa con todo lo necesario para citarla: banco, tipo, valor, desde cuándo rige, fuente y fecha de carga. */
public record TasaCreditoDto(Long id, String banco, TipoTasa tipo, BigDecimal tasaEa, LocalDate vigenteDesde,
                             String fuente, Instant fechaCarga, boolean esEjemplo) {

    public static TasaCreditoDto desde(TasaCredito t) {
        return new TasaCreditoDto(t.getId(), t.getBanco(), t.getTipo(), t.getTasaEa(), t.getVigenteDesde(),
                t.getFuente(), t.getFechaCarga(), t.isEsEjemplo());
    }
}
