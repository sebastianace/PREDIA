package co.predia.cierre.recordatorios;

import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Recordatorios a los propietarios. La IA los redacta, pero el asesor los revisa y los aprueba: sin la aprobación
 * explícita ({@code POST /aprobar}) no se envía nada.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Recordatorios")
public class RecordatorioController {

    private final ServicioRecordatorios servicio;
    private final RecordatorioRepository recordatorios;
    private final NegocioRepository negocios;
    private final PropietarioRepository propietarios;
    private final InmuebleRepository inmuebles;
    private final UsuarioRepository usuarios;
    private final SesionActual sesion;

    public RecordatorioController(ServicioRecordatorios servicio, RecordatorioRepository recordatorios,
                                  NegocioRepository negocios, PropietarioRepository propietarios,
                                  InmuebleRepository inmuebles, UsuarioRepository usuarios, SesionActual sesion) {
        this.servicio = servicio;
        this.recordatorios = recordatorios;
        this.negocios = negocios;
        this.propietarios = propietarios;
        this.inmuebles = inmuebles;
        this.usuarios = usuarios;
        this.sesion = sesion;
    }

    @GetMapping("/recordatorios")
    @Operation(summary = "Bandeja del asesor: por defecto, los recordatorios SUGERIDOS pendientes de aprobar")
    public List<RecordatorioDto> bandeja(@RequestParam(defaultValue = "SUGERIDO") EstadoRecordatorio estado) {
        return recordatorios.findByInmobiliariaIdAndEstadoOrderByCreadoEnDesc(sesion.inmobiliariaId(), estado)
                .stream().map(r -> dto(r, List.of())).toList();
    }

    @GetMapping("/negocios/{negocioId}/recordatorios")
    @Operation(summary = "Historial de un negocio: qué se pidió, cuándo y quién lo aprobó")
    public List<RecordatorioDto> historial(@PathVariable Long negocioId) {
        buscarNegocio(negocioId);
        return recordatorios.findByNegocioIdAndInmobiliariaIdOrderByCreadoEnDesc(negocioId, sesion.inmobiliariaId())
                .stream().map(r -> dto(r, List.of())).toList();
    }

    @PostMapping("/negocios/{negocioId}/recordatorios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Genera ahora un recordatorio SUGERIDO con lo que falta. No envía nada")
    public RecordatorioDto generar(@PathVariable Long negocioId) {
        Recordatorio r = servicio.generar(buscarNegocio(negocioId));
        return dto(r, servicio.advertenciasDelTexto(r));
    }

    @PutMapping("/recordatorios/{id}")
    @Operation(summary = "Edita el asunto y el texto de un recordatorio sin aprobar. Devuelve advertencias si menciona algo de más")
    public RecordatorioDto editar(@PathVariable Long id, @Valid @RequestBody SolicitudEdicion s) {
        ServicioRecordatorios.Edicion e = servicio.editar(sesion.usuario(), id, s.asunto(), s.cuerpo());
        return dto(e.recordatorio(), e.advertencias());
    }

    @PostMapping("/recordatorios/{id}/aprobar")
    @Operation(summary = "El asesor aprueba el recordatorio y se envía por correo. Es la ÚNICA forma de enviarlo")
    public RecordatorioDto aprobar(@PathVariable Long id) {
        return dto(servicio.aprobar(sesion.usuario(), id), List.of());
    }

    @PostMapping("/recordatorios/{id}/descartar")
    @Operation(summary = "Descarta un recordatorio sin enviarlo")
    public RecordatorioDto descartar(@PathVariable Long id) {
        return dto(servicio.descartar(sesion.usuario(), id), List.of());
    }

    private Negocio buscarNegocio(Long id) {
        return negocios.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", id));
    }

    private RecordatorioDto dto(Recordatorio r, List<String> advertencias) {
        Long tenant = r.getInmobiliariaId();
        Negocio negocio = negocios.findByIdAndInmobiliariaId(r.getNegocioId(), tenant).orElseThrow();
        String inmueble = inmuebles.findByIdAndInmobiliariaId(negocio.getInmuebleId(), tenant)
                .map(Inmueble::getTitulo).orElse(null);
        String propietario = propietarios.findByIdAndInmobiliariaId(negocio.getPropietarioId(), tenant)
                .map(Propietario::getNombre).orElse(null);
        return new RecordatorioDto(r.getId(), r.getNegocioId(), inmueble, propietario, r.getDestinatarioEmail(),
                r.getEstado(), r.getItems(), r.getAsunto(), r.cuerpoVigente(), r.getCuerpoSugerido(),
                r.getOrigenTexto(), r.getCreadoEn(), nombre(r.getEditadoPor()), nombre(r.getAprobadoPor()),
                r.getAprobadoEn(), r.getEnviadoEn(), r.getError(), advertencias);
    }

    private String nombre(Long usuarioId) {
        return usuarioId == null ? null : usuarios.findById(usuarioId).map(Usuario::getNombre).orElse(null);
    }

    public record SolicitudEdicion(@NotBlank @Size(max = 200) String asunto, @NotBlank @Size(max = 3000) String cuerpo) {
    }

    public record RecordatorioDto(Long id, Long negocioId, String inmuebleTitulo, String propietarioNombre,
                                  String destinatarioEmail, EstadoRecordatorio estado, List<ItemRecordatorio> items,
                                  String asunto, String cuerpo, String cuerpoSugerido, OrigenTexto origenTexto,
                                  Instant creadoEn, String editadoPor, String aprobadoPor, Instant aprobadoEn,
                                  Instant enviadoEn, String error, List<String> advertencias) {
    }
}
