package co.predia.usuarios;

import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * El asesor invita al propietario a su portal y administra ese acceso. Invitar es una acción explícita de una persona:
 * Predia nunca envía la invitación por su cuenta.
 */
@Service
public class ServicioAccesoPortal {

    public record AccesoPortal(EstadoAcceso estado, String email, Instant invitadoEn, Instant invitacionVence,
                               Instant ultimoIngreso) {
    }

    public record Invitacion(AccesoPortal acceso, boolean correoEnviado, String enlace) {
    }

    private final PropietarioRepository propietarios;
    private final NegocioRepository negocios;
    private final InmuebleRepository inmuebles;
    private final UsuarioRepository usuarios;
    private final InmobiliariaRepository inmobiliarias;
    private final ServicioInvitaciones invitaciones;
    private final CorreoInvitaciones correos;

    public ServicioAccesoPortal(PropietarioRepository propietarios, NegocioRepository negocios,
                                InmuebleRepository inmuebles, UsuarioRepository usuarios,
                                InmobiliariaRepository inmobiliarias, ServicioInvitaciones invitaciones,
                                CorreoInvitaciones correos) {
        this.propietarios = propietarios;
        this.negocios = negocios;
        this.inmuebles = inmuebles;
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.invitaciones = invitaciones;
        this.correos = correos;
    }

    @Transactional(readOnly = true)
    public AccesoPortal estado(UsuarioActual asesor, Long propietarioId) {
        Propietario p = propietario(asesor, propietarioId);
        return acceso(p, usuarios.findByInmobiliariaIdAndPropietarioId(asesor.inmobiliariaId(), p.getId()));
    }

    /** El correo que se enviará, con un marcador en vez del enlace: el asesor lo revisa antes de enviarlo. */
    @Transactional(readOnly = true)
    public CorreoInvitaciones.Correo vistaPrevia(UsuarioActual asesor, Long propietarioId) {
        Propietario p = propietario(asesor, propietarioId);
        exigirCorreo(p);
        boolean recuperar = usuarios.findByInmobiliariaIdAndPropietarioId(asesor.inmobiliariaId(), p.getId())
                .map(Usuario::tieneContrasena).orElse(false);
        return correos.redactar(Rol.PROPIETARIO, p.getNombre(), p.getEmail(), inmobiliaria(asesor),
                inmueble(asesor, p), asesor.nombre(), null, null, recuperar);
    }

    @Transactional
    public Invitacion invitar(UsuarioActual asesor, Long propietarioId) {
        Propietario p = propietario(asesor, propietarioId);
        String email = exigirCorreo(p);
        Optional<Usuario> existente = usuarios.findByInmobiliariaIdAndPropietarioId(asesor.inmobiliariaId(), p.getId());
        Usuario u;
        if (existente.isEmpty()) {
            if (usuarios.existsByEmailIgnoreCase(email)) {
                throw new ReglaNegocioException("Ese correo ya lo usa otra persona en Predia. "
                        + "Corrige el correo del propietario antes de invitarlo.");
            }
            u = usuarios.save(Usuario.invitado(asesor.inmobiliariaId(), p.getNombre(), email, Rol.PROPIETARIO,
                    p.getId(), asesor.id()));
        } else {
            u = existente.get();
            if (!u.getEmail().equalsIgnoreCase(email)) {
                // El asesor corrigió el correo del propietario: la cuenta pasa a ese correo.
                if (usuarios.findByEmailIgnoreCase(email).filter(o -> !o.getId().equals(existente.get().getId())).isPresent()) {
                    throw new ReglaNegocioException("Ese correo ya lo usa otra persona en Predia.");
                }
            }
            u.actualizarDatos(p.getNombre(), email);
            u = usuarios.save(u);
        }
        ServicioInvitaciones.Emitida emitida = invitaciones.emitir(u, asesor.id());
        CorreoInvitaciones.Correo correo = correos.redactar(Rol.PROPIETARIO, p.getNombre(), email,
                inmobiliaria(asesor), inmueble(asesor, p), asesor.nombre(), emitida.enlace(), emitida.expiraEn(),
                u.tieneContrasena());
        boolean enviado = correos.enviar(asesor.inmobiliariaId(), correo, asesor.email());
        return new Invitacion(acceso(p, Optional.of(u)), enviado, emitida.enlace());
    }

    @Transactional
    public AccesoPortal cambiarAcceso(UsuarioActual asesor, Long propietarioId, boolean activo) {
        Propietario p = propietario(asesor, propietarioId);
        Usuario u = usuarios.findByInmobiliariaIdAndPropietarioId(asesor.inmobiliariaId(), p.getId())
                .orElseThrow(() -> new ReglaNegocioException("Este propietario todavía no tiene acceso al portal."));
        if (activo && !u.tieneContrasena()) {
            throw new ReglaNegocioException("El propietario todavía no ha creado su contraseña: reenvíale la invitación.");
        }
        u.cambiarActivo(activo);
        return acceso(p, Optional.of(usuarios.save(u)));
    }

    private AccesoPortal acceso(Propietario p, Optional<Usuario> usuario) {
        if (usuario.isEmpty()) {
            return new AccesoPortal(EstadoAcceso.SIN_INVITAR, p.getEmail(), null, null, null);
        }
        Usuario u = usuario.get();
        Optional<InvitacionAcceso> ultima = invitaciones.ultima(u.getId());
        Instant vence = u.tieneContrasena() ? null
                : ultima.filter(i -> i.getUsadaEn() == null).map(InvitacionAcceso::getExpiraEn).orElse(null);
        return new AccesoPortal(ServicioUsuarios.estado(u), u.getEmail(),
                ultima.map(InvitacionAcceso::getCreadaEn).orElse(null), vence, u.getUltimoIngreso());
    }

    private Propietario propietario(UsuarioActual asesor, Long id) {
        return propietarios.findByIdAndInmobiliariaId(id, asesor.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Propietario", id));
    }

    private static String exigirCorreo(Propietario p) {
        if (p.getEmail() == null || p.getEmail().isBlank()) {
            throw new ReglaNegocioException("El propietario no tiene correo registrado. Agrégalo antes de invitarlo.");
        }
        return ServicioUsuarios.normalizar(p.getEmail());
    }

    private String inmobiliaria(UsuarioActual asesor) {
        return inmobiliarias.findById(asesor.inmobiliariaId()).map(i -> i.getNombre()).orElse("Predia");
    }

    /** El inmueble del negocio más reciente del propietario, para que el correo diga de qué venta se trata. */
    private String inmueble(UsuarioActual asesor, Propietario p) {
        return negocios.findByInmobiliariaIdAndPropietarioIdOrderByCreadoEnDesc(asesor.inmobiliariaId(), p.getId())
                .stream().findFirst().map(Negocio::getInmuebleId)
                .flatMap(id -> inmuebles.findByIdAndInmobiliariaId(id, asesor.inmobiliariaId()))
                .map(i -> i.getTitulo()).orElse("su inmueble");
    }
}
