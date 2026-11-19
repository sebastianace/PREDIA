package co.predia.inmobiliaria;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Parámetros de las reglas de negocio de la inmobiliaria del usuario. Solo el ADMIN los modifica. */
@RestController
@RequestMapping("/api/parametros")
@Tag(name = "Parámetros de la inmobiliaria")
public class ParametrosController {

    private final ParametrosInmobiliariaRepository repositorio;
    private final SesionActual sesion;

    public ParametrosController(ParametrosInmobiliariaRepository repositorio, SesionActual sesion) {
        this.repositorio = repositorio;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Consulta los parámetros de la inmobiliaria")
    public Parametros consultar() {
        return Parametros.desde(obtener(sesion.inmobiliariaId()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Actualiza los parámetros (solo ADMIN)")
    public Parametros actualizar(@Valid @RequestBody Parametros p) {
        ParametrosInmobiliaria actual = obtener(sesion.inmobiliariaId());
        actual.actualizar(p.pctCuotaMaxIngreso(), p.pctCuotaInicial(), p.pctCuotaInicialVis(), p.plazoDefaultAnios(),
                p.diasAlertaVencimiento(), p.ciudadesConValorizacion() == null ? List.of() : p.ciudadesConValorizacion(),
                p.umbralSimilitudRag(), p.emailRemitente());
        return Parametros.desde(repositorio.save(actual));
    }

    private ParametrosInmobiliaria obtener(Long inmobiliariaId) {
        return repositorio.findById(inmobiliariaId).orElseGet(() -> repositorio.save(new ParametrosInmobiliaria(inmobiliariaId)));
    }

    public record Parametros(
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("1") BigDecimal pctCuotaMaxIngreso,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("1") BigDecimal pctCuotaInicial,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("1") BigDecimal pctCuotaInicialVis,
            @Min(1) @Max(30) int plazoDefaultAnios,
            @Min(0) @Max(90) int diasAlertaVencimiento,
            List<String> ciudadesConValorizacion,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal umbralSimilitudRag,
            @Email String emailRemitente) {

        static Parametros desde(ParametrosInmobiliaria p) {
            return new Parametros(p.getPctCuotaMaxIngreso(), p.getPctCuotaInicial(), p.getPctCuotaInicialVis(),
                    p.getPlazoDefaultAnios(), p.getDiasAlertaVencimiento(), List.copyOf(p.getCiudadesConValorizacion()),
                    p.getUmbralSimilitudRag(), p.getEmailRemitente());
        }
    }
}
