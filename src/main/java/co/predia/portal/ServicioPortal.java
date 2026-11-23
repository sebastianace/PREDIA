package co.predia.portal;

import co.predia.cierre.negocio.EstadoNegocio;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.OrigenSugerencia;
import co.predia.cierre.negocio.RequisitoNegocio;
import co.predia.cierre.negocio.RequisitoNegocioRepository;
import co.predia.cierre.recordatorios.EstadoRecordatorio;
import co.predia.cierre.recordatorios.RecordatorioRepository;
import co.predia.cierre.reglas.EstadoDocumental;
import co.predia.cierre.reglas.EstadoRequisito;
import co.predia.cierre.reglas.ServicioEstadoDocumental;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.comun.ConfiguracionReloj;
import co.predia.expedientes.carga.ServicioCargaDocumento;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.foto.FotoInfo;
import co.predia.inventario.foto.FotoInmuebleRepositorio;
import co.predia.inventario.foto.ServicioFotosInmueble;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que ve y hace el propietario en su portal. Todo parte del propietario del token y de su inmobiliaria: un negocio
 * de otro propietario (aunque sea de la misma inmobiliaria) responde 404, igual que uno que no existe. El propietario
 * no ve el comprador, las notas internas ni nada del resto de la inmobiliaria, y lo que envía queda SUGERIDO: solo el
 * asesor lo da por recibido.
 */
@Service
public class ServicioPortal {

    public record NegocioPortal(Long id, Long inmuebleId, String inmueble, String barrio, String ciudad,
                                EstadoNegocio estado, boolean listoParaFirmar, String semaforo, int faltan,
                                int porcentajeAvance, LocalDate fechaFirma, String asesorNombre, String asesorEmail,
                                FotoInfo foto) {
    }

    /** {@code enviadoPorUsted}: lo subió el propietario y está esperando la revisión del asesor. */
    public record DocumentoPortal(Long id, String nombre, String descripcion, boolean obligatorio,
                                  EstadoRequisito estado, String detalle, LocalDate venceEl, boolean enviadoPorUsted,
                                  String documentoNombre, boolean puedeEnviar) {
    }

    public record DetallePortal(NegocioPortal negocio, List<DocumentoPortal> documentos, String aviso) {
    }

    public record RecordatorioPortal(String asunto, String cuerpo, Instant enviadoEn) {
    }

    public static final String AVISO = "Lista de referencia de su inmobiliaria; confirme los requisitos con la notaría. "
            + "Los documentos que envíe quedan en revisión hasta que su asesor los confirme.";

    private final NegocioRepository negocios;
    private final RequisitoNegocioRepository requisitos;
    private final ServicioEstadoDocumental estadoDocumental;
    private final InmuebleRepository inmuebles;
    private final UsuarioRepository usuarios;
    private final RecordatorioRepository recordatorios;
    private final FotoInmuebleRepositorio fotos;
    private final ServicioFotosInmueble servicioFotos;
    private final ServicioCargaDocumento carga;
    private final Clock reloj;

    public ServicioPortal(NegocioRepository negocios, RequisitoNegocioRepository requisitos,
                          ServicioEstadoDocumental estadoDocumental, InmuebleRepository inmuebles,
                          UsuarioRepository usuarios, RecordatorioRepository recordatorios,
                          FotoInmuebleRepositorio fotos, ServicioFotosInmueble servicioFotos,
                          ServicioCargaDocumento carga, Clock reloj) {
        this.negocios = negocios;
        this.requisitos = requisitos;
        this.estadoDocumental = estadoDocumental;
        this.inmuebles = inmuebles;
        this.usuarios = usuarios;
        this.recordatorios = recordatorios;
        this.fotos = fotos;
        this.servicioFotos = servicioFotos;
        this.carga = carga;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<NegocioPortal> negocios(UsuarioActual propietario) {
        exigirPropietario(propietario);
        return negocios.findByInmobiliariaIdAndPropietarioIdOrderByCreadoEnDesc(propietario.inmobiliariaId(),
                        propietario.propietarioId()).stream()
                .filter(n -> n.getEstado() != EstadoNegocio.CANCELADO)
                .map(n -> resumen(n, estadoDocumental.calcular(n))).toList();
    }

    @Transactional(readOnly = true)
    public DetallePortal detalle(UsuarioActual propietario, Long negocioId) {
        Negocio n = negocioDe(propietario, negocioId);
        return detalle(n, estadoDocumental.calcular(n));
    }

    @Transactional
    public DetallePortal enviar(UsuarioActual propietario, Long negocioId, Long requisitoNegocioId,
                                MultipartFile archivo, LocalDate fechaExpedicion) {
        Negocio n = negocioDe(propietario, negocioId);
        if (n.getEstado() != EstadoNegocio.ABIERTO) {
            throw new ReglaNegocioException("Esta venta ya no está abierta: no se pueden enviar documentos.");
        }
        RequisitoNegocio fila = requisitos.findByIdAndNegocioIdAndInmobiliariaId(requisitoNegocioId, n.getId(),
                        propietario.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento", requisitoNegocioId));
        EstadoDocumental.Item item = estadoDocumental.calcular(n).items().stream()
                .filter(i -> i.id().equals(fila.getId())).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento", requisitoNegocioId));
        if (!sePuedeEnviar(item)) {
            throw new ReglaNegocioException("Ese documento ya está recibido y vigente, o no aplica a esta venta.");
        }
        if (fechaExpedicion != null && fechaExpedicion.isAfter(LocalDate.now(reloj.withZone(ConfiguracionReloj.ZONA_COLOMBIA)))) {
            throw new ReglaNegocioException("La fecha de expedición no puede ser posterior a hoy.");
        }
        ServicioCargaDocumento.ResultadoCarga resultado = carga.cargar(archivo, n.getInmuebleId(), n.getId(), propietario);
        fila.sugerirPorPropietario(resultado.documento().id(), fechaExpedicion);
        requisitos.save(fila);
        return detalle(n, estadoDocumental.calcular(n));
    }

    @Transactional(readOnly = true)
    public List<RecordatorioPortal> recordatorios(UsuarioActual propietario, Long negocioId) {
        Negocio n = negocioDe(propietario, negocioId);
        return recordatorios.findByNegocioIdAndInmobiliariaIdOrderByCreadoEnDesc(n.getId(), propietario.inmobiliariaId())
                .stream().filter(r -> r.getEstado() == EstadoRecordatorio.ENVIADO)
                .map(r -> new RecordatorioPortal(r.getAsunto(), r.cuerpoVigente(), r.getEnviadoEn())).toList();
    }

    @Transactional(readOnly = true)
    public FotoInmuebleRepositorio.Bytes foto(UsuarioActual propietario, Long negocioId, boolean miniatura) {
        Negocio n = negocioDe(propietario, negocioId);
        return servicioFotos.leer(propietario.inmobiliariaId(), n.getInmuebleId(), miniatura);
    }

    private DetallePortal detalle(Negocio n, EstadoDocumental estado) {
        boolean abierto = n.getEstado() == EstadoNegocio.ABIERTO;
        List<DocumentoPortal> documentos = estado.items().stream()
                .filter(i -> i.estado() != EstadoRequisito.NO_APLICA)
                .map(i -> new DocumentoPortal(i.id(), i.nombre(), i.descripcion(), i.obligatorio(), i.estado(),
                        i.detalle(), i.venceEl(), i.sugeridoPor() == OrigenSugerencia.PROPIETARIO,
                        i.documentoNombre(), abierto && sePuedeEnviar(i)))
                .toList();
        return new DetallePortal(resumen(n, estado), documentos, AVISO);
    }

    private NegocioPortal resumen(Negocio n, EstadoDocumental estado) {
        Inmueble i = inmuebles.findByIdAndInmobiliariaId(n.getInmuebleId(), n.getInmobiliariaId()).orElseThrow();
        var asesor = usuarios.findByIdAndInmobiliariaId(n.getAsesorId(), n.getInmobiliariaId());
        return new NegocioPortal(n.getId(), i.getId(), i.getTitulo(), i.getBarrio(), i.getCiudad(), n.getEstado(),
                estado.listoParaFirmar(), estado.semaforo(), estado.faltan(), estado.porcentajeAvance(),
                n.getFechaFirmaTentativa(), asesor.map(u -> u.getNombre()).orElse(null),
                asesor.map(u -> u.getEmail()).orElse(null), fotos.info(n.getInmobiliariaId(), i.getId()).orElse(null));
    }

    /** Lo que falta, lo vencido, lo que está por vencer o lo que él mismo envió y todavía se revisa. */
    private static boolean sePuedeEnviar(EstadoDocumental.Item i) {
        return i.estado() == EstadoRequisito.PENDIENTE || i.estado() == EstadoRequisito.VENCIDO
                || i.estado() == EstadoRequisito.POR_VENCER || i.estado() == EstadoRequisito.SUGERIDO;
    }

    private Negocio negocioDe(UsuarioActual propietario, Long negocioId) {
        exigirPropietario(propietario);
        return negocios.findByIdAndInmobiliariaId(negocioId, propietario.inmobiliariaId())
                .filter(n -> propietario.propietarioId().equals(n.getPropietarioId()))
                .filter(n -> n.getEstado() != EstadoNegocio.CANCELADO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", negocioId));
    }

    private static void exigirPropietario(UsuarioActual u) {
        if (u.propietarioId() == null) {
            throw new RecursoNoEncontradoException("No hay un propietario asociado a esta sesión.");
        }
    }
}
