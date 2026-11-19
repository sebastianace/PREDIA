package co.predia.cierre.negocio;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
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

import java.time.Instant;
import java.util.List;

/** Propietarios-vendedores. No se guardan sin la autorización de tratamiento de datos. */
@RestController
@RequestMapping("/api/propietarios")
@Tag(name = "Cierre del negocio")
public class PropietarioController {

    private final PropietarioRepository propietarios;
    private final SesionActual sesion;

    public PropietarioController(PropietarioRepository propietarios, SesionActual sesion) {
        this.propietarios = propietarios;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Lista los propietarios-vendedores de la inmobiliaria")
    public List<PropietarioDto> listar() {
        return propietarios.findByInmobiliariaIdOrderByNombreAsc(sesion.inmobiliariaId()).stream()
                .map(PropietarioDto::desde).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un propietario")
    public PropietarioDto detalle(@PathVariable Long id) {
        return PropietarioDto.desde(buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Guarda un propietario. Exige la autorización de tratamiento de datos (Ley 1581 de 2012)")
    public PropietarioDto crear(@Valid @RequestBody SolicitudPropietario s) {
        if (!Boolean.TRUE.equals(s.autorizacionDatos())) {
            throw new ReglaNegocioException("Para guardar al propietario debes registrar su autorización de "
                    + "tratamiento de datos personales (Ley 1581 de 2012).");
        }
        UsuarioActual usuario = sesion.usuario();
        Propietario p = new Propietario(usuario.inmobiliariaId(), usuario.id());
        aplicar(p, s);
        return PropietarioDto.desde(propietarios.save(p));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza los datos de un propietario")
    public PropietarioDto actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudPropietario s) {
        Propietario p = buscar(id);
        aplicar(p, s);
        return PropietarioDto.desde(propietarios.save(p));
    }

    private Propietario buscar(Long id) {
        return propietarios.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Propietario", id));
    }

    private static void aplicar(Propietario p, SolicitudPropietario s) {
        p.actualizar(s.tipoPersona(), s.nombre().strip(), s.tipoDocumento().strip(), s.numeroDocumento().strip(),
                s.email() == null || s.email().isBlank() ? null : s.email().strip(),
                s.telefono() == null || s.telefono().isBlank() ? null : s.telefono().strip(), s.estadoCivil());
    }

    public record SolicitudPropietario(
            @NotNull TipoPersona tipoPersona,
            @NotBlank @Size(max = 150) String nombre,
            @NotBlank @Size(max = 12) String tipoDocumento,
            @NotBlank @Size(max = 30) String numeroDocumento,
            @Email @Size(max = 150) String email,
            @Size(max = 30) String telefono,
            EstadoCivil estadoCivil,
            Boolean autorizacionDatos) {
    }

    public record PropietarioDto(Long id, TipoPersona tipoPersona, String nombre, String tipoDocumento,
                                 String numeroDocumento, String email, String telefono, EstadoCivil estadoCivil,
                                 boolean autorizacionDatos, Instant autorizacionFecha) {
        static PropietarioDto desde(Propietario p) {
            return new PropietarioDto(p.getId(), p.getTipoPersona(), p.getNombre(), p.getTipoDocumento(),
                    p.getNumeroDocumento(), p.getEmail(), p.getTelefono(), p.getEstadoCivil(),
                    p.isAutorizacionDatos(), p.getAutorizacionFecha());
        }
    }
}
