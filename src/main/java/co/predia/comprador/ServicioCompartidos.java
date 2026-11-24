package co.predia.comprador;

import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.clientes.Intencion;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.EstadoInmueble;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * El asesor decide qué inmuebles ve un cliente: compartir es una acción explícita suya. El comprador no navega el
 * inventario; solo ve lo que está aquí.
 */
@Service
public class ServicioCompartidos {

    public record CompartidoDto(Long inmuebleId, String titulo, String barrio, String ciudad, BigDecimal precio,
                                EstadoInmueble estado, String nota, Instant compartidoEn,
                                InmuebleCompartido.Interes interes, Instant interesEn) {
    }

    private final InmuebleCompartidoRepository compartidos;
    private final ClienteRepository clientes;
    private final InmuebleRepository inmuebles;

    public ServicioCompartidos(InmuebleCompartidoRepository compartidos, ClienteRepository clientes,
                               InmuebleRepository inmuebles) {
        this.compartidos = compartidos;
        this.clientes = clientes;
        this.inmuebles = inmuebles;
    }

    @Transactional
    public CompartidoDto compartir(UsuarioActual asesor, Long clienteId, Long inmuebleId, String nota) {
        Cliente cliente = cliente(asesor, clienteId);
        if (cliente.getIntencion() != Intencion.COMPRAR) {
            throw new ReglaNegocioException("Por ahora solo se comparten inmuebles con clientes que quieren comprar.");
        }
        Inmueble inmueble = inmueble(asesor, inmuebleId);
        if (inmueble.getEstado() != EstadoInmueble.DISPONIBLE || inmueble.getOperacion() != Operacion.VENTA
                || inmueble.getPrecioVenta() == null) {
            throw new ReglaNegocioException("Solo se comparten inmuebles disponibles y en venta.");
        }
        String limpia = nota == null || nota.isBlank() ? null : nota.strip();
        Optional<InmuebleCompartido> existente = compartidos
                .findByInmobiliariaIdAndClienteIdAndInmuebleId(asesor.inmobiliariaId(), clienteId, inmuebleId);
        InmuebleCompartido c = existente.orElseGet(() -> new InmuebleCompartido(asesor.inmobiliariaId(), clienteId,
                inmuebleId, limpia, asesor.id()));
        if (existente.isPresent()) {
            c.actualizarNota(limpia);
        }
        return dto(compartidos.save(c), inmueble);
    }

    @Transactional(readOnly = true)
    public List<CompartidoDto> listar(UsuarioActual asesor, Long clienteId) {
        cliente(asesor, clienteId);
        return compartidos.findByInmobiliariaIdAndClienteIdOrderByCompartidoEnDesc(asesor.inmobiliariaId(), clienteId)
                .stream().map(c -> dto(c, inmueble(asesor, c.getInmuebleId()))).toList();
    }

    @Transactional
    public void dejarDeCompartir(UsuarioActual asesor, Long clienteId, Long inmuebleId) {
        cliente(asesor, clienteId);
        compartidos.findByInmobiliariaIdAndClienteIdAndInmuebleId(asesor.inmobiliariaId(), clienteId, inmuebleId)
                .ifPresent(compartidos::delete);
    }

    private Cliente cliente(UsuarioActual u, Long id) {
        return clientes.findByIdAndInmobiliariaId(id, u.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    private Inmueble inmueble(UsuarioActual u, Long id) {
        return inmuebles.findByIdAndInmobiliariaId(id, u.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", id));
    }

    private static CompartidoDto dto(InmuebleCompartido c, Inmueble i) {
        return new CompartidoDto(i.getId(), i.getTitulo(), i.getBarrio(), i.getCiudad(), i.getPrecioVenta(),
                i.getEstado(), c.getNota(), c.getCompartidoEn(), c.getInteres(), c.getInteresEn());
    }
}
