package co.predia.auth;

import co.predia.perfil.FotoPerfilRepositorio;
import co.predia.perfil.SujetoFoto;
import co.predia.comun.seguridad.ServicioJwt;
import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.CredencialesInvalidasException;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.usuarios.ServicioInvitaciones;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final FotoPerfilRepositorio fotos;
    private final ServicioInvitaciones invitaciones;

    public AuthController(UsuarioRepository usuarios, InmobiliariaRepository inmobiliarias,
                          PasswordEncoder passwordEncoder, ServicioJwt servicioJwt, SesionActual sesion,
                          ServicioInvitaciones invitaciones, FotoPerfilRepositorio fotos) {
        this.fotos = fotos;
        this.invitaciones = invitaciones;
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.passwordEncoder = passwordEncoder;
        this.servicioJwt = servicioJwt;
        this.sesion = sesion;
    }

    @PostMapping("/login")
    @SecurityRequirements   // público: no lleva candado en Swagger
    @Operation(summary = "Inicia sesión y devuelve un JWT")
    public RespuestaLogin login(@Valid @RequestBody SolicitudLogin solicitud) {
        Usuario usuario = usuarios.findByEmailIgnoreCaseAndActivoTrue(solicitud.email().trim())
                .filter(u -> passwordEncoder.matches(solicitud.password(), u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);
        usuario.registrarIngreso();
        usuarios.save(usuario);
        ServicioJwt.TokenEmitido token = servicioJwt.emitir(usuario);
        return new RespuestaLogin(token.token(), token.expiraEn(), perfil(usuario.getId(), usuario.getInmobiliariaId(),
                usuario.getNombre(), usuario.getEmail(), usuario.getRol()));
    }

    @GetMapping("/invitaciones/{token}")
    @SecurityRequirements   // público: no lleva candado en Swagger
    @Operation(summary = "Valida un enlace de invitación (público): devuelve a quién y de qué inmobiliaria")
    public ServicioInvitaciones.Vista invitacion(@PathVariable String token) {
        return invitaciones.ver(token);
    }

    @PostMapping("/activar")
    @SecurityRequirements   // público: no lleva candado en Swagger
    @Operation(summary = "Crea (o recupera) la contraseña con un enlace de invitación y deja la cuenta activa")
    public RespuestaLogin activar(@Valid @RequestBody SolicitudActivacion s) {
        Usuario usuario = invitaciones.activar(s.token(), s.password());
        usuario.registrarIngreso();
        usuarios.save(usuario);
        ServicioJwt.TokenEmitido token = servicioJwt.emitir(usuario);
        return new RespuestaLogin(token.token(), token.expiraEn(), perfil(usuario.getId(), usuario.getInmobiliariaId(),
                usuario.getNombre(), usuario.getEmail(), usuario.getRol()));
    }

    public record SolicitudActivacion(@NotBlank @Size(max = 200) String token,
                                      @NotBlank @Size(min = ServicioInvitaciones.LARGO_MINIMO, max = 72) String password) {
    }

    @GetMapping("/yo")
    @Operation(summary = "Datos del usuario autenticado")
    public PerfilUsuario yo() {
        UsuarioActual u = sesion.usuario();
        // El nombre del token puede estar viejo si cambió después de iniciar sesión: se lee el actual.
        String nombre = usuarios.findByIdAndInmobiliariaId(u.id(), u.inmobiliariaId()).map(Usuario::getNombre).orElse(u.nombre());
        return perfil(u.id(), u.inmobiliariaId(), nombre, u.email(), u.rol());
    }

    private PerfilUsuario perfil(Long id, Long inmobiliariaId, String nombre, String email, Rol rol) {
        String nombreInmobiliaria = inmobiliarias.findById(inmobiliariaId).map(i -> i.getNombre()).orElse("");
        return new PerfilUsuario(id, nombre, email, rol, inmobiliariaId, nombreInmobiliaria,
                fotos.version(inmobiliariaId, SujetoFoto.USUARIO, id).orElse(null));
    }

    public record SolicitudLogin(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RespuestaLogin(String token, Instant expiraEn, PerfilUsuario usuario) {
    }

    public record PerfilUsuario(Long id, String nombre, String email, Rol rol, Long inmobiliariaId,
                                String inmobiliaria, String fotoVersion) {
    }
}
