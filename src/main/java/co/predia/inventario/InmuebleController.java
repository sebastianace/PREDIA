package co.predia.inventario;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.inventario.foto.FotoInmuebleRepositorio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.math.BigDecimal;

import java.util.List;

/** Inventario de inmuebles: consulta, alta y edición, y búsqueda en dos pasos (SQL filtra, pgvector ordena). */
@RestController
@RequestMapping("/api/inmuebles")
@Tag(name = "Inventario")
public class InmuebleController {

    private final InmuebleRepository repositorio;
    private final InmuebleService servicio;
    private final BusquedaInmuebles busqueda;
    private final SesionActual sesion;
    private final FotoInmuebleRepositorio fotos;

    public InmuebleController(InmuebleRepository repositorio, InmuebleService servicio, BusquedaInmuebles busqueda,
                              SesionActual sesion, FotoInmuebleRepositorio fotos) {
        this.fotos = fotos;
        this.repositorio = repositorio;
        this.servicio = servicio;
        this.busqueda = busqueda;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Lista los inmuebles de la inmobiliaria, opcionalmente por estado")
    public List<InmuebleDto> listar(@RequestParam(required = false) EstadoInmueble estado) {
        Long tenant = sesion.inmobiliariaId();
        List<Inmueble> inmuebles = estado == null
                ? repositorio.findByInmobiliariaIdOrderByTituloAsc(tenant)
                : repositorio.findByInmobiliariaIdAndEstadoOrderByTituloAsc(tenant, estado);
        var info = fotos.infoDe(tenant);
        return inmuebles.stream().map((i) -> InmuebleDto.desde(i).conFoto(info.get(i.getId()))).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un inmueble")
    public InmuebleDto detalle(@PathVariable Long id) {
        Long tenant = sesion.inmobiliariaId();
        return repositorio.findByIdAndInmobiliariaId(id, tenant)
                .map((i) -> InmuebleDto.desde(i).conFoto(fotos.info(tenant, i.getId()).orElse(null)))
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un inmueble")
    public InmuebleDto crear(@Valid @RequestBody SolicitudInmueble solicitud) {
        return InmuebleDto.desde(servicio.crear(sesion.inmobiliariaId(), solicitud));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita un inmueble (incluido su estado)")
    public InmuebleDto actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudInmueble solicitud) {
        Long tenant = sesion.inmobiliariaId();
        return InmuebleDto.desde(servicio.actualizar(tenant, id, solicitud)).conFoto(fotos.info(tenant, id).orElse(null));
    }

    @PostMapping("/busqueda")
    @Operation(summary = "Busca inmuebles DISPONIBLES: SQL filtra lo no negociable y pgvector ordena por similitud")
    public BusquedaInmuebles.Respuesta buscar(@Valid @RequestBody SolicitudBusqueda s) {
        return busqueda.buscar(sesion.inmobiliariaId(), new BusquedaInmuebles.Solicitud(s.operacion(),
                s.precioMax(), s.habitacionesMin(), s.ciudad(), s.descripcion()));
    }

    public record SolicitudBusqueda(Operacion operacion, @DecimalMin("0") BigDecimal precioMax,
                                    @Min(0) @Max(30) Integer habitacionesMin, @Size(max = 100) String ciudad,
                                    @Size(max = 1000) String descripcion) {
    }
}
