package co.predia.expedientes.consulta;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preguntas sobre los expedientes, con citas a documento, página y sección. */
@RestController
@RequestMapping("/api/consultas")
@Tag(name = "Consultas")
public class ConsultaController {

    private final MotorRag motorRag;
    private final SesionActual sesion;

    public ConsultaController(MotorRag motorRag, SesionActual sesion) {
        this.motorRag = motorRag;
        this.sesion = sesion;
    }

    @PostMapping
    @Operation(summary = "Pregunta sobre los expedientes indexados; responde con citas o 'no lo sé'")
    public RespuestaConsulta preguntar(@Valid @RequestBody SolicitudConsulta solicitud) {
        return motorRag.responder(sesion.usuario(), solicitud.pregunta().strip(), solicitud.inmuebleId());
    }

    public record SolicitudConsulta(@NotBlank @Size(max = 1000) String pregunta, Long inmuebleId) {
    }
}
