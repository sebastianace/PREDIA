package co.predia.cierre.reglas;

import co.predia.cierre.catalogo.CatalogoService;
import co.predia.cierre.catalogo.RequisitoCatalogo;
import co.predia.cierre.negocio.HipotecaVigente;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.cierre.negocio.RequisitoNegocio;
import co.predia.cierre.negocio.RequisitoNegocioRepository;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.cierre.reglas.MotorReglasCierre.Contexto;
import co.predia.cierre.reglas.MotorReglasCierre.Evaluacion;
import co.predia.cierre.reglas.MotorReglasCierre.Registro;
import co.predia.cierre.reglas.MotorReglasCierre.Requisito;
import co.predia.expedientes.Documento;
import co.predia.expedientes.DocumentoRepository;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Junta los datos del negocio y se los pasa al MotorReglasCierre. Si el catálogo ganó un requisito activo que el
 * negocio todavía no tiene, lo crea como PENDIENTE (por eso no es de solo lectura).
 */
@Service
public class ServicioEstadoDocumental {

    private final CatalogoService catalogoService;
    private final RequisitoNegocioRepository requisitosNegocio;
    private final InmuebleRepository inmuebles;
    private final PropietarioRepository propietarios;
    private final ParametrosInmobiliariaRepository parametros;
    private final DocumentoRepository documentos;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    public ServicioEstadoDocumental(CatalogoService catalogoService, RequisitoNegocioRepository requisitosNegocio,
                                    InmuebleRepository inmuebles, PropietarioRepository propietarios,
                                    ParametrosInmobiliariaRepository parametros, DocumentoRepository documentos,
                                    UsuarioRepository usuarios, Clock reloj) {
        this.catalogoService = catalogoService;
        this.requisitosNegocio = requisitosNegocio;
        this.inmuebles = inmuebles;
        this.propietarios = propietarios;
        this.parametros = parametros;
        this.documentos = documentos;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Transactional
    public EstadoDocumental calcular(Negocio negocio) {
        return calcular(negocio, LocalDate.now(reloj));
    }

    @Transactional
    public EstadoDocumental calcular(Negocio negocio, LocalDate hoy) {
        Long tenant = negocio.getInmobiliariaId();
        List<RequisitoCatalogo> catalogo = catalogoService.asegurar(tenant).stream()
                .filter(RequisitoCatalogo::isActivo).toList();

        Map<Long, RequisitoNegocio> filas = new HashMap<>();
        requisitosNegocio.findByNegocioId(negocio.getId()).forEach(f -> filas.put(f.getRequisitoId(), f));
        for (RequisitoCatalogo r : catalogo) {
            filas.computeIfAbsent(r.getId(),
                    id -> requisitosNegocio.save(new RequisitoNegocio(tenant, negocio.getId(), id)));
        }

        Inmueble inmueble = inmuebles.findByIdAndInmobiliariaId(negocio.getInmuebleId(), tenant).orElseThrow();
        Propietario propietario = propietarios.findByIdAndInmobiliariaId(negocio.getPropietarioId(), tenant)
                .orElseThrow();
        ParametrosInmobiliaria params = parametros.findById(tenant).orElseGet(() -> new ParametrosInmobiliaria(tenant));
        Contexto contexto = new Contexto(inmueble.isPropiedadHorizontal(),
                ciudadConValorizacion(params, inmueble.getCiudad()), propietario.getTipoPersona() == TipoPersona.JURIDICA,
                propietario.getEstadoCivil(), negocio.isActuaPorApoderado(), negocio.getHipotecaVigente(),
                negocio.getFechaFirmaTentativa(), params.getDiasAlertaVencimiento(), hoy);

        Map<Long, String> nombresDocumento = nombresDeDocumentos(tenant, filas.values().stream()
                .map(RequisitoNegocio::getDocumentoId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> nombresUsuario = nombresDeUsuarios(filas.values().stream()
                .map(RequisitoNegocio::getRecibidoPor).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));

        List<EstadoDocumental.Item> items = new ArrayList<>();
        List<Evaluacion> evaluaciones = new ArrayList<>();
        for (RequisitoCatalogo r : catalogo) {
            RequisitoNegocio fila = filas.get(r.getId());
            Evaluacion e = MotorReglasCierre.evaluar(
                    new Requisito(r.getReglaAplica(), r.isObligatorio(), r.getReglaVigencia(), r.getVigenciaDias()),
                    new Registro(fila.getEstadoRegistrado(), fila.getFechaExpedicion()), contexto);
            evaluaciones.add(e);
            items.add(new EstadoDocumental.Item(fila.getId(), r.getId(), r.getCodigo(), r.getNombre(),
                    r.getDescripcion(), r.isObligatorio(), e.estado(),
                    DescriptorItem.detalle(e.estado(), e.venceEl(), r.getReglaVigencia(), r.getVigenciaDias(),
                            negocio.getFechaFirmaTentativa()),
                    e.venceEl(), fila.getEstadoRegistrado(), fila.getFechaExpedicion(), fila.getDocumentoId(),
                    buscar(nombresDocumento, fila.getDocumentoId()), buscar(nombresUsuario, fila.getRecibidoPor()),
                    fila.getRecibidoEn(), fila.getNota(),
                    r.getReglaVigencia() != co.predia.cierre.catalogo.ReglaVigencia.NINGUNA, r.getPalabrasClave(),
                    fila.getEstadoRegistrado() == co.predia.cierre.negocio.EstadoRegistrado.SUGERIDO
                            ? fila.getSugeridoPor() : null));
        }

        var semaforo = MotorReglasCierre.semaforo(evaluaciones);
        return new EstadoDocumental(negocio.getId(), semaforo.listoParaFirmar(), DescriptorItem.textoSemaforo(semaforo),
                semaforo.faltan(), semaforo.obligatoriosQueAplican(), semaforo.recibidos(), semaforo.porcentajeAvance(),
                items, advertencias(negocio, propietario), EstadoDocumental.AVISO);
    }

    /** Los mapas inmutables (Map.of) lanzan NPE con una clave nula: un requisito sin documento tiene id nulo. */
    private static String buscar(Map<Long, String> nombres, Long id) {
        return id == null ? null : nombres.get(id);
    }

    private static boolean ciudadConValorizacion(ParametrosInmobiliaria params, String ciudad) {
        return params.getCiudadesConValorizacion().stream().anyMatch(c -> c.equalsIgnoreCase(ciudad));
    }

    private static List<String> advertencias(Negocio negocio, Propietario propietario) {
        List<String> a = new ArrayList<>();
        if (negocio.getHipotecaVigente() == HipotecaVigente.DESCONOCIDO) {
            a.add("Confirma si el inmueble tiene una hipoteca vigente (se ve en el certificado de tradición y "
                    + "libertad); si la tiene, se exige la carta del banco.");
        }
        if (negocio.getFechaFirmaTentativa() == null) {
            a.add("Define la fecha tentativa de firma para saber qué documentos vencen antes de firmar.");
        }
        if (propietario.getEmail() == null) {
            a.add("El vendedor no tiene correo registrado: no se le podrán enviar recordatorios.");
        }
        return a;
    }

    private Map<Long, String> nombresDeDocumentos(Long tenant, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return documentos.findAllById(ids).stream().filter(d -> d.getInmobiliariaId().equals(tenant))
                .collect(Collectors.toMap(Documento::getId, Documento::getNombreArchivo));
    }

    private Map<Long, String> nombresDeUsuarios(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return usuarios.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
    }
}
