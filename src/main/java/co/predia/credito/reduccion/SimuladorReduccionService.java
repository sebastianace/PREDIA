package co.predia.credito.reduccion;

import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.credito.reduccion.CalculadoraReduccion.Frecuencia;
import co.predia.credito.reduccion.CalculadoraReduccion.Modo;
import co.predia.credito.reduccion.CalculadoraReduccion.Resultado;
import co.predia.credito.reduccion.SimulacionReduccion.Escenario;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Simula abonos extraordinarios a un crédito existente. La IA no interviene: todo es aritmética con BigDecimal. */
@Service
public class SimuladorReduccionService {

    public static final int MAX_ESCENARIOS = 3;
    public static final int MAX_CUOTAS = 360;

    private final ClienteRepository clientes;
    private final ParametrosInmobiliariaRepository parametros;

    public SimuladorReduccionService(ClienteRepository clientes, ParametrosInmobiliariaRepository parametros) {
        this.clientes = clientes;
        this.parametros = parametros;
    }

    /**
     * @param tasaEa            fracción (0,11 = 11 % E.A.)
     * @param abonos            hasta {@value #MAX_ESCENARIOS} valores de abono; vacío = solo el escenario actual
     * @param escenarioDeLaTabla 0 = actual, 1..n = el abono correspondiente
     */
    public record Solicitud(Long clienteId, String banco, BigDecimal saldo, BigDecimal tasaEa, Integer cuotasPendientes,
                            BigDecimal seguroMensual, Modo modo, Frecuencia frecuencia, List<BigDecimal> abonos,
                            Integer escenarioDeLaTabla) {
    }

    public SimulacionReduccion simular(Long inmobiliariaId, Solicitud s) {
        if (s.saldo() == null || s.saldo().signum() <= 0) {
            throw new IllegalArgumentException("El saldo del crédito debe ser mayor que cero.");
        }
        if (s.tasaEa() == null || s.tasaEa().signum() < 0 || s.tasaEa().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("La tasa efectiva anual va como fracción entre 0 y 1 (0,11 = 11 %).");
        }
        if (s.cuotasPendientes() == null || s.cuotasPendientes() < 1 || s.cuotasPendientes() > MAX_CUOTAS) {
            throw new IllegalArgumentException("Las cuotas pendientes deben estar entre 1 y " + MAX_CUOTAS + ".");
        }
        BigDecimal seguro = s.seguroMensual() == null ? BigDecimal.ZERO : s.seguroMensual();
        if (seguro.signum() < 0) {
            throw new IllegalArgumentException("El seguro mensual no puede ser negativo.");
        }
        List<BigDecimal> abonos = s.abonos() == null ? List.of() : s.abonos();
        if (abonos.size() > MAX_ESCENARIOS) {
            throw new IllegalArgumentException("Se pueden comparar como máximo " + MAX_ESCENARIOS + " abonos.");
        }
        if (abonos.stream().anyMatch(a -> a == null || a.signum() <= 0)) {
            throw new IllegalArgumentException("Cada abono extraordinario debe ser mayor que cero.");
        }
        int escenarioTabla = s.escenarioDeLaTabla() == null ? Math.min(1, abonos.size()) : s.escenarioDeLaTabla();
        if (escenarioTabla < 0 || escenarioTabla > abonos.size()) {
            throw new IllegalArgumentException("El escenario de la tabla no existe.");
        }
        Modo modo = s.modo() == null ? Modo.REDUCIR_PLAZO : s.modo();
        Frecuencia frecuencia = s.frecuencia() == null ? Frecuencia.MENSUAL : s.frecuencia();

        Cliente cliente = s.clienteId() == null ? null
                : clientes.findByIdAndInmobiliariaId(s.clienteId(), inmobiliariaId)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", s.clienteId()));
        ParametrosInmobiliaria params = parametros.findById(inmobiliariaId)
                .orElseGet(() -> new ParametrosInmobiliaria(inmobiliariaId));
        BigDecimal pctMax = params.getPctCuotaMaxIngreso();

        Resultado actual = CalculadoraReduccion.simular(s.saldo(), s.tasaEa(), s.cuotasPendientes(), BigDecimal.ZERO,
                frecuencia, modo);
        List<Resultado> conAbono = new ArrayList<>();
        for (BigDecimal abono : abonos) {
            conAbono.add(CalculadoraReduccion.simular(s.saldo(), s.tasaEa(), s.cuotasPendientes(), abono, frecuencia,
                    modo));
        }

        List<Escenario> escenarios = new ArrayList<>();
        for (int i = 0; i < abonos.size(); i++) {
            escenarios.add(escenario(abonos.get(i), conAbono.get(i), actual, seguro, frecuencia, pctMax, cliente));
        }
        Resultado deLaTabla = escenarioTabla == 0 ? actual : conAbono.get(escenarioTabla - 1);

        return new SimulacionReduccion(cliente == null ? null : cliente.getId(),
                cliente == null ? null : cliente.getNombre(), s.banco() == null || s.banco().isBlank() ? null
                        : s.banco().trim(), s.saldo(), s.tasaEa(), s.cuotasPendientes(), seguro, modo, frecuencia,
                pctMax, actual.filas().get(0).capital(), actual.filas().get(0).intereses(), base(actual, seguro), escenarios, escenarioTabla, deLaTabla.filas(),
                SimulacionReduccion.AVISO);
    }

    private static Escenario base(Resultado r, BigDecimal seguro) {
        return new Escenario(null, r.cuotaInicial(), r.cuotaFinal(), r.cuotas(), r.totalIntereses(), r.totalAbonos(),
                r.totalPagado(), seguro.multiply(BigDecimal.valueOf(r.cuotas())), r.cuotaInicial().add(seguro),
                null, null, null, null, null, null);
    }

    private static Escenario escenario(BigDecimal abono, Resultado r, Resultado actual, BigDecimal seguro,
                                       Frecuencia f, BigDecimal pctMax, Cliente cliente) {
        BigDecimal totalSeguro = seguro.multiply(BigDecimal.valueOf(r.cuotas()));
        int reducidas = actual.cuotas() - r.cuotas();
        BigDecimal ahorroIntereses = actual.totalIntereses().subtract(r.totalIntereses());
        BigDecimal ahorroSeguro = seguro.multiply(BigDecimal.valueOf(reducidas));
        // Lo que se desembolsa cada mes: el abono solo cuenta si se hace todos los meses.
        BigDecimal desembolso = r.cuotaInicial().add(seguro);
        if (f == Frecuencia.MENSUAL) {
            desembolso = desembolso.add(abono);
        }
        // Ingreso con el que ese desembolso no pasa del porcentaje máximo que la inmobiliaria acepta.
        BigDecimal ingresoRequerido = pctMax.signum() > 0
                ? desembolso.divide(pctMax, 0, RoundingMode.UP) : null;
        Boolean cumple = cliente != null && ingresoRequerido != null
                ? cliente.getIngresoFamiliar().compareTo(ingresoRequerido) >= 0 : null;
        return new Escenario(abono, r.cuotaInicial(), r.cuotaFinal(), r.cuotas(), r.totalIntereses(), r.totalAbonos(),
                r.totalPagado(), totalSeguro, desembolso, ahorroIntereses, ahorroSeguro,
                ahorroIntereses.add(ahorroSeguro), reducidas, ingresoRequerido, cumple);
    }
}
