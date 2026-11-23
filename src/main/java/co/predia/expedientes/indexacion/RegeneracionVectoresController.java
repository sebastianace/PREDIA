package co.predia.expedientes.indexacion;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documentos")
@Tag(name = "Expedientes")
public class RegeneracionVectoresController {

    private final ServicioRegeneracionVectores servicio;
    private final SesionActual sesion;

    public RegeneracionVectoresController(ServicioRegeneracionVectores servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    /** Solo ADMIN (ver ConfiguracionSeguridad). Se llama de nuevo mientras {@code pendientes} sea mayor que cero. */
    @PostMapping("/regenerar-vectores")
    @Operation(summary = "Regenera, desde el texto guardado, los vectores indexados con otro modelo o sin vector")
    public ServicioRegeneracionVectores.Resultado regenerar() {
        return servicio.regenerar(sesion.inmobiliariaId());
    }
}
