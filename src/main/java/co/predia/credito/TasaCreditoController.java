package co.predia.credito;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Tasas de crédito por banco. Todos las consultan; solo el ADMIN las carga (ver ConfiguracionSeguridad). */
@RestController
@RequestMapping("/api/tasas")
@Tag(name = "Crédito")
public class TasaCreditoController {

    private final TasaCreditoRepository tasas;
    private final SesionActual sesion;

    public TasaCreditoController(TasaCreditoRepository tasas, SesionActual sesion) {
        this.tasas = tasas;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Lista las tasas de crédito de la inmobiliaria")
    public List<TasaCreditoDto> listar() {
        return tasas.findByInmobiliariaIdOrderByBancoAscVigenteDesdeDesc(sesion.inmobiliariaId()).stream()
                .map(TasaCreditoDto::desde).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Carga una tasa (solo ADMIN)")
    public TasaCreditoDto crear(@Valid @RequestBody SolicitudTasa s) {
        TasaCredito tasa = new TasaCredito(sesion.inmobiliariaId(), s.banco().strip(), s.tipo(), s.tasaEa(),
                s.vigenteDesde(), s.fuente().strip(), s.esEjemplo(), sesion.usuario().id());
        return TasaCreditoDto.desde(tasas.save(tasa));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrige una tasa (solo ADMIN)")
    public TasaCreditoDto actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudTasa s) {
        TasaCredito tasa = tasas.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tasa", id));
        tasa.actualizar(s.banco().strip(), s.tipo(), s.tasaEa(), s.vigenteDesde(), s.fuente().strip(), s.esEjemplo());
        return TasaCreditoDto.desde(tasas.save(tasa));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una tasa (solo ADMIN)")
    public void eliminar(@PathVariable Long id) {
        TasaCredito tasa = tasas.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tasa", id));
        tasas.delete(tasa);
    }

    /** {@code tasaEa} es una fracción: 0.12 equivale a 12 % efectivo anual. */
    public record SolicitudTasa(
            @NotBlank @Size(max = 100) String banco,
            @NotNull TipoTasa tipo,
            @NotNull @DecimalMin("0.0001") @DecimalMax("0.9999") BigDecimal tasaEa,
            @NotNull LocalDate vigenteDesde,
            @NotBlank @Size(max = 200) String fuente,
            boolean esEjemplo) {
    }
}
