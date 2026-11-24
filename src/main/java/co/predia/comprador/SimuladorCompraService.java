package co.predia.comprador;

import co.predia.asistente.Formato;
import co.predia.clientes.Cliente;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.credito.SimulacionCredito;
import co.predia.credito.SimuladorCreditoService;
import co.predia.credito.TasaCreditoDto;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inventario.EstadoInmueble;
import co.predia.inventario.Inmueble;
import co.predia.inventario.Operacion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * El simulador de compra del comprador: reutiliza SimuladorCreditoService (que a su vez usa CalculadoraCredito) y solo
 * agrega el rango de la cuota inicial (CalculadoraCompra). Todas las cifras y el veredicto los escribe Java.
 */
@Service
public class SimuladorCompraService {

    public static final int PLAZO_MINIMO = 1;
    public static final int PLAZO_MAXIMO = 30;

    /** Lo que necesita la pantalla para armar los controles del simulador. */
    public record Controles(BigDecimal cuotaInicialMinima, BigDecimal cuotaInicialMaxima, boolean ahorroAlcanza,
                            BigDecimal faltanteCuotaInicial, int plazoPorDefecto, int plazoMinimo, int plazoMaximo) {
    }

    public record SimulacionCompra(
            BigDecimal precio, BigDecimal cuotaInicial, BigDecimal cuotaInicialMinima, BigDecimal cuotaInicialMaxima,
            boolean ahorroAlcanza, BigDecimal faltanteCuotaInicial, BigDecimal montoFinanciado, int plazoAnios,
            int numeroCuotas, BigDecimal cuotaMensual, BigDecimal totalAPagar, BigDecimal totalIntereses,
            BigDecimal cuotaMaxima, BigDecimal porcentajeSobreIngreso, boolean cumpleCuotaMaxima,
            BigDecimal excesoMensual, String veredicto, TasaCreditoDto tasa, String aviso) {
    }

    private final SimuladorCreditoService simulador;
    private final ParametrosInmobiliariaRepository parametros;

    public SimuladorCompraService(SimuladorCreditoService simulador, ParametrosInmobiliariaRepository parametros) {
        this.simulador = simulador;
        this.parametros = parametros;
    }

    public Controles controles(Long inmobiliariaId, Inmueble inmueble, Cliente cliente) {
        ParametrosInmobiliaria params = params(inmobiliariaId);
        CalculadoraCompra.Rango rango = rango(inmueble, cliente, params);
        return new Controles(rango.minima(), rango.maxima(), rango.ahorroAlcanza(), rango.faltante(),
                params.getPlazoDefaultAnios(), PLAZO_MINIMO, PLAZO_MAXIMO);
    }

    public SimulacionCompra simular(Long inmobiliariaId, Inmueble inmueble, Cliente cliente, BigDecimal cuotaInicial,
                                    Integer plazoAnios) {
        if (inmueble.getEstado() != EstadoInmueble.DISPONIBLE || inmueble.getOperacion() != Operacion.VENTA) {
            throw new ReglaNegocioException("Este inmueble ya no está disponible para comprar.");
        }
        ParametrosInmobiliaria params = params(inmobiliariaId);
        CalculadoraCompra.Rango rango = rango(inmueble, cliente, params);
        if (cuotaInicial == null || cuotaInicial.compareTo(rango.minima()) < 0) {
            throw new ReglaNegocioException("La cuota inicial mínima es " + Formato.pesos(rango.minima()) + ".");
        }
        if (cuotaInicial.compareTo(rango.maxima()) > 0) {
            throw new ReglaNegocioException("La cuota inicial no puede superar " + Formato.pesos(rango.maxima()) + ".");
        }
        int plazo = plazoAnios == null ? params.getPlazoDefaultAnios() : plazoAnios;
        if (plazo < PLAZO_MINIMO || plazo > PLAZO_MAXIMO) {
            throw new ReglaNegocioException("El plazo debe estar entre " + PLAZO_MINIMO + " y " + PLAZO_MAXIMO + " años.");
        }

        BigDecimal monto = inmueble.getPrecioVenta().subtract(cuotaInicial);
        SimulacionCredito s = simulador.simular(inmobiliariaId, new SimuladorCreditoService.Solicitud(monto,
                inmueble.getId(), cliente.getId(), plazo, null, null));

        BigDecimal exceso = s.cuotaMensual().subtract(s.cuotaMaxima()).max(BigDecimal.ZERO);
        boolean cumple = Boolean.TRUE.equals(s.cumpleCuotaMaxima());
        String veredicto = cumple
                ? "Está dentro de su cuota máxima de " + Formato.pesos(s.cuotaMaxima()) + " al mes."
                : "La cuota supera su máximo de " + Formato.pesos(s.cuotaMaxima()) + " por " + Formato.pesos(exceso)
                + " al mes. Con más plazo o más cuota inicial puede bajar.";
        return new SimulacionCompra(inmueble.getPrecioVenta(), cuotaInicial, rango.minima(), rango.maxima(),
                rango.ahorroAlcanza(), rango.faltante(), s.montoFinanciado(), s.plazoAnios(), s.numeroCuotas(),
                s.cuotaMensual(), s.totalAPagar(), s.totalIntereses(), s.cuotaMaxima(), s.porcentajeSobreIngreso(),
                cumple, exceso, veredicto, s.tasa(), SimulacionCredito.AVISO);
    }

    private CalculadoraCompra.Rango rango(Inmueble inmueble, Cliente cliente, ParametrosInmobiliaria params) {
        BigDecimal pct = inmueble.isEsVis() ? params.getPctCuotaInicialVis() : params.getPctCuotaInicial();
        return CalculadoraCompra.rango(inmueble.getPrecioVenta(), pct, cliente.getAhorro());
    }

    private ParametrosInmobiliaria params(Long inmobiliariaId) {
        return parametros.findById(inmobiliariaId).orElseGet(() -> new ParametrosInmobiliaria(inmobiliariaId));
    }
}
