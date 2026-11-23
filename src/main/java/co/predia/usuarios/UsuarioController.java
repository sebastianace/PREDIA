package co.predia.usuarios;

import co.predia.comun.seguridad.SesionActual;
import co.predia.inmobiliaria.Rol;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Solo ADMIN (lo exige ConfiguracionSeguridad para toda la ruta). */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios")
public class UsuarioController {

    private final ServicioUsuarios servicio;
    private final SesionActual sesion;

    public UsuarioController(ServicioUsuarios servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Personal de la inmobiliaria (administradores y asesores)")
    public List<ServicioUsuarios.UsuarioDto> listar() {
        return servicio.listar(sesion.usuario());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un administrador o asesor y le envía la invitación para crear su contraseña")
    public ServicioUsuarios.Invitado crear(@Valid @RequestBody SolicitudUsuario s) {
        return servicio.crear(sesion.usuario(), s.nombre(), s.email(), s.rol());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cambia el rol o activa/desactiva a una persona (desactivarla cierra su sesión al instante)")
    public ServicioUsuarios.UsuarioDto actualizar(@PathVariable Long id, @RequestBody SolicitudCambio s) {
        return servicio.actualizar(sesion.usuario(), id, s.rol(), s.activo());
    }

    @PostMapping("/{id}/invitacion")
    @Operation(summary = "Reenvía la invitación; a quien ya tenía cuenta le sirve para elegir una contraseña nueva")
    public ServicioUsuarios.Invitado reenviar(@PathVariable Long id) {
        return servicio.reenviar(sesion.usuario(), id);
    }

    public record SolicitudUsuario(@NotBlank @Size(max = 150) String nombre,
                                   @NotBlank @Email @Size(max = 150) String email, @NotNull Rol rol) {
    }

    public record SolicitudCambio(Rol rol, Boolean activo) {
    }
}
