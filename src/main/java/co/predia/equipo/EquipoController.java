package co.predia.equipo;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** El comparativo del equipo: solo el administrador (ConfiguracionSeguridad). El tenant sale siempre de la sesión. */
@RestController
@RequestMapping("/api/equipo")
@Tag(name = "Equipo")
public class EquipoController {

    public record Totales(int vendidos, BigDecimal valorVendido, int asesores) {
    }

    public record RendimientoEquipo(Periodo periodo, List<ServicioRendimiento.Rendimiento> asesores, Totales totales) {
    }

    private final ServicioRendimiento rendimiento;
    private final SesionActual sesion;

    public EquipoController(ServicioRendimiento rendimiento, SesionActual sesion) {
        this.rendimiento = rendimiento;
        this.sesion = sesion;
    }

    @GetMapping("/rendimiento")
    @Operation(summary = "Cuánto vendió cada asesor en el periodo (solo administrador), con los totales del equipo")
    public RendimientoEquipo equipo(@RequestParam(defaultValue = "ANIO") Periodo periodo) {
        List<ServicioRendimiento.Rendimiento> filas = rendimiento.equipo(sesion.inmobiliariaId(), periodo);
        int vendidos = filas.stream().mapToInt(ServicioRendimiento.Rendimiento::vendidos).sum();
        BigDecimal valor = filas.stream().map(ServicioRendimiento.Rendimiento::valorVendido).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RendimientoEquipo(periodo, filas, new Totales(vendidos, valor, filas.size()));
    }
}
