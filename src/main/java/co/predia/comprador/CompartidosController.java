package co.predia.comprador;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lo que el asesor comparte con un comprador y los mensajes que el comprador le deja. */
@RestController
@RequestMapping("/api")
@Tag(name = "Portal del comprador (personal)")
public class CompartidosController {

    private final ServicioCompartidos compartidos;
    private final ServicioSolicitudes solicitudes;
    private final SesionActual sesion;

    public CompartidosController(ServicioCompartidos compartidos, ServicioSolicitudes solicitudes, SesionActual sesion) {
        this.compartidos = compartidos;
        this.solicitudes = solicitudes;
        this.sesion = sesion;
    }

    @GetMapping("/clientes/{id}/inmuebles-compartidos")
    @Operation(summary = "Los inmuebles que se le compartieron al cliente y si marcó interés")
    public List<ServicioCompartidos.CompartidoDto> listar(@PathVariable Long id) {
        return compartidos.listar(sesion.usuario(), id);
    }

    @PostMapping("/clientes/{id}/inmuebles-compartidos")
    @Operation(summary = "Comparte un inmueble con el cliente (con una nota opcional); es una decisión del asesor")
    public ServicioCompartidos.CompartidoDto compartir(@PathVariable Long id, @Valid @RequestBody SolicitudCompartir s) {
        return compartidos.compartir(sesion.usuario(), id, s.inmuebleId(), s.nota());
    }

    @DeleteMapping("/clientes/{id}/inmuebles-compartidos/{inmuebleId}")
    @Operation(summary = "Deja de compartir el inmueble: el cliente ya no lo ve")
    public void dejarDeCompartir(@PathVariable Long id, @PathVariable Long inmuebleId) {
        compartidos.dejarDeCompartir(sesion.usuario(), id, inmuebleId);
    }

    @GetMapping("/solicitudes-comprador")
    @Operation(summary = "Los mensajes de compradores que aún no se atienden")
    public List<ServicioSolicitudes.SolicitudDto> pendientes() {
        return solicitudes.pendientes(sesion.usuario());
    }

    @PostMapping("/solicitudes-comprador/{id}/atender")
    @Operation(summary = "Marca el mensaje como atendido")
    public void atender(@PathVariable Long id) {
        solicitudes.atender(sesion.usuario(), id);
    }

    public record SolicitudCompartir(@NotNull Long inmuebleId, @Size(max = 500) String nota) {
    }
}
