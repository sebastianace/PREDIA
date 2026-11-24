package co.predia.comprador;

import co.predia.clientes.ClienteRepository;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Las solicitudes de los compradores («Quiero más información») que atiende el personal. */
@Service
public class ServicioSolicitudes {

    public record SolicitudDto(Long id, Long clienteId, String clienteNombre, Long inmuebleId, String inmuebleTitulo,
                               String mensaje, Instant creadaEn) {
    }

    private final SolicitudCompradorRepository solicitudes;
    private final ClienteRepository clientes;
    private final InmuebleRepository inmuebles;

    public ServicioSolicitudes(SolicitudCompradorRepository solicitudes, ClienteRepository clientes,
                               InmuebleRepository inmuebles) {
        this.solicitudes = solicitudes;
        this.clientes = clientes;
        this.inmuebles = inmuebles;
    }

    /** El ADMIN ve las de toda la inmobiliaria; el asesor, las de los clientes que él registró. */
    @Transactional(readOnly = true)
    public List<SolicitudDto> pendientes(UsuarioActual u) {
        return visibles(u).stream().map(s -> dto(s)).toList();
    }

    @Transactional(readOnly = true)
    public long contarPendientes(UsuarioActual u) {
        return visibles(u).size();
    }

    @Transactional
    public void atender(UsuarioActual u, Long id) {
        SolicitudComprador s = solicitudes.findByIdAndInmobiliariaId(id, u.inmobiliariaId())
                .filter(x -> visibles(u).stream().anyMatch(v -> v.getId().equals(x.getId())) || x.getAtendidaEn() != null)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud", id));
        s.atender(u.id());
        solicitudes.save(s);
    }

    private List<SolicitudComprador> visibles(UsuarioActual u) {
        return u.esAdmin() ? solicitudes.findByInmobiliariaIdAndAtendidaEnIsNullOrderByCreadaEnAsc(u.inmobiliariaId())
                : solicitudes.pendientesDelAsesor(u.inmobiliariaId(), u.id());
    }

    private SolicitudDto dto(SolicitudComprador s) {
        String cliente = clientes.findByIdAndInmobiliariaId(s.getClienteId(), s.getInmobiliariaId())
                .map(c -> c.getNombre()).orElse("—");
        String inmueble = s.getInmuebleId() == null ? null
                : inmuebles.findByIdAndInmobiliariaId(s.getInmuebleId(), s.getInmobiliariaId())
                .map(i -> i.getTitulo()).orElse(null);
        return new SolicitudDto(s.getId(), s.getClienteId(), cliente, s.getInmuebleId(), inmueble, s.getMensaje(),
                s.getCreadaEn());
    }
}
