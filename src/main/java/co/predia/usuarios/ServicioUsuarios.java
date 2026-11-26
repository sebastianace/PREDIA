package co.predia.usuarios;

import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** El ADMIN administra al personal de su inmobiliaria. Los propietarios se invitan desde su ficha (ver ServicioAccesoPortal). */
@Service
public class ServicioUsuarios {

    private static final List<Rol> PERSONAL = List.of(Rol.ADMIN, Rol.ASESOR);

    public record UsuarioDto(Long id, String nombre, String email, Rol rol, EstadoAcceso estado, Instant ultimoIngreso,
                             Instant invitacionVence, boolean esUstedMismo) {
    }

    /**
     * {@code enlace} se devuelve a quien invita para que también pueda compartirlo por otro medio (por ejemplo si el
     * correo no llegó): es personal, vence a las 72 horas y sirve una sola vez.
     */
    public record Invitado(UsuarioDto usuario, boolean correoEnviado, String enlace) {
    }

    private final UsuarioRepository usuarios;
    private final InmobiliariaRepository inmobiliarias;
    private final ServicioInvitaciones invitaciones;
    private final CorreoInvitaciones correos;

    public ServicioUsuarios(UsuarioRepository usuarios, InmobiliariaRepository inmobiliarias,
                            ServicioInvitaciones invitaciones, CorreoInvitaciones correos) {
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.invitaciones = invitaciones;
        this.correos = correos;
    }

    @Transactional(readOnly = true)
    public List<UsuarioDto> listar(UsuarioActual admin) {
        return usuarios.findByInmobiliariaIdAndRolInOrderByNombreAsc(admin.inmobiliariaId(), PERSONAL).stream()
                .map(u -> dto(u, admin)).toList();
    }

    @Transactional
    public Invitado crear(UsuarioActual admin, String nombre, String email, Rol rol) {
        if (rol == null || !rol.esPersonal()) {
            throw new ReglaNegocioException("Aquí solo se crean administradores y asesores. "
                    + "A un propietario se le invita desde su negocio.");
        }
        String correo = normalizar(email);
        if (usuarios.existsByEmailIgnoreCase(correo)) {
            throw new ReglaNegocioException("Ya existe un usuario con ese correo.");
        }
        Usuario nuevo = usuarios.save(Usuario.invitado(admin.inmobiliariaId(), nombre.strip(), correo, rol, null,
                admin.id()));
        return invitar(admin, nuevo);
    }

    @Transactional
    public UsuarioDto actualizar(UsuarioActual admin, Long id, Rol rol, Boolean activo) {
        Usuario u = personal(admin, id);
        Rol nuevoRol = rol == null ? u.getRol() : rol;
        boolean nuevoActivo = activo == null ? u.isActivo() : activo;
        if (!nuevoRol.esPersonal()) {
            throw new ReglaNegocioException("El rol debe ser ADMIN o ASESOR.");
        }
        if (u.getId().equals(admin.id()) && (!nuevoActivo || nuevoRol != Rol.ADMIN)) {
            throw new ReglaNegocioException("No puedes quitarte tu propio acceso de administrador.");
        }
        boolean dejaDeSerAdminActivo = u.getRol() == Rol.ADMIN && u.isActivo() && (!nuevoActivo || nuevoRol != Rol.ADMIN);
        if (dejaDeSerAdminActivo
                && usuarios.countByInmobiliariaIdAndRolAndActivoTrue(admin.inmobiliariaId(), Rol.ADMIN) <= 1) {
            throw new ReglaNegocioException("La inmobiliaria debe tener al menos un administrador activo.");
        }
        if (nuevoActivo && !u.tieneContrasena()) {
            throw new ReglaNegocioException("Esta persona todavía no ha creado su contraseña: reenvíale la invitación.");
        }
        u.cambiarRol(nuevoRol);
        u.cambiarActivo(nuevoActivo);
        return dto(usuarios.save(u), admin);
    }

    @Transactional
    public Invitado reenviar(UsuarioActual admin, Long id) {
        return invitar(admin, personal(admin, id));
    }

    private Invitado invitar(UsuarioActual admin, Usuario u) {
        ServicioInvitaciones.Emitida emitida = invitaciones.emitir(u, admin.id());
        String inmobiliaria = inmobiliarias.findById(u.getInmobiliariaId()).map(i -> i.getNombre()).orElse("Predia");
        CorreoInvitaciones.Correo correo = correos.redactar(u.getRol(), u.getNombre(), u.getEmail(), inmobiliaria,
                null, admin.nombre(), emitida.enlace(), emitida.expiraEn(), u.tieneContrasena());
        boolean enviado = correos.enviar(u.getInmobiliariaId(), correo, admin.email());
        return new Invitado(dto(u, admin), enviado, emitida.enlace());
    }

    private Usuario personal(UsuarioActual admin, Long id) {
        return usuarios.findByIdAndInmobiliariaId(id, admin.inmobiliariaId())
                .filter(u -> u.getRol().esPersonal())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private UsuarioDto dto(Usuario u, UsuarioActual quien) {
        Instant vence = u.tieneContrasena() ? null : invitaciones.ultima(u.getId())
                .filter(i -> i.getUsadaEn() == null).map(InvitacionAcceso::getExpiraEn).orElse(null);
        return new UsuarioDto(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), estado(u), u.getUltimoIngreso(),
                vence, u.getId().equals(quien.id()));
    }

    static EstadoAcceso estado(Usuario u) {
        if (!u.tieneContrasena()) {
            return EstadoAcceso.INVITADO;
        }
        return u.isActivo() ? EstadoAcceso.ACTIVO : EstadoAcceso.DESACTIVADO;
    }

    static String normalizar(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
