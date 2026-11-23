package co.predia.credito.reduccion;

import co.predia.comun.seguridad.SesionActual;
import co.predia.credito.reduccion.CalculadoraReduccion.Frecuencia;
import co.predia.credito.reduccion.CalculadoraReduccion.Modo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/reducciones")
@Tag(name = "Reducción de crédito")
public class ReduccionController {

    private final SimuladorReduccionService simulador;
    private final SesionActual sesion;

    public ReduccionController(SimuladorReduccionService simulador, SesionActual sesion) {
        this.simulador = simulador;
        this.sesion = sesion;
    }

    @PostMapping("/simular")
    @Operation(summary = "Simula abonos extraordinarios a un crédito hipotecario existente (Ley 546 de 1999)")
    public SimulacionReduccion simular(@Valid @RequestBody SolicitudReduccion s) {
        return simulador.simular(sesion.inmobiliariaId(),
                new SimuladorReduccionService.Solicitud(s.clienteId(), s.banco(), s.saldo(), s.tasaEa(),
                        s.cuotasPendientes(), s.seguroMensual(), s.modo(), s.frecuencia(), s.abonos(),
                        s.escenarioDeLaTabla()));
    }

    /**
     * {@code tasaEa} va como fracción (0,11 = 11 % E.A.). {@code abonos}: hasta 3 valores a comparar; vacío
     * devuelve solo el crédito actual. {@code escenarioDeLaTabla}: 0 = actual, 1..3 = el abono respectivo.
     */
    public record SolicitudReduccion(
            Long clienteId, @Size(max = 120) String banco,
            @NotNull @DecimalMin("1") BigDecimal saldo,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal tasaEa,
            @NotNull @Min(1) @Max(SimuladorReduccionService.MAX_CUOTAS) Integer cuotasPendientes,
            @DecimalMin("0") BigDecimal seguroMensual, Modo modo, Frecuencia frecuencia,
            @Size(max = SimuladorReduccionService.MAX_ESCENARIOS) List<@NotNull @DecimalMin("1") BigDecimal> abonos,
            @Min(0) @Max(SimuladorReduccionService.MAX_ESCENARIOS) Integer escenarioDeLaTabla) {
    }
}
