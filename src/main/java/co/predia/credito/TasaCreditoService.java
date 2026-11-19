package co.predia.credito;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Elige la tasa con la que se calcula un presupuesto o una simulación. */
@Service
public class TasaCreditoService {

    private final TasaCreditoRepository tasas;

    public TasaCreditoService(TasaCreditoRepository tasas) {
        this.tasas = tasas;
    }

    /**
     * Prioridad: la tasa pedida por id; si no, la vigente más reciente del banco pedido; si no, la vigente
     * más reciente del tipo (VIS o NO_VIS). Si no hay ninguna cargada, no se inventa: se avisa.
     */
    public TasaCredito elegir(Long inmobiliariaId, Long tasaId, String banco, TipoTasa tipo) {
        if (tasaId != null) {
            return tasas.findByIdAndInmobiliariaId(tasaId, inmobiliariaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Tasa", tasaId));
        }
        LocalDate hoy = LocalDate.now();
        if (banco != null && !banco.isBlank()) {
            return tasas.findFirstByInmobiliariaIdAndTipoAndBancoIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDescIdDesc(
                            inmobiliariaId, tipo, banco.strip(), hoy)
                    .orElseThrow(() -> new ReglaNegocioException(
                            "No hay una tasa %s vigente cargada para el banco «%s».".formatted(tipo, banco.strip())));
        }
        return tasas.findFirstByInmobiliariaIdAndTipoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDescIdDesc(
                        inmobiliariaId, tipo, hoy)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No hay una tasa %s vigente cargada. Pídele al administrador que la cargue.".formatted(tipo)));
    }
}
