package co.predia.auth;

import co.predia.comun.seguridad.ServicioJwt;
import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.CredencialesInvalidasException;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final UsuarioRepository usuarios;
    private final InmobiliariaRepository inmobiliarias;
    private final PasswordEncoder passwordEncoder;
    private final ServicioJwt servicioJwt;
    private final SesionActual sesion;

    public AuthController(UsuarioRepository usuarios, InmobiliariaRepository inmobiliarias,
                          PasswordEncoder passwordEncoder, ServicioJwt servicioJwt, SesionActual sesion) {
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.passwordEncoder = passwordEncoder;
        this.servicioJwt = servicioJwt;
        this.sesion = sesion;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve un JWT")
    public RespuestaLogin login(@Valid @RequestBody SolicitudLogin solicitud) {
        Usuario usuario = usuarios.findByEmailIgnoreCaseAndActivoTrue(solicitud.email().trim())
                .filter(u -> passwordEncoder.matches(solicitud.password(), u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);
        ServicioJwt.TokenEmitido token = servicioJwt.emitir(usuario);
        return new RespuestaLogin(token.token(), token.expiraEn(), perfil(usuario.getId(), usuario.getInmobiliariaId(),
                usuario.getNombre(), usuario.getEmail(), usuario.getRol()));
    }

    @GetMapping("/yo")
    @Operation(summary = "Datos del usuario autenticado")
    public PerfilUsuario yo() {
        UsuarioActual u = sesion.usuario();
        return perfil(u.id(), u.inmobiliariaId(), u.nombre(), u.email(), u.rol());
    }

    private PerfilUsuario perfil(Long id, Long inmobiliariaId, String nombre, String email, Rol rol) {
        String nombreInmobiliaria = inmobiliarias.findById(inmobiliariaId).map(i -> i.getNombre()).orElse("");
        return new PerfilUsuario(id, nombre, email, rol, inmobiliariaId, nombreInmobiliaria);
    }

    public record SolicitudLogin(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RespuestaLogin(String token, Instant expiraEn, PerfilUsuario usuario) {
    }

    public record PerfilUsuario(Long id, String nombre, String email, Rol rol, Long inmobiliariaId,
                                String inmobiliaria) {
    }
}
