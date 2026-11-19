package co.predia.inventario;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consulta del inventario (Fase 1: solo lectura; la creación, edición y búsqueda llegan en la Fase 4). */
@RestController
@RequestMapping("/api/inmuebles")
@Tag(name = "Inventario")
public class InmuebleController {

    private final InmuebleRepository repositorio;
    private final SesionActual sesion;

    public InmuebleController(InmuebleRepository repositorio, SesionActual sesion) {
        this.repositorio = repositorio;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Lista los inmuebles de la inmobiliaria, opcionalmente por estado")
    public List<InmuebleDto> listar(@RequestParam(required = false) EstadoInmueble estado) {
        Long tenant = sesion.inmobiliariaId();
        List<Inmueble> inmuebles = estado == null
                ? repositorio.findByInmobiliariaIdOrderByTituloAsc(tenant)
                : repositorio.findByInmobiliariaIdAndEstadoOrderByTituloAsc(tenant, estado);
        return inmuebles.stream().map(InmuebleDto::desde).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un inmueble")
    public InmuebleDto detalle(@PathVariable Long id) {
        return repositorio.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .map(InmuebleDto::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", id));
    }
}
