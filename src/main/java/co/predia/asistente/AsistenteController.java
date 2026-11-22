package co.predia.asistente;

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

@RestController
@RequestMapping("/api/asistente")
@Tag(name = "Asistente")
public class AsistenteController {

    private final ServicioAsistente servicio;
    private final SesionActual sesion;

    public AsistenteController(ServicioAsistente servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @PostMapping("/mensajes")
    @Operation(summary = "Conversa con el asistente. El modelo elige las herramientas; las cifras salen de Java. "
            + "Sin IA responde 503; los recordatorios solo quedan como borrador")
    public RespuestaAsistente enviar(@Valid @RequestBody SolicitudMensaje solicitud) {
        return servicio.responder(sesion.usuario(), solicitud.mensaje().strip(), solicitud.conversacionId());
    }

    public record SolicitudMensaje(@NotBlank @Size(max = 2000) String mensaje, @Size(max = 64) String conversacionId) {
    }
}
