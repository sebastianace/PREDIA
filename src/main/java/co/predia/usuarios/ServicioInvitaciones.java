package co.predia.usuarios;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Crea y usa los enlaces de invitación. Un enlace vence a las 72 horas, sirve una sola vez y, al crear uno nuevo, el
 * anterior deja de valer. Sirve para activar una cuenta nueva y para elegir una contraseña nueva si se olvidó.
 */
@Service
public class ServicioInvitaciones {

    public static final Duration VIGENCIA = Duration.ofHours(72);
    public static final int LARGO_MINIMO = 10;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** El token en claro solo existe en este objeto y en el enlace: en la base queda su hash. */
    public record Emitida(String token, String enlace, Instant expiraEn) {
    }

    /** Lo que ve la persona al abrir el enlace, sin datos sensibles. */
    public record Vista(String nombre, String inmobiliaria, Rol rol, boolean yaTieneCuenta) {
    }

    private final InvitacionAccesoRepository invitaciones;
    private final UsuarioRepository usuarios;
    private final InmobiliariaRepository inmobiliarias;
    private final PasswordEncoder passwordEncoder;
    private final PropiedadesUsuarios propiedades;
    private final Clock reloj;

    public ServicioInvitaciones(InvitacionAccesoRepository invitaciones, UsuarioRepository usuarios,
                                InmobiliariaRepository inmobiliarias, PasswordEncoder passwordEncoder,
                                PropiedadesUsuarios propiedades, Clock reloj) {
        this.invitaciones = invitaciones;
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.passwordEncoder = passwordEncoder;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Transactional
    public Emitida emitir(Usuario usuario, Long creadaPor) {
        Instant ahora = reloj.instant();
        invitaciones.findByUsuarioIdAndUsadaEnIsNull(usuario.getId()).forEach(i -> i.gastar(ahora));
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = ahora.plus(VIGENCIA);
        invitaciones.save(new InvitacionAcceso(usuario.getInmobiliariaId(), usuario.getId(), hash(token), expira,
                creadaPor));
        return new Emitida(token, enlace(token), expira);
    }

    @Transactional(readOnly = true)
    public Vista ver(String token) {
        InvitacionAcceso invitacion = vigente(token);
        Usuario usuario = usuarios.findById(invitacion.getUsuarioId()).orElseThrow(ServicioInvitaciones::invalido);
        String inmobiliaria = inmobiliarias.findById(usuario.getInmobiliariaId()).map(i -> i.getNombre()).orElse("");
        return new Vista(usuario.getNombre(), inmobiliaria, usuario.getRol(), usuario.tieneContrasena());
    }

    @Transactional
    public Usuario activar(String token, String password) {
        if (password == null || password.length() < LARGO_MINIMO) {
            throw new ReglaNegocioException("La contraseña debe tener al menos " + LARGO_MINIMO + " caracteres.");
        }
        InvitacionAcceso invitacion = vigente(token);
        Usuario usuario = usuarios.findById(invitacion.getUsuarioId()).orElseThrow(ServicioInvitaciones::invalido);
        invitacion.gastar(reloj.instant());
        usuario.fijarContrasena(passwordEncoder.encode(password));
        return usuarios.save(usuario);
    }

    /** La invitación más reciente de un usuario, para mostrar su estado (invitado el…, vence el…). */
    @Transactional(readOnly = true)
    public java.util.Optional<InvitacionAcceso> ultima(Long usuarioId) {
        return invitaciones.findFirstByUsuarioIdOrderByCreadaEnDesc(usuarioId);
    }

    public String enlace(String token) {
        String base = propiedades.urlPublica() == null ? "" : propiedades.urlPublica().replaceAll("/+$", "");
        return base + "/activar?token=" + token;
    }

    private InvitacionAcceso vigente(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            throw invalido();
        }
        return invitaciones.findByTokenHash(hash(token))
                .filter(i -> i.vigente(reloj.instant()))
                .orElseThrow(ServicioInvitaciones::invalido);
    }

    /** Un enlace inexistente, vencido o ya usado responden igual: no se revela cuál de los tres es. */
    private static RecursoNoEncontradoException invalido() {
        return new RecursoNoEncontradoException(
                "Este enlace ya no es válido: venció o ya se usó. Quien le invitó puede enviarle uno nuevo.");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
