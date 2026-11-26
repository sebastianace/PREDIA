package co.predia.cierre.negocio;

import co.predia.cierre.catalogo.CatalogoService;
import co.predia.cierre.catalogo.RequisitoCatalogo;
import co.predia.cierre.catalogo.RequisitoCatalogoRepository;
import co.predia.cierre.catalogo.ReglaVigencia;
import co.predia.clientes.ClienteRepository;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.expedientes.Documento;
import co.predia.expedientes.DocumentoRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Crea los negocios con su lista de requisitos y registra lo que hace el asesor sobre cada uno. */
@Service
public class NegocioService {

    private final NegocioRepository negocios;
    private final InmuebleRepository inmuebles;
    private final PropietarioRepository propietarios;
    private final ClienteRepository clientes;
    private final RequisitoNegocioRepository requisitosNegocio;
    private final RequisitoCatalogoRepository catalogo;
    private final CatalogoService catalogoService;
    private final DocumentoRepository documentos;
    private final Clock reloj;

    public NegocioService(NegocioRepository negocios, InmuebleRepository inmuebles,
                          PropietarioRepository propietarios, ClienteRepository clientes,
                          RequisitoNegocioRepository requisitosNegocio, RequisitoCatalogoRepository catalogo,
                          CatalogoService catalogoService, DocumentoRepository documentos, Clock reloj) {
        this.negocios = negocios;
        this.inmuebles = inmuebles;
        this.propietarios = propietarios;
        this.clientes = clientes;
        this.requisitosNegocio = requisitosNegocio;
        this.catalogo = catalogo;
        this.catalogoService = catalogoService;
        this.documentos = documentos;
        this.reloj = reloj;
    }

    public record Datos(Long inmuebleId, Long propietarioId, Long clienteCompradorId, LocalDate fechaFirmaTentativa,
                        boolean actuaPorApoderado, HipotecaVigente hipotecaVigente, EstadoNegocio estado) {
    }

    @Transactional
    public Negocio crear(UsuarioActual usuario, Datos d) {
        Long tenant = usuario.inmobiliariaId();
        if (d.inmuebleId() == null) {
            throw new ReglaNegocioException("Indica el inmueble del negocio.");
        }
        Inmueble inmueble = inmuebles.findByIdAndInmobiliariaId(d.inmuebleId(), tenant)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", d.inmuebleId()));
        if (inmueble.getOperacion() != Operacion.VENTA) {
            throw new ReglaNegocioException("El cierre de negocio, con los documentos del vendedor, aplica a "
                    + "inmuebles en venta.");
        }
        validarParticipantes(tenant, d);

        Negocio negocio = new Negocio(tenant, inmueble.getId(), usuario.id());
        negocio.actualizar(d.propietarioId(), d.clienteCompradorId(), d.fechaFirmaTentativa(),
                d.actuaPorApoderado(), d.hipotecaVigente() == null ? HipotecaVigente.DESCONOCIDO : d.hipotecaVigente(),
                EstadoNegocio.ABIERTO);
        negocios.save(negocio);

        // Cada requisito activo del catálogo arranca PENDIENTE; el motor decide después cuáles aplican.
        for (RequisitoCatalogo r : catalogoService.asegurar(tenant)) {
            if (r.isActivo()) {
                requisitosNegocio.save(new RequisitoNegocio(tenant, negocio.getId(), r.getId()));
            }
        }
        return negocio;
    }

    @Transactional
    public Negocio actualizar(Long tenant, Long negocioId, Datos d) {
        Negocio negocio = negocios.findByIdAndInmobiliariaId(negocioId, tenant)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", negocioId));
        validarParticipantes(tenant, d);
        EstadoNegocio estadoActual = negocio.getEstado();
        EstadoNegocio nuevo = d.estado() == null ? estadoActual : d.estado();
        // Una venta solo se registra con «Cerrar negocio» (que exige la lista de documentos y deja quién la confirmó) y
        // solo un administrador la deshace: por aquí el estado FIRMADO ni se pone ni se quita.
        if (nuevo == EstadoNegocio.FIRMADO && estadoActual != EstadoNegocio.FIRMADO) {
            throw new ReglaNegocioException("Un negocio se da por firmado con «Cerrar negocio», no editando su estado.");
        }
        if (estadoActual == EstadoNegocio.FIRMADO && nuevo != EstadoNegocio.FIRMADO) {
            throw new ReglaNegocioException("Un negocio firmado solo lo reabre un administrador.");
        }
        negocio.actualizar(d.propietarioId(), d.clienteCompradorId(), d.fechaFirmaTentativa(),
                d.actuaPorApoderado(), d.hipotecaVigente() == null ? HipotecaVigente.DESCONOCIDO : d.hipotecaVigente(),
                estadoActual);
        negocio.cambiarEstadoSinCierre(nuevo, java.time.Instant.now(reloj));
        return negocios.save(negocio);
    }

    @Transactional
    public void aplicarAccion(UsuarioActual usuario, Long negocioId, Long requisitoNegocioId, AccionRequisito accion,
                              LocalDate fechaExpedicion, Long documentoId, String nota) {
        Long tenant = usuario.inmobiliariaId();
        Negocio negocio = negocios.findByIdAndInmobiliariaId(negocioId, tenant)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", negocioId));
        RequisitoNegocio fila = requisitosNegocio.findByIdAndNegocioIdAndInmobiliariaId(requisitoNegocioId,
                        negocioId, tenant)
                .orElseThrow(() -> new RecursoNoEncontradoException("Requisito del negocio", requisitoNegocioId));

        switch (accion) {
            case RECIBIR -> recibir(usuario, negocio, fila, fechaExpedicion, documentoId, nota);
            case NO_APLICA -> fila.marcarNoAplica(usuario.id(), nota);
            case REABRIR -> fila.reabrir();
        }
        requisitosNegocio.save(fila);
    }

    private void recibir(UsuarioActual usuario, Negocio negocio, RequisitoNegocio fila, LocalDate fechaExpedicion,
                         Long documentoId, String nota) {
        RequisitoCatalogo requisito = catalogo.findByIdAndInmobiliariaId(fila.getRequisitoId(),
                usuario.inmobiliariaId()).orElseThrow();
        LocalDate fecha = fechaExpedicion;
        if (documentoId != null) {
            Documento documento = documentos.findByIdAndInmobiliariaId(documentoId, usuario.inmobiliariaId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Documento", documentoId));
            boolean delNegocio = negocio.getId().equals(documento.getNegocioId())
                    || negocio.getInmuebleId().equals(documento.getInmuebleId());
            if (!delNegocio) {
                throw new ReglaNegocioException("Ese documento no pertenece a este negocio ni a su inmueble.");
            }
            if (fecha == null) {
                // Sin fecha explícita se propone la que el asesor ya confirmó, o la que sugirió la IA.
                fecha = documento.getFechaExpedicionConfirmada() != null ? documento.getFechaExpedicionConfirmada()
                        : documento.getFechaExpedicionSugerida();
            }
        }
        if (requisito.getReglaVigencia() != ReglaVigencia.NINGUNA && fecha == null) {
            throw new ReglaNegocioException("Indica la fecha de expedición: este documento tiene vigencia y Predia la "
                    + "necesita para saber hasta cuándo sirve.");
        }
        if (fecha != null && fecha.isAfter(LocalDate.now(reloj))) {
            throw new ReglaNegocioException("La fecha de expedición no puede ser futura.");
        }
        fila.recibir(documentoId, fecha, usuario.id(), nota);
    }

    private void validarParticipantes(Long tenant, Datos d) {
        propietarios.findByIdAndInmobiliariaId(d.propietarioId(), tenant)
                .orElseThrow(() -> new RecursoNoEncontradoException("Propietario", d.propietarioId()));
        if (d.clienteCompradorId() != null) {
            clientes.findByIdAndInmobiliariaId(d.clienteCompradorId(), tenant)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", d.clienteCompradorId()));
        }
    }
}
