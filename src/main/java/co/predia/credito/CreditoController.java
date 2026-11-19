package co.predia.credito;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/creditos")
@Tag(name = "Crédito")
public class CreditoController {

    private final SimuladorCreditoService simulador;
    private final SesionActual sesion;

    public CreditoController(SimuladorCreditoService simulador, SesionActual sesion) {
        this.simulador = simulador;
        this.sesion = sesion;
    }

    @PostMapping("/simular")
    @Operation(summary = "Simula un crédito hipotecario con cuota fija (sistema francés)")
    public SimulacionCredito simular(@Valid @RequestBody SolicitudSimulacion s) {
        return simulador.simular(sesion.inmobiliariaId(),
                new SimuladorCreditoService.Solicitud(s.monto(), s.inmuebleId(), s.clienteId(), s.plazoAnios(),
                        s.tasaId(), s.banco()));
    }

    /** Con {@code monto}, o con {@code inmuebleId} + {@code clienteId} (se financia precio − ahorro). */
    public record SolicitudSimulacion(@DecimalMin("1") BigDecimal monto, Long inmuebleId, Long clienteId,
                                      @Min(1) @Max(30) Integer plazoAnios, Long tasaId, String banco) {
    }
}
