package co.predia.cierre.recordatorios;

import co.predia.cierre.catalogo.CatalogoService;
import co.predia.cierre.catalogo.RequisitoCatalogo;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.cierre.reglas.EstadoDocumental;
import co.predia.cierre.reglas.ServicioEstadoDocumental;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Genera, edita, aprueba y envía los recordatorios a los propietarios. La única puerta de salida de un correo es
 * {@link #aprobar}: sin la aprobación explícita del asesor no se envía nada (ni siquiera desde el trabajo diario).
 */
@Service
public class ServicioRecordatorios {

    private static final Logger log = LoggerFactory.getLogger(ServicioRecordatorios.class);
    private static final String REMITENTE_POR_DEFECTO = "notificaciones@predia.local";

    private final RecordatorioRepository recordatorios;
    private final NegocioRepository negocios;
    private final PropietarioRepository propietarios;
    private final InmuebleRepository inmuebles;
    private final InmobiliariaRepository inmobiliarias;
    private final UsuarioRepository usuarios;
    private final ParametrosInmobiliariaRepository parametros;
    private final CatalogoService catalogoService;
    private final ServicioEstadoDocumental estadoDocumental;
    private final RedactorRecordatorio redactor;
    private final JavaMailSender correo;
    private final Clock reloj;

    public ServicioRecordatorios(RecordatorioRepository recordatorios, NegocioRepository negocios,
                                 PropietarioRepository propietarios, InmuebleRepository inmuebles,
                                 InmobiliariaRepository inmobiliarias, UsuarioRepository usuarios,
                                 ParametrosInmobiliariaRepository parametros, CatalogoService catalogoService,
                                 ServicioEstadoDocumental estadoDocumental, RedactorRecordatorio redactor,
                                 JavaMailSender correo, Clock reloj) {
        this.recordatorios = recordatorios;
        this.negocios = negocios;
        this.propietarios = propietarios;
        this.inmuebles = inmuebles;
        this.inmobiliarias = inmobiliarias;
        this.usuarios = usuarios;
        this.parametros = parametros;
        this.catalogoService = catalogoService;
        this.estadoDocumental = estadoDocumental;
        this.redactor = redactor;
        this.correo = correo;
        this.reloj = reloj;
    }

    /** Crea un recordatorio SUGERIDO con lo que falta o está por vencer. No envía nada. */
    public Recordatorio generar(Negocio negocio) {
        return generar(negocio, LocalDate.now(reloj));
    }

    public Recordatorio generar(Negocio negocio, LocalDate hoy) {
        Long tenant = negocio.getInmobiliariaId();
        EstadoDocumental estado = estadoDocumental.calcular(negocio, hoy);
        List<EstadoDocumental.Item> faltantes = estado.faltantesParaElPropietario();
        if (faltantes.isEmpty()) {
            throw new ReglaNegocioException("No hay documentos pendientes por pedirle al vendedor.");
        }

        List<ItemRecordatorio> items = faltantes.stream().map(i -> new ItemRecordatorio(i.codigo(), i.nombre(),
                i.estado().name(), i.detalle(), i.venceEl() == null ? null : i.venceEl().toString())).toList();
        Propietario propietario = propietarios.findByIdAndInmobiliariaId(negocio.getPropietarioId(), tenant)
                .orElseThrow();
        Inmueble inmueble = inmuebles.findByIdAndInmobiliariaId(negocio.getInmuebleId(), tenant).orElseThrow();
        String asesor = usuarios.findById(negocio.getAsesorId()).map(Usuario::getNombre).orElse("Tu asesor");
        String inmobiliaria = inmobiliarias.findById(tenant).map(i -> i.getNombre()).orElse("la inmobiliaria");

        var contexto = contextoDeValidacion(tenant, items);
        var borrador = redactor.redactar(new RedactorRecordatorio.Entrada(propietario.getNombre(),
                inmueble.getTitulo(), asesor, inmobiliaria, items, contexto.pedidos(), contexto.noPedidos()));

        String asunto = borrador.asunto().length() > 200 ? borrador.asunto().substring(0, 200) : borrador.asunto();
        return recordatorios.save(new Recordatorio(tenant, negocio.getId(), propietario.getEmail(), items, asunto,
                borrador.cuerpo(), borrador.origen()));
    }

    /** El asesor edita el borrador. El texto se valida otra vez: si menciona algo de más, se le avisa, sin bloquearlo. */
    public record Edicion(Recordatorio recordatorio, List<String> advertencias) {
    }

    public Edicion editar(UsuarioActual usuario, Long id, String asunto, String cuerpo) {
        Recordatorio r = buscar(usuario.inmobiliariaId(), id);
        if (r.getEstado() != EstadoRecordatorio.SUGERIDO) {
            throw new ReglaNegocioException("Solo se puede editar un recordatorio que todavía no se ha aprobado.");
        }
        r.editar(asunto.strip(), cuerpo.strip(), usuario.id());
        r = recordatorios.save(r);
        return new Edicion(r, advertenciasDelTexto(r));
    }

    /** La única puerta de salida de un correo: el asesor aprueba y recién ahí se envía. */
    public Recordatorio aprobar(UsuarioActual usuario, Long id) {
        Recordatorio r = buscar(usuario.inmobiliariaId(), id);
        if (r.getEstado() != EstadoRecordatorio.SUGERIDO && r.getEstado() != EstadoRecordatorio.ERROR_ENVIO) {
            throw new ReglaNegocioException("Este recordatorio ya no está pendiente de aprobación.");
        }
        Long negocioId = r.getNegocioId();
        Negocio negocio = negocios.findByIdAndInmobiliariaId(negocioId, usuario.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", negocioId));
        Propietario propietario = propietarios.findByIdAndInmobiliariaId(negocio.getPropietarioId(),
                usuario.inmobiliariaId()).orElseThrow();
        if (propietario.getEmail() == null) {
            throw new ReglaNegocioException("El vendedor no tiene correo registrado: agrégalo antes de enviar.");
        }

        r.aprobar(usuario.id(), propietario.getEmail());
        r = recordatorios.save(r);

        try {
            correo.send(mensaje(usuario, negocio, propietario, r));
            r.marcarEnviado();
        } catch (Exception e) {
            // No se registra el correo del destinatario: es un dato personal.
            log.warn("No se pudo enviar el recordatorio {}: {}", r.getId(), e.getClass().getSimpleName());
            r.marcarErrorEnvio("No se pudo enviar el correo. Revisa la configuración del correo e intenta de nuevo.");
        }
        return recordatorios.save(r);
    }

    public Recordatorio descartar(UsuarioActual usuario, Long id) {
        Recordatorio r = buscar(usuario.inmobiliariaId(), id);
        if (r.getEstado() != EstadoRecordatorio.SUGERIDO) {
            throw new ReglaNegocioException("Solo se puede descartar un recordatorio que todavía no se ha aprobado.");
        }
        r.descartar();
        return recordatorios.save(r);
    }

    public Recordatorio buscar(Long tenant, Long id) {
        return recordatorios.findByIdAndInmobiliariaId(id, tenant)
                .orElseThrow(() -> new RecursoNoEncontradoException("Recordatorio", id));
    }

    /** Las advertencias de un texto ya redactado, contra lo que de verdad faltaba cuando se generó. */
    public List<String> advertenciasDelTexto(Recordatorio r) {
        Negocio negocio = negocios.findByIdAndInmobiliariaId(r.getNegocioId(), r.getInmobiliariaId()).orElse(null);
        String inmueble = negocio == null ? "" : inmuebles.findByIdAndInmobiliariaId(negocio.getInmuebleId(),
                r.getInmobiliariaId()).map(Inmueble::getTitulo).orElse("");
        var contexto = contextoDeValidacion(r.getInmobiliariaId(), r.getItems());
        List<String> frases = new ArrayList<>();
        r.getItems().forEach(i -> {
            frases.add(i.nombre());
            frases.add(i.detalle());
        });
        frases.add(inmueble);
        String texto = r.getAsunto() + "\n" + r.cuerpoVigente();
        return ValidadorTextoRecordatorio.validar(texto, contexto.pedidos(), contexto.noPedidos(),
                ValidadorTextoRecordatorio.numerosDe(frases)).problemas();
    }

    private record ContextoValidacion(List<ValidadorTextoRecordatorio.Documento> pedidos,
                                      List<ValidadorTextoRecordatorio.NoPedido> noPedidos) {
    }

    private ContextoValidacion contextoDeValidacion(Long tenant, List<ItemRecordatorio> items) {
        Set<String> codigos = items.stream().map(ItemRecordatorio::codigo).collect(java.util.stream.Collectors.toSet());
        List<ValidadorTextoRecordatorio.Documento> pedidos = new ArrayList<>();
        List<ValidadorTextoRecordatorio.NoPedido> noPedidos = new ArrayList<>();
        for (RequisitoCatalogo r : catalogoService.asegurar(tenant)) {
            if (codigos.contains(r.getCodigo())) {
                pedidos.add(new ValidadorTextoRecordatorio.Documento(r.getNombre(), r.getPalabrasClave()));
            } else {
                noPedidos.add(new ValidadorTextoRecordatorio.NoPedido(r.getNombre(), r.getPalabrasClave()));
            }
        }
        return new ContextoValidacion(pedidos, noPedidos);
    }

    private SimpleMailMessage mensaje(UsuarioActual asesor, Negocio negocio, Propietario propietario, Recordatorio r) {
        ParametrosInmobiliaria params = parametros.findById(negocio.getInmobiliariaId())
                .orElseGet(() -> new ParametrosInmobiliaria(negocio.getInmobiliariaId()));
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(params.getEmailRemitente() != null ? params.getEmailRemitente() : REMITENTE_POR_DEFECTO);
        m.setReplyTo(asesor.email());
        m.setTo(propietario.getEmail());
        m.setSubject(r.getAsunto());
        m.setText(r.cuerpoVigente());
        return m;
    }
}
