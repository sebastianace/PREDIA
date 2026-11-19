package co.predia.clientes;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.credito.TasaCredito;
import co.predia.credito.TasaCreditoDto;
import co.predia.credito.TasaCreditoService;
import co.predia.credito.TipoTasa;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Calcula el presupuesto de compra de un cliente con los parámetros de su inmobiliaria. */
@Service
public class PresupuestoService {

    private final ClienteRepository clientes;
    private final ParametrosInmobiliariaRepository parametros;
    private final TasaCreditoService tasas;

    public PresupuestoService(ClienteRepository clientes, ParametrosInmobiliariaRepository parametros,
                              TasaCreditoService tasas) {
        this.clientes = clientes;
        this.parametros = parametros;
        this.tasas = tasas;
    }

    public PresupuestoCliente calcular(Long inmobiliariaId, Long clienteId, Integer plazoAnios, Long tasaId,
                                       boolean vis) {
        Cliente cliente = clientes.findByIdAndInmobiliariaId(clienteId, inmobiliariaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", clienteId));
        ParametrosInmobiliaria params = parametros.findById(inmobiliariaId)
                .orElseGet(() -> new ParametrosInmobiliaria(inmobiliariaId));

        int plazo = plazoAnios != null ? plazoAnios : params.getPlazoDefaultAnios();
        if (plazo < 1 || plazo > 30) {
            throw new IllegalArgumentException("El plazo debe estar entre 1 y 30 años.");
        }
        TasaCredito tasa = tasas.elegir(inmobiliariaId, tasaId, null, vis ? TipoTasa.VIS : TipoTasa.NO_VIS);
        var pctInicial = vis ? params.getPctCuotaInicialVis() : params.getPctCuotaInicial();

        var presupuesto = CalculadoraPresupuesto.calcular(new CalculadoraPresupuesto.Entrada(
                cliente.getIngresoFamiliar(), cliente.getAhorro(), cliente.getDeudasMensuales(),
                params.getPctCuotaMaxIngreso(), pctInicial, tasa.getTasaEa(), plazo * 12));

        List<String> avisos = new ArrayList<>();
        avisos.add("Es una estimación; la aprobación del crédito es del banco.");
        if (tasa.isEsEjemplo()) {
            avisos.add("La tasa usada es de ejemplo, no una tasa real.");
        }
        if (presupuesto.alertaEndeudamiento()) {
            avisos.add("Con sus deudas mensuales, el endeudamiento estimado supera el 40 % del ingreso. "
                    + "Las deudas no se restan de la cuota máxima, pero el banco puede tenerlas en cuenta.");
        }
        return new PresupuestoCliente(cliente.getId(), cliente.getNombre(), plazo, vis,
                params.getPctCuotaMaxIngreso(), pctInicial, TasaCreditoDto.desde(tasa), cliente.getIngresoFamiliar(),
                cliente.getAhorro(), cliente.getDeudasMensuales(), presupuesto, avisos);
    }
}
