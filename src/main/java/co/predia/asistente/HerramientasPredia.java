package co.predia.asistente;

import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.recordatorios.Recordatorio;
import co.predia.cierre.reglas.DescriptorItem;
import co.predia.cierre.reglas.EstadoDocumental;
import co.predia.cierre.reglas.EstadoRequisito;
import co.predia.clientes.CalculadoraPresupuesto;
import co.predia.clientes.Cliente;
import co.predia.clientes.PresupuestoCliente;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.credito.SimulacionCredito;
import co.predia.credito.SimuladorCreditoService;
import co.predia.credito.TasaCreditoDto;
import co.predia.expedientes.consulta.Cita;
import co.predia.expedientes.consulta.RespuestaConsulta;
import co.predia.inventario.BusquedaInmuebles;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleDto;
import co.predia.inventario.Operacion;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Las herramientas que el modelo puede elegir. Se crea una instancia por petición y queda atada al usuario
 * autenticado: la inmobiliaria sale de {@code usuario}, NUNCA de un argumento que el modelo pueda inventar.
 *
 * <p>Principios que cumple cada herramienta:
 * <ul>
 *   <li>Los números salen de los servicios de Java (calculadoras y motor de reglas) y se entregan ya escritos
 *       («$300.000.000», «29,6 %») para que el modelo los copie, no los calcule ni los redondee.</li>
 *   <li>Lo que no se puede resolver (no existe, es ambiguo, falta un dato) vuelve como {@code error}, para que el
 *       modelo pregunte al asesor en lugar de suponer.</li>
 *   <li>Ninguna herramienta confirma, recibe ni envía nada: el recordatorio solo se deja como borrador SUGERIDO.</li>
 * </ul>
 */
public class HerramientasPredia {

    static final int MAXIMO_INMUEBLES = 5;

    private final UsuarioActual usuario;
    private final ServiciosHerramientas servicios;
    private final RegistroHerramientas registro;

    public HerramientasPredia(UsuarioActual usuario, ServiciosHerramientas servicios, RegistroHerramientas registro) {
        this.usuario = usuario;
        this.servicios = servicios;
        this.registro = registro;
    }

    @Tool(name = "consultarExpedientes", description = """
            Responde una pregunta sobre el CONTENIDO de los documentos indexados (contratos, certificados, \
            escrituras…) con citas de documento, página y sección. Úsala para cláusulas, linderos, parqueaderos \
            incluidos en la venta, cánones, fechas dentro de un documento, etc. Si 'sinEvidencia' es true, \
            dile al asesor que no hay información suficiente; no inventes la respuesta.""")
    public Map<String, Object> consultarExpedientes(
            @ToolParam(description = "La pregunta tal como la hizo el asesor, con todos sus detalles") String pregunta,
            @ToolParam(required = false, description = "Id o nombre del inmueble al que se limita la búsqueda, "
                    + "p. ej. 'Apto 1102 Cañaveral'. Omítelo para buscar en todos los expedientes") String inmueble) {
        return ejecutar("consultarExpedientes", () -> {
            Long inmuebleId = inmueble == null || inmueble.isBlank() ? null
                    : servicios.resolvedor().inmueble(usuario.inmobiliariaId(), inmueble).getId();
            RespuestaConsulta r = servicios.motorRag().responder(usuario, pregunta, inmuebleId);
            registro.citas(r.citas());

            Map<String, Object> salida = new LinkedHashMap<>();
            salida.put("sinEvidencia", r.sinEvidencia());
            salida.put("respuesta", r.respuesta() == null ? "No se pudo redactar una respuesta con los fragmentos."
                    : r.respuesta());
            salida.put("citas", r.citas().stream().map(HerramientasPredia::describir).toList());
            salida.put("nota", "Conserva las citas [n] tal como vienen en la respuesta.");
            return salida;
        });
    }

    @Tool(name = "buscarInmuebles", description = """
            Busca inmuebles del inventario según filtros y una descripción libre, ordenados por coincidencia. \
            Para saber qué le alcanza a un cliente, pasa 'paraCliente': la herramienta usa el tope de compra \
            calculado por las reglas de Predia (no lo calcules tú). Devuelve como máximo 5 resultados.""")
    public Map<String, Object> buscarInmuebles(
            @ToolParam(required = false, description = "VENTA o ARRIENDO. Por defecto VENTA") String operacion,
            @ToolParam(required = false, description = "Precio máximo en pesos colombianos, solo números") Long precioMax,
            @ToolParam(required = false, description = "Mínimo de habitaciones") Integer habitacionesMin,
            @ToolParam(required = false, description = "Ciudad, p. ej. Floridablanca o Bucaramanga") String ciudad,
            @ToolParam(required = false, description = "Lo que busca la persona en lenguaje natural: "
                    + "'con parqueadero, que acepte mascotas, cerca de colegios'") String descripcion,
            @ToolParam(required = false, description = "Id o nombre de un cliente: si no das precioMax se usa su "
                    + "tope de compra calculado por Predia, y si no das habitacionesMin, las de su perfil") String paraCliente) {
        return ejecutar("buscarInmuebles", () -> {
            Long tenant = usuario.inmobiliariaId();
            BigDecimal tope = precioMax == null ? null : BigDecimal.valueOf(precioMax);
            Integer habitaciones = habitacionesMin;
            String descripcionBusqueda = descripcion;
            Map<String, Object> salida = new LinkedHashMap<>();

            if (paraCliente != null && !paraCliente.isBlank()) {
                Cliente cliente = servicios.resolvedor().cliente(tenant, paraCliente);
                PresupuestoCliente presupuesto = servicios.presupuestos().calcular(tenant, cliente.getId(), null, null, false);
                if (tope == null) {
                    tope = presupuesto.presupuesto().topeDeCompra();
                    salida.put("topeDeCompraDelCliente", Formato.pesos(tope));
                }
                if (habitaciones == null && cliente.getHabitacionesMin() > 0) {
                    habitaciones = (int) cliente.getHabitacionesMin();
                }
                if (descripcionBusqueda == null || descripcionBusqueda.isBlank()) {
                    // Sin una descripción nueva se usan las preferencias que ya registró el asesor del cliente.
                    descripcionBusqueda = cliente.getPreferencias();
                }
                salida.put("cliente", cliente.getNombre());
            }

            BusquedaInmuebles.Respuesta r = servicios.busquedaInmuebles().buscar(tenant,
                    new BusquedaInmuebles.Solicitud(operacionDe(operacion), tope, habitaciones, vacioANulo(ciudad),
                            descripcionBusqueda));

            List<Map<String, Object>> resultados = new ArrayList<>();
            for (BusquedaInmuebles.Resultado res : r.resultados().stream().limit(MAXIMO_INMUEBLES).toList()) {
                resultados.add(describir(res));
            }
            salida.put("totalEncontrados", r.resultados().size());
            salida.put("resultados", resultados);
            salida.put("ordenadoPorSimilitud", r.ordenadoPorSimilitud());
            if (r.aviso() != null) {
                salida.put("aviso", r.aviso());
            }
            if (resultados.isEmpty()) {
                salida.put("nota", "No hay inmuebles con esos filtros. No propongas inmuebles que no estén aquí.");
            }
            return salida;
        });
    }

    @Tool(name = "calcularPresupuesto", description = """
            Calcula cuánto puede comprar un cliente: cuota máxima mensual, tope de compra y qué lo limita \
            (el ahorro para la cuota inicial o la capacidad de pago), con la tasa y el plazo usados. \
            Es el ÚNICO lugar de donde puedes sacar esas cifras.""")
    public Map<String, Object> calcularPresupuesto(
            @ToolParam(description = "Id o nombre del cliente, p. ej. 'Paola y Juan'") String cliente,
            @ToolParam(required = false, description = "Plazo en años (1 a 30). Por defecto el de la inmobiliaria") Integer plazoAnios) {
        return ejecutar("calcularPresupuesto", () -> {
            Long tenant = usuario.inmobiliariaId();
            Cliente c = servicios.resolvedor().cliente(tenant, cliente);
            PresupuestoCliente p = servicios.presupuestos().calcular(tenant, c.getId(), plazoAnios, null, false);
            CalculadoraPresupuesto.Presupuesto pr = p.presupuesto();

            Map<String, Object> salida = new LinkedHashMap<>();
            salida.put("cliente", p.nombre());
            salida.put("clienteId", p.clienteId());
            salida.put("plazo", Formato.anios(p.plazoAnios()));
            salida.put("tasa", describir(p.tasa()));
            salida.put("ingresoFamiliar", Formato.pesos(p.ingresoFamiliar()));
            salida.put("ahorro", Formato.pesos(p.ahorro()));
            salida.put("deudasMensuales", Formato.pesos(p.deudasMensuales()));
            salida.put("cuotaMaximaMensual", Formato.pesos(pr.cuotaMaxima()) + " ("
                    + Formato.porcentaje(p.pctCuotaMaximaIngreso()) + " del ingreso)");
            salida.put("montoQueSoportaLaCuota", Formato.pesos(pr.montoQueSoportaLaCuota()));
            salida.put("topePorAhorro", Formato.pesos(pr.topePorAhorro()) + " (el ahorro cubre la cuota inicial de "
                    + Formato.porcentaje(p.pctCuotaInicial()) + ")");
            salida.put("topePorCapacidadDePago", Formato.pesos(pr.topePorCapacidadDePago()));
            salida.put("topeDeCompra", Formato.pesos(pr.topeDeCompra()));
            salida.put("limitadoPor", pr.limitante() == CalculadoraPresupuesto.Limitante.AHORRO
                    ? "el ahorro (cuota inicial)" : "la capacidad de pago");
            salida.put("cuotaInicialMinimaDelTope", Formato.pesos(pr.cuotaInicialMinimaDelTope()));
            salida.put("avisos", p.avisos());
            return salida;
        });
    }

    @Tool(name = "simularCredito", description = """
            Simula el crédito hipotecario de un cliente para un inmueble en venta: monto a financiar, cuota \
            mensual, total a pagar, intereses, si cumple la cuota máxima y la cuota inicial mínima. \
            Es una simulación; la aprobación es del banco.""")
    public Map<String, Object> simularCredito(
            @ToolParam(description = "Id o nombre del inmueble en venta, p. ej. 'Apto 1102 Cañaveral'") String inmueble,
            @ToolParam(description = "Id o nombre del cliente, p. ej. 'Paola y Juan'") String cliente,
            @ToolParam(required = false, description = "Plazo en años (1 a 30). Por defecto el de la inmobiliaria") Integer plazoAnios,
            @ToolParam(required = false, description = "Banco de la tasa a usar, p. ej. 'Banco Ejemplo'") String banco) {
        return ejecutar("simularCredito", () -> {
            Long tenant = usuario.inmobiliariaId();
            Inmueble i = servicios.resolvedor().inmueble(tenant, inmueble);
            Cliente c = servicios.resolvedor().cliente(tenant, cliente);
            SimulacionCredito s = servicios.simulador().simular(tenant,
                    new SimuladorCreditoService.Solicitud(null, i.getId(), c.getId(), plazoAnios, null,
                            vacioANulo(banco)));

            Map<String, Object> salida = new LinkedHashMap<>();
            salida.put("inmueble", i.getTitulo());
            salida.put("cliente", c.getNombre());
            salida.put("precioInmueble", Formato.pesos(s.precioInmueble()));
            salida.put("cuotaInicialDelCliente", Formato.pesos(s.cuotaInicial()));
            salida.put("cuotaInicialMinima", Formato.pesos(s.cuotaInicialMinima()));
            salida.put("cumpleCuotaInicial", s.cumpleCuotaInicial());
            salida.put("montoFinanciado", Formato.pesos(s.montoFinanciado()));
            salida.put("plazo", Formato.anios(s.plazoAnios()) + " (" + s.numeroCuotas() + " cuotas)");
            salida.put("tasa", describir(s.tasa()));
            salida.put("cuotaMensual", Formato.pesos(s.cuotaMensual()));
            salida.put("totalAPagar", Formato.pesos(s.totalAPagar()));
            salida.put("totalIntereses", Formato.pesos(s.totalIntereses()));
            salida.put("cuotaMaximaDelCliente", Formato.pesos(s.cuotaMaxima()));
            salida.put("cuotaSobreIngreso", Formato.porcentaje(s.porcentajeSobreIngreso()));
            salida.put("cumpleCuotaMaxima", s.cumpleCuotaMaxima());
            salida.put("aviso", s.aviso());
            return salida;
        });
    }

    @Tool(name = "estadoDocumentalNegocio", description = """
            Dice cómo va el cierre de un negocio: semáforo, cuántos documentos faltan, cuáles están pendientes, \
            vencidos o por vencer (con sus fechas) y si ya está listo para firmar. Úsala para «¿qué le falta al \
            dueño del apto…?». Las fechas y los plazos vienen ya escritos: cópialos sin cambiarlos.""")
    public Map<String, Object> estadoDocumentalNegocio(
            @ToolParam(description = "Id del negocio, o el nombre del inmueble (o del propietario), "
                    + "p. ej. 'Apto 1102 Cañaveral'") String negocio) {
        return ejecutar("estadoDocumentalNegocio", () -> {
            Negocio n = servicios.resolvedor().negocio(usuario.inmobiliariaId(), negocio);
            EstadoDocumental estado = servicios.estadoDocumental().calcular(n);

            Map<String, Object> salida = new LinkedHashMap<>();
            salida.put("negocioId", n.getId());
            salida.put("inmueble", nombreInmueble(n));
            salida.put("propietario", nombrePropietario(n));
            salida.put("estadoDelNegocio", n.getEstado().name());
            salida.put("semaforo", estado.semaforo());
            salida.put("listoParaFirmar", estado.listoParaFirmar());
            salida.put("documentosQueFaltan", estado.faltan());
            salida.put("documentosRecibidos", estado.recibidos() + " de " + estado.obligatoriosQueAplican());
            salida.put("avance", estado.porcentajeAvance() + " %");
            if (n.getFechaFirmaTentativa() != null) {
                salida.put("fechaDeFirmaTentativa", DescriptorItem.fecha(n.getFechaFirmaTentativa()));
            }
            salida.put("quePedirleAlPropietario", estado.faltantesParaElPropietario().stream()
                    .map(i -> i.nombre() + ": " + i.detalle()).toList());
            salida.put("documentos", estado.items().stream().filter(i -> i.estado() != EstadoRequisito.NO_APLICA)
                    .map(i -> {
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("documento", i.nombre());
                        d.put("obligatorio", i.obligatorio());
                        d.put("estado", i.estado().name());
                        d.put("detalle", i.detalle());
                        return d;
                    }).toList());
            salida.put("advertencias", estado.advertencias());
            salida.put("aviso", estado.aviso());
            return salida;
        });
    }

    @Tool(name = "redactarRecordatorioPropietario", description = """
            Deja un BORRADOR de recordatorio para el propietario con los documentos que faltan o están por \
            vencer. NO lo envía: queda en la bandeja de Recordatorios y solo sale cuando el asesor lo revisa y \
            lo aprueba. Nunca digas que se envió; di que quedó como borrador para su aprobación.""")
    public Map<String, Object> redactarRecordatorioPropietario(
            @ToolParam(description = "Id del negocio, o el nombre del inmueble, p. ej. 'Apto 1102 Cañaveral'") String negocio) {
        return ejecutar("redactarRecordatorioPropietario", () -> {
            Negocio n = servicios.resolvedor().negocio(usuario.inmobiliariaId(), negocio);
            Recordatorio r = servicios.recordatorios().generar(n);
            registro.recordatorioCreado(r.getId());

            Map<String, Object> salida = new LinkedHashMap<>();
            salida.put("recordatorioId", r.getId());
            salida.put("estado", r.getEstado().name());
            salida.put("enviado", false);
            salida.put("inmueble", nombreInmueble(n));
            salida.put("propietario", nombrePropietario(n));
            salida.put("asunto", r.getAsunto());
            salida.put("cuerpo", r.cuerpoVigente());
            salida.put("siguientePaso", "El asesor debe revisarlo y aprobarlo en la bandeja de Recordatorios; "
                    + "hasta entonces no se envía nada al propietario.");
            return salida;
        });
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * Ejecuta una herramienta: la registra, y si no puede completarse por una razón de negocio (no existe, es
     * ambiguo, falta un dato) devuelve el motivo al modelo en lugar de romper la conversación.
     */
    private Map<String, Object> ejecutar(String nombre, Supplier<Map<String, Object>> accion) {
        registro.usada(nombre);
        Map<String, Object> salida;
        try {
            salida = accion.get();
        } catch (ReferenciaException | ReglaNegocioException | RecursoNoEncontradoException
                 | IllegalArgumentException e) {
            salida = Map.of("error", e.getMessage() == null ? "No se pudo completar la consulta." : e.getMessage(),
                    "nota", "No supongas el dato: díselo al asesor y pregúntale lo que falte.");
        }
        registro.salida(salida.toString());
        return salida;
    }

    private static Map<String, Object> describir(BusquedaInmuebles.Resultado res) {
        InmuebleDto i = res.inmueble();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", i.id());
        d.put("codigo", i.codigo());
        d.put("titulo", i.titulo());
        d.put("ubicacion", String.join(", ", i.direccion() == null ? "" : i.direccion(),
                i.barrio() == null ? "" : i.barrio(), i.ciudad() == null ? "" : i.ciudad()));
        if (i.operacion() == Operacion.VENTA) {
            d.put("precioVenta", Formato.pesos(i.precioVenta()));
        } else {
            d.put("canonArriendo", Formato.pesos(i.canonArriendo()));
        }
        d.put("habitaciones", i.habitaciones());
        d.put("banos", i.banos());
        d.put("parqueaderos", i.parqueaderos());
        d.put("areaM2", i.areaM2() == null ? null : i.areaM2().stripTrailingZeros().toPlainString() + " m²");
        d.put("aceptaMascotas", i.aceptaMascotas());
        if (i.valorAdministracion() != null) {
            d.put("administracion", Formato.pesos(i.valorAdministracion()));
        }
        if (res.coincidenciaPct() != null) {
            d.put("coincidencia", res.coincidenciaPct() + " %");
        }
        return d;
    }

    private static String describir(Cita c) {
        String paginas = c.paginaInicio() == c.paginaFin() ? "pág. " + c.paginaInicio()
                : "págs. " + c.paginaInicio() + "-" + c.paginaFin();
        String seccion = c.seccion() == null || c.seccion().isBlank() ? "" : ", " + c.seccion();
        return "[%d] %s, %s%s".formatted(c.numero(), c.nombreArchivo(), paginas, seccion);
    }

    private static String describir(TasaCreditoDto t) {
        return "%s, %s E.A.%s".formatted(t.banco(), Formato.porcentaje(t.tasaEa()),
                t.esEjemplo() ? " (tasa de ejemplo, no real)" : "");
    }

    private String nombreInmueble(Negocio n) {
        return servicios.inmuebles().findByIdAndInmobiliariaId(n.getInmuebleId(), usuario.inmobiliariaId())
                .map(Inmueble::getTitulo).orElse("(inmueble no disponible)");
    }

    private String nombrePropietario(Negocio n) {
        return servicios.propietarios().findByIdAndInmobiliariaId(n.getPropietarioId(), usuario.inmobiliariaId())
                .map(Propietario::getNombre).orElse("(propietario no disponible)");
    }

    private static Operacion operacionDe(String texto) {
        if (texto == null || texto.isBlank()) {
            return Operacion.VENTA;
        }
        String t = Texto.normalizar(texto);
        if (t.startsWith("arriend") || t.startsWith("alquil")) {
            return Operacion.ARRIENDO;
        }
        if (t.startsWith("venta") || t.startsWith("vender") || t.startsWith("compra")) {
            return Operacion.VENTA;
        }
        throw new IllegalArgumentException("La operación debe ser VENTA o ARRIENDO.");
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
