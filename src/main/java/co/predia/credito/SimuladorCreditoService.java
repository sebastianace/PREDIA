package co.predia.credito;

import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Simula un crédito con cuota fija. La IA no interviene: todo es aritmética explícita con BigDecimal. */
@Service
public class SimuladorCreditoService {

    private final TasaCreditoService tasas;
    private final InmuebleRepository inmuebles;
    private final ClienteRepository clientes;
    private final ParametrosInmobiliariaRepository parametros;

    public SimuladorCreditoService(TasaCreditoService tasas, InmuebleRepository inmuebles, ClienteRepository clientes,
                                   ParametrosInmobiliariaRepository parametros) {
        this.tasas = tasas;
        this.inmuebles = inmuebles;
        this.clientes = clientes;
        this.parametros = parametros;
    }

    public record Solicitud(BigDecimal monto, Long inmuebleId, Long clienteId, Integer plazoAnios, Long tasaId,
                            String banco) {
    }

    public SimulacionCredito simular(Long inmobiliariaId, Solicitud s) {
        ParametrosInmobiliaria params = parametros.findById(inmobiliariaId)
                .orElseGet(() -> new ParametrosInmobiliaria(inmobiliariaId));
        int plazoAnios = s.plazoAnios() != null ? s.plazoAnios() : params.getPlazoDefaultAnios();
        if (plazoAnios < 1 || plazoAnios > 30) {
            throw new IllegalArgumentException("El plazo debe estar entre 1 y 30 años.");
        }

        Inmueble inmueble = s.inmuebleId() == null ? null
                : inmuebles.findByIdAndInmobiliariaId(s.inmuebleId(), inmobiliariaId)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", s.inmuebleId()));
        Cliente cliente = s.clienteId() == null ? null
                : clientes.findByIdAndInmobiliariaId(s.clienteId(), inmobiliariaId)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", s.clienteId()));
        if (inmueble != null && (inmueble.getOperacion() != Operacion.VENTA || inmueble.getPrecioVenta() == null)) {
            throw new ReglaNegocioException("El crédito hipotecario solo aplica a inmuebles en venta.");
        }

        BigDecimal monto = s.monto();
        BigDecimal cuotaInicial = null;
        BigDecimal cuotaInicialMinima = null;
        Boolean cumpleCuotaInicial = null;
        if (inmueble != null && cliente != null) {
            BigDecimal pctInicial = inmueble.isEsVis() ? params.getPctCuotaInicialVis() : params.getPctCuotaInicial();
            cuotaInicial = cliente.getAhorro();
            cuotaInicialMinima = inmueble.getPrecioVenta().multiply(pctInicial).setScale(0, RoundingMode.UP);
            cumpleCuotaInicial = cuotaInicial.compareTo(cuotaInicialMinima) >= 0;
            if (monto == null) {
                // El ahorro del cliente se usa como cuota inicial; el resto se financia.
                monto = inmueble.getPrecioVenta().subtract(cuotaInicial);
            }
        }
        if (monto == null) {
            throw new ReglaNegocioException("Indica el monto a financiar, o un inmueble y un cliente.");
        }
        if (monto.signum() <= 0) {
            throw new ReglaNegocioException("No hay nada que financiar: el ahorro cubre todo el precio.");
        }

        TipoTasa tipo = inmueble != null && inmueble.isEsVis() ? TipoTasa.VIS : TipoTasa.NO_VIS;
        TasaCredito tasa = tasas.elegir(inmobiliariaId, s.tasaId(), s.banco(), tipo);

        int cuotas = plazoAnios * 12;
        BigDecimal tasaMensual = CalculadoraCredito.tasaMensual(tasa.getTasaEa());
        BigDecimal cuota = CalculadoraCredito.cuota(monto, tasaMensual, cuotas);
        BigDecimal total = cuota.multiply(BigDecimal.valueOf(cuotas));

        BigDecimal cuotaMaxima = null;
        BigDecimal pctSobreIngreso = null;
        Boolean cumpleCuotaMaxima = null;
        if (cliente != null) {
            cuotaMaxima = cliente.getIngresoFamiliar().multiply(params.getPctCuotaMaxIngreso())
                    .setScale(0, RoundingMode.DOWN);
            cumpleCuotaMaxima = cuota.compareTo(cuotaMaxima) <= 0;
            if (cliente.getIngresoFamiliar().signum() > 0) {
                pctSobreIngreso = cuota.divide(cliente.getIngresoFamiliar(), 4, RoundingMode.HALF_UP);
            }
        }

        return new SimulacionCredito(monto, plazoAnios, cuotas, cuota, total, total.subtract(monto), tasaMensual,
                TasaCreditoDto.desde(tasa), cuotaMaxima, pctSobreIngreso, cumpleCuotaMaxima,
                inmueble == null ? null : inmueble.getPrecioVenta(), cuotaInicial, cuotaInicialMinima,
                cumpleCuotaInicial, SimulacionCredito.AVISO);
    }
}
