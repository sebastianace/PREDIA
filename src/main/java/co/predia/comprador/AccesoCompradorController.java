package co.predia.comprador;

import co.predia.comun.seguridad.SesionActual;
import co.predia.usuarios.CorreoInvitaciones;
import co.predia.usuarios.ServicioAccesoPortal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El acceso del cliente comprador a su portal, administrado por el personal (ADMIN o ASESOR). */
@RestController
@RequestMapping("/api/clientes/{id}")
@Tag(name = "Portal del comprador (personal)")
public class AccesoCompradorController {

    private final ServicioAccesoComprador servicio;
    private final SesionActual sesion;

    public AccesoCompradorController(ServicioAccesoComprador servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping("/acceso")
    @Operation(summary = "Estado del acceso al portal: sin invitar, invitado, activo o desactivado")
    public ServicioAccesoPortal.AccesoPortal estado(@PathVariable Long id) {
        return servicio.estado(sesion.usuario(), id);
    }

    @GetMapping("/invitacion/vista-previa")
    @Operation(summary = "El correo de invitación tal como se enviará, para revisarlo antes")
    public CorreoInvitaciones.Correo vistaPrevia(@PathVariable Long id) {
        return servicio.vistaPrevia(sesion.usuario(), id);
    }

    @PostMapping("/invitacion")
    @Operation(summary = "Invita al cliente a su portal (o le reenvía la invitación para una contraseña nueva)")
    public ServicioAccesoPortal.Invitacion invitar(@PathVariable Long id) {
        return servicio.invitar(sesion.usuario(), id);
    }

    @PutMapping("/acceso")
    @Operation(summary = "Activa o desactiva el acceso del cliente (desactivarlo cierra su sesión al instante)")
    public ServicioAccesoPortal.AccesoPortal cambiar(@PathVariable Long id, @Valid @RequestBody SolicitudAcceso s) {
        return servicio.cambiarAcceso(sesion.usuario(), id, s.activo());
    }

    public record SolicitudAcceso(@NotNull Boolean activo) {
    }
}
