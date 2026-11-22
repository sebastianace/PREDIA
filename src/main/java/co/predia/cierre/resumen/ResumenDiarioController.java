package co.predia.cierre.resumen;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumen-diario")
@Tag(name = "Cierre del negocio")
public class ResumenDiarioController {

    private final ResumenDiarioService servicio;
    private final SesionActual sesion;

    public ResumenDiarioController(ResumenDiarioService servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Resumen del día: negocios bloqueados y documentos que vencen esta semana")
    public ResumenDiarioService.Resumen resumen() {
        return servicio.resumen(sesion.usuario());
    }
}
