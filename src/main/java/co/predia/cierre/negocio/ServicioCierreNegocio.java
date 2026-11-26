package co.predia.cierre.negocio;

import co.predia.cierre.reglas.EstadoDocumental;
import co.predia.cierre.reglas.ServicioEstadoDocumental;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.EstadoInmueble;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Cerrar un negocio es registrar una venta firmada: la persona confirma (el asesor del negocio o un administrador) y las
 * reglas deciden si se puede (la lista de documentos debe estar completa). Al cerrarlo el inmueble queda VENDIDO y la venta
 * cuenta para su asesor; solo un administrador puede deshacerlo, y siempre con una nota. De aquí salen las cifras de
 * rendimiento: no hay ningún número que alguien escriba a mano.
 */
@Service
public class ServicioCierreNegocio {

    static final int MINIMO_NOTA = 10;

    private final NegocioRepository negocios;
    private final InmuebleRepository inmuebles;
    private final ServicioEstadoDocumental estadoDocumental;
    private final Clock reloj;

    public ServicioCierreNegocio(NegocioRepository negocios, InmuebleRepository inmuebles,
                                 ServicioEstadoDocumental estadoDocumental, Clock reloj) {
        this.negocios = negocios;
        this.inmuebles = inmuebles;
        this.estadoDocumental = estadoDocumental;
        this.reloj = reloj;
    }

    @Transactional
    public Negocio cerrar(UsuarioActual actor, Long negocioId, BigDecimal valorCierre, String nota) {
        Negocio negocio = buscar(actor, negocioId);
        if (negocio.getEstado() != EstadoNegocio.ABIERTO) {
            throw new ReglaNegocioException(negocio.getEstado() == EstadoNegocio.FIRMADO
                    ? "Este negocio ya está cerrado." : "Un negocio cancelado no se puede cerrar: ábrelo de nuevo primero.");
        }
        if (!actor.esAdmin() && !negocio.getAsesorId().equals(actor.id())) {
            throw new AccessDeniedException("Solo el asesor del negocio o un administrador puede cerrarlo.");
        }
        Inmueble inmueble = inmuebles.findByIdAndInmobiliariaId(negocio.getInmuebleId(), actor.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", negocio.getInmuebleId()));
        if (inmueble.getOperacion() != Operacion.VENTA) {
            throw new ReglaNegocioException("Solo se cierran negocios de inmuebles en venta.");
        }
        if (inmueble.getEstado() == EstadoInmueble.VENDIDO) {
            throw new ReglaNegocioException("Ese inmueble ya figura como vendido en otro negocio.");
        }

        // Las reglas deciden: sin la lista de documentos completa no se cierra, salvo un administrador y con una nota.
        EstadoDocumental estado = estadoDocumental.calcular(negocio);
        String notaLimpia = nota == null ? null : nota.strip();
        if (!estado.listoParaFirmar()) {
            if (!actor.esAdmin()) {
                throw new ReglaNegocioException("Todavía no se puede cerrar: " + estado.semaforo().toLowerCase()
                        + ". Resuélvelo o pide a un administrador que lo cierre dejando una nota.");
            }
            if (notaLimpia == null || notaLimpia.length() < MINIMO_NOTA) {
                throw new ReglaNegocioException("Faltan documentos (" + estado.semaforo().toLowerCase() + "). Para cerrarlo igual, "
                        + "escribe una nota que explique por qué (mínimo " + MINIMO_NOTA + " caracteres).");
            }
        }

        BigDecimal valor = valorCierre != null ? valorCierre : inmueble.getPrecioVenta();
        if (valor == null || valor.signum() <= 0) {
            throw new ReglaNegocioException("Indica por cuánto se vendió el inmueble: debe ser mayor que cero.");
        }

        negocio.cerrar(actor.id(), valor, notaLimpia == null || notaLimpia.isEmpty() ? null : notaLimpia, Instant.now(reloj));
        inmueble.setEstado(EstadoInmueble.VENDIDO);
        inmuebles.save(inmueble);
        return negocios.save(negocio);
    }

    @Transactional
    public Negocio reabrir(UsuarioActual actor, Long negocioId, String nota) {
        if (!actor.esAdmin()) {
            throw new AccessDeniedException("Solo un administrador puede reabrir un negocio cerrado.");
        }
        Negocio negocio = buscar(actor, negocioId);
        if (negocio.getEstado() != EstadoNegocio.FIRMADO) {
            throw new ReglaNegocioException("Solo se reabre un negocio que está cerrado.");
        }
        if (nota == null || nota.strip().length() < MINIMO_NOTA) {
            throw new ReglaNegocioException("Escribe una nota que explique por qué se reabre (mínimo " + MINIMO_NOTA
                    + " caracteres).");
        }
        negocio.reabrir();
        inmuebles.findByIdAndInmobiliariaId(negocio.getInmuebleId(), actor.inmobiliariaId()).ifPresent(i -> {
            if (i.getEstado() == EstadoInmueble.VENDIDO) {
                i.setEstado(EstadoInmueble.DISPONIBLE);
                inmuebles.save(i);
            }
        });
        return negocios.save(negocio);
    }

    private Negocio buscar(UsuarioActual actor, Long id) {
        return negocios.findByIdAndInmobiliariaId(id, actor.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", id));
    }
}
