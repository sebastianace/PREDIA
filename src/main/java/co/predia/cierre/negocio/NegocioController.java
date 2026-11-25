package co.predia.cierre.negocio;

import co.predia.cierre.reglas.EstadoDocumental;
import co.predia.perfil.FotoPerfilRepositorio;
import co.predia.perfil.SujetoFoto;
import co.predia.cierre.reglas.ServicioEstadoDocumental;
import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

import java.time.LocalDate;
import java.util.List;

/** Negocios de venta y el estado de los documentos del vendedor. Siempre dentro de la inmobiliaria del usuario. */
@RestController
@RequestMapping("/api/negocios")
@Tag(name = "Cierre del negocio")
public class NegocioController {

    private final NegocioRepository negocios;
    private final NegocioService servicio;
    private final ServicioEstadoDocumental estadoDocumental;
    private final InmuebleRepository inmuebles;
    private final PropietarioRepository propietarios;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final SesionActual sesion;
    private final FotoPerfilRepositorio fotos;

    public NegocioController(NegocioRepository negocios, NegocioService servicio,
                             ServicioEstadoDocumental estadoDocumental, InmuebleRepository inmuebles,
                             PropietarioRepository propietarios, ClienteRepository clientes,
                             UsuarioRepository usuarios, SesionActual sesion, FotoPerfilRepositorio fotos) {
        this.fotos = fotos;
        this.negocios = negocios;
        this.servicio = servicio;
        this.estadoDocumental = estadoDocumental;
        this.inmuebles = inmuebles;
        this.propietarios = propietarios;
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Lista los negocios con su semáforo: 'Listo para firmar' o 'Faltan X documentos'")
    public List<NegocioDto> listar(@RequestParam(required = false) EstadoNegocio estado) {
        Long tenant = sesion.inmobiliariaId();
        List<Negocio> lista = estado == null ? negocios.findByInmobiliariaIdOrderByCreadoEnDesc(tenant)
                : negocios.findByInmobiliariaIdAndEstadoOrderByCreadoEnDesc(tenant, estado);
        return lista.stream().map(this::dto).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un negocio")
    public NegocioDto detalle(@PathVariable Long id) {
        return dto(buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre un negocio: une el inmueble, el vendedor, el comprador y la fecha tentativa de firma")
    public NegocioDto crear(@Valid @RequestBody SolicitudNegocio s) {
        Negocio negocio = servicio.crear(sesion.usuario(), s.aDatos());
        return dto(negocio);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza el negocio (fecha de firma, apoderado, hipoteca, estado)")
    public NegocioDto actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudNegocio s) {
        return dto(servicio.actualizar(sesion.inmobiliariaId(), id, s.aDatos()));
    }

    @GetMapping("/{id}/estado-documental")
    @Operation(summary = "Qué le falta al vendedor: estado de cada requisito, semáforo y porcentaje de avance")
    public EstadoDocumental estadoDocumental(@PathVariable Long id) {
        return estadoDocumental.calcular(buscar(id));
    }

    @PutMapping("/{id}/requisitos/{requisitoNegocioId}")
    @Operation(summary = "El asesor da un requisito por recibido, lo marca como no aplica o lo reabre. "
            + "Solo él puede pasarlo a RECIBIDO: la IA solo sugiere")
    public EstadoDocumental accion(@PathVariable Long id, @PathVariable Long requisitoNegocioId,
                                   @Valid @RequestBody SolicitudAccion s) {
        servicio.aplicarAccion(sesion.usuario(), id, requisitoNegocioId, s.accion(), s.fechaExpedicion(),
                s.documentoId(), s.nota());
        return estadoDocumental.calcular(buscar(id));
    }

    private Negocio buscar(Long id) {
        return negocios.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", id));
    }

    private NegocioDto dto(Negocio n) {
        Inmueble inmueble = inmuebles.findByIdAndInmobiliariaId(n.getInmuebleId(), n.getInmobiliariaId()).orElseThrow();
        Propietario propietario = propietarios.findByIdAndInmobiliariaId(n.getPropietarioId(), n.getInmobiliariaId())
                .orElseThrow();
        String comprador = n.getClienteCompradorId() == null ? null
                : clientes.findByIdAndInmobiliariaId(n.getClienteCompradorId(), n.getInmobiliariaId())
                        .map(Cliente::getNombre).orElse(null);
        String asesor = usuarios.findById(n.getAsesorId()).map(Usuario::getNombre).orElse(null);
        EstadoDocumental ed = estadoDocumental.calcular(n);
        return new NegocioDto(n.getId(), n.getInmuebleId(), inmueble.getTitulo(), n.getPropietarioId(),
                propietario.getNombre(), n.getClienteCompradorId(), comprador, n.getAsesorId(), asesor,
                n.getFechaFirmaTentativa(), n.isActuaPorApoderado(), n.getHipotecaVigente(), n.getEstado(),
                ed.listoParaFirmar(), ed.semaforo(), ed.faltan(), ed.porcentajeAvance(),
                fotos.version(n.getInmobiliariaId(), SujetoFoto.PROPIETARIO, n.getPropietarioId()).orElse(null),
                n.getClienteCompradorId() == null ? null
                        : fotos.version(n.getInmobiliariaId(), SujetoFoto.CLIENTE, n.getClienteCompradorId()).orElse(null),
                fotos.version(n.getInmobiliariaId(), SujetoFoto.USUARIO, n.getAsesorId()).orElse(null));
    }

    public record SolicitudNegocio(Long inmuebleId, @NotNull Long propietarioId, Long clienteCompradorId,
                                   LocalDate fechaFirmaTentativa, boolean actuaPorApoderado,
                                   HipotecaVigente hipotecaVigente, EstadoNegocio estado) {
        NegocioService.Datos aDatos() {
            return new NegocioService.Datos(inmuebleId, propietarioId, clienteCompradorId, fechaFirmaTentativa,
                    actuaPorApoderado, hipotecaVigente, estado);
        }
    }

    public record SolicitudAccion(@NotNull AccionRequisito accion, LocalDate fechaExpedicion, Long documentoId,
                                  @Size(max = 500) String nota) {
    }

    public record NegocioDto(Long id, Long inmuebleId, String inmuebleTitulo, Long propietarioId,
                             String propietarioNombre, Long clienteCompradorId, String clienteCompradorNombre,
                             Long asesorId, String asesorNombre, LocalDate fechaFirmaTentativa,
                             boolean actuaPorApoderado, HipotecaVigente hipotecaVigente, EstadoNegocio estado,
                             boolean listoParaFirmar, String semaforo, int faltan, int porcentajeAvance,
                             String propietarioFotoVersion, String clienteCompradorFotoVersion, String asesorFotoVersion) {
    }
}
