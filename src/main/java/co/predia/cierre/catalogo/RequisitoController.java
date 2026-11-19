package co.predia.cierre.catalogo;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catálogo de requisitos de la inmobiliaria. Todos lo consultan; solo el ADMIN lo ajusta (ver ConfiguracionSeguridad).
 * Lista de referencia: Predia no da asesoría legal; los requisitos se confirman con la notaría.
 */
@RestController
@RequestMapping("/api/requisitos")
@Tag(name = "Cierre del negocio")
public class RequisitoController {

    public static final String AVISO = "Lista de referencia; confirma los requisitos con la notaría.";

    private final CatalogoService catalogoService;
    private final RequisitoCatalogoRepository catalogo;
    private final SesionActual sesion;

    public RequisitoController(CatalogoService catalogoService, RequisitoCatalogoRepository catalogo,
                               SesionActual sesion) {
        this.catalogoService = catalogoService;
        this.catalogo = catalogo;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Catálogo de requisitos de la inmobiliaria (se siembra con la lista base la primera vez)")
    public List<RequisitoDto> listar() {
        return catalogoService.asegurar(sesion.inmobiliariaId()).stream().map(RequisitoDto::desde).toList();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Ajusta un requisito: nombre, orden, si está activo, si es obligatorio y su vigencia (solo ADMIN)")
    public RequisitoDto actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudRequisito s) {
        RequisitoCatalogo requisito = catalogo.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Requisito", id));
        if (s.reglaVigencia() == ReglaVigencia.DIAS_DESDE_EXPEDICION && s.vigenciaDias() == null) {
            throw new ReglaNegocioException("Indica cuántos días de vigencia tiene el documento.");
        }
        requisito.actualizar(s.nombre().strip(), s.descripcion(), s.orden(), s.activo(), s.reglaAplica(),
                s.obligatorio(), s.reglaVigencia(), s.vigenciaDias());
        return RequisitoDto.desde(catalogo.save(requisito));
    }

    public record SolicitudRequisito(@NotBlank @Size(max = 200) String nombre, @Size(max = 1000) String descripcion,
                                     @Min(0) @Max(1000) int orden, boolean activo, @NotNull ReglaAplica reglaAplica,
                                     boolean obligatorio, @NotNull ReglaVigencia reglaVigencia,
                                     @Min(1) @Max(3650) Integer vigenciaDias) {
    }

    public record RequisitoDto(Long id, String codigo, String nombre, String descripcion, int orden, boolean activo,
                               ReglaAplica reglaAplica, boolean obligatorio, ReglaVigencia reglaVigencia,
                               Integer vigenciaDias, String aviso) {
        static RequisitoDto desde(RequisitoCatalogo r) {
            return new RequisitoDto(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(), r.getOrden(),
                    r.isActivo(), r.getReglaAplica(), r.isObligatorio(), r.getReglaVigencia(), r.getVigenciaDias(),
                    AVISO);
        }
    }
}
