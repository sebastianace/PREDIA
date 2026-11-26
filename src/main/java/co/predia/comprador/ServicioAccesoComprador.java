package co.predia.comprador;

import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.usuarios.CorreoInvitaciones;
import co.predia.usuarios.EstadoAcceso;
import co.predia.usuarios.InvitacionAcceso;
import co.predia.usuarios.ServicioAccesoPortal.AccesoPortal;
import co.predia.usuarios.ServicioAccesoPortal.Invitacion;
import co.predia.usuarios.ServicioInvitaciones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * El asesor invita a un cliente comprador a su portal y administra ese acceso. Invitar es una acción explícita de una
 * persona: Predia nunca envía la invitación por su cuenta.
 */
@Service
public class ServicioAccesoComprador {

    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final InmobiliariaRepository inmobiliarias;
    private final ServicioInvitaciones invitaciones;
    private final CorreoInvitaciones correos;

    public ServicioAccesoComprador(ClienteRepository clientes, UsuarioRepository usuarios,
                                   InmobiliariaRepository inmobiliarias, ServicioInvitaciones invitaciones,
                                   CorreoInvitaciones correos) {
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.invitaciones = invitaciones;
        this.correos = correos;
    }

    @Transactional(readOnly = true)
    public AccesoPortal estado(UsuarioActual asesor, Long clienteId) {
        Cliente c = cliente(asesor, clienteId);
        return acceso(c, usuarios.findByInmobiliariaIdAndClienteId(asesor.inmobiliariaId(), c.getId()));
    }

    /** El correo que se enviará, con un marcador en vez del enlace: el asesor lo revisa antes de enviarlo. */
    @Transactional(readOnly = true)
    public CorreoInvitaciones.Correo vistaPrevia(UsuarioActual asesor, Long clienteId) {
        Cliente c = cliente(asesor, clienteId);
        String email = exigirCorreo(c);
        boolean recuperar = usuarios.findByInmobiliariaIdAndClienteId(asesor.inmobiliariaId(), c.getId())
                .map(Usuario::tieneContrasena).orElse(false);
        return correos.redactar(Rol.COMPRADOR, c.getNombre(), email, inmobiliaria(asesor), null, asesor.nombre(), null,
                null, recuperar);
    }

    @Transactional
    public Invitacion invitar(UsuarioActual asesor, Long clienteId) {
        Cliente c = cliente(asesor, clienteId);
        String email = exigirCorreo(c);
        Optional<Usuario> existente = usuarios.findByInmobiliariaIdAndClienteId(asesor.inmobiliariaId(), c.getId());
        Usuario u;
        if (existente.isEmpty()) {
            if (usuarios.existsByEmailIgnoreCase(email)) {
                throw new ReglaNegocioException("Ese correo ya lo usa otra persona en Predia (por ejemplo, como "
                        + "propietario). Un correo solo puede tener un rol: usa otro para este cliente.");
            }
            u = usuarios.save(Usuario.invitadoComprador(asesor.inmobiliariaId(), c.getNombre(), email, c.getId(),
                    asesor.id()));
        } else {
            Usuario actual = existente.get();
            if (!actual.getEmail().equalsIgnoreCase(email)
                    && usuarios.findByEmailIgnoreCase(email).filter(o -> !o.getId().equals(actual.getId())).isPresent()) {
                throw new ReglaNegocioException("Ese correo ya lo usa otra persona en Predia.");
            }
            actual.actualizarDatos(c.getNombre(), email);
            u = usuarios.save(actual);
        }
        ServicioInvitaciones.Emitida emitida = invitaciones.emitir(u, asesor.id());
        CorreoInvitaciones.Correo correo = correos.redactar(Rol.COMPRADOR, c.getNombre(), email, inmobiliaria(asesor),
                null, asesor.nombre(), emitida.enlace(), emitida.expiraEn(), u.tieneContrasena());
        boolean enviado = correos.enviar(asesor.inmobiliariaId(), correo, asesor.email());
        return new Invitacion(acceso(c, Optional.of(u)), enviado, emitida.enlace());
    }

    @Transactional
    public AccesoPortal cambiarAcceso(UsuarioActual asesor, Long clienteId, boolean activo) {
        Cliente c = cliente(asesor, clienteId);
        Usuario u = usuarios.findByInmobiliariaIdAndClienteId(asesor.inmobiliariaId(), c.getId())
                .orElseThrow(() -> new ReglaNegocioException("Este cliente todavía no tiene acceso al portal."));
        if (activo && !u.tieneContrasena()) {
            throw new ReglaNegocioException("El cliente todavía no ha creado su contraseña: reenvíale la invitación.");
        }
        u.cambiarActivo(activo);
        return acceso(c, Optional.of(usuarios.save(u)));
    }

    private AccesoPortal acceso(Cliente c, Optional<Usuario> usuario) {
        if (usuario.isEmpty()) {
            return new AccesoPortal(EstadoAcceso.SIN_INVITAR, c.getEmail(), null, null, null);
        }
        Usuario u = usuario.get();
        Optional<InvitacionAcceso> ultima = invitaciones.ultima(u.getId());
        Instant vence = u.tieneContrasena() ? null
                : ultima.filter(i -> i.getUsadaEn() == null).map(InvitacionAcceso::getExpiraEn).orElse(null);
        EstadoAcceso estado = !u.tieneContrasena() ? EstadoAcceso.INVITADO
                : u.isActivo() ? EstadoAcceso.ACTIVO : EstadoAcceso.DESACTIVADO;
        return new AccesoPortal(estado, u.getEmail(), ultima.map(InvitacionAcceso::getCreadaEn).orElse(null), vence,
                u.getUltimoIngreso());
    }

    private Cliente cliente(UsuarioActual asesor, Long id) {
        return clientes.findByIdAndInmobiliariaId(id, asesor.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    private static String exigirCorreo(Cliente c) {
        if (c.getEmail() == null || c.getEmail().isBlank()) {
            throw new ReglaNegocioException("El cliente no tiene correo registrado. Agrégalo antes de invitarlo.");
        }
        return c.getEmail().strip().toLowerCase(Locale.ROOT);
    }

    private String inmobiliaria(UsuarioActual asesor) {
        return inmobiliarias.findById(asesor.inmobiliariaId()).map(i -> i.getNombre()).orElse("Predia");
    }
}
