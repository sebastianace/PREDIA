package co.predia.asistente;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.cierre.recordatorios.EstadoRecordatorio;
import co.predia.cierre.recordatorios.RecordatorioRepository;
import co.predia.cierre.reglas.ServicioEstadoDocumental;
import co.predia.clientes.CalculadoraPresupuesto;
import co.predia.clientes.Cliente;
import co.predia.clientes.PresupuestoService;
import co.predia.comun.ia.EstadoIa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.AsistenteSimulado;
import co.predia.soporte.AsistenteSimulado.Llamada;
import co.predia.soporte.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El asistente de punta a punta con un modelo de mentira ({@link AsistenteSimulado}): las herramientas son las
 * reales y la base de datos es PostgreSQL real, pero NUNCA se llama a OpenAI ni se envía un correo.
 */
class AsistenteApiTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;
    @Autowired ServicioEstadoDocumental estadoDocumental;
    @Autowired PresupuestoService presupuestos;
    @Autowired RecordatorioRepository recordatorios;
    @MockitoBean EstadoIa estadoIa;
    @MockitoBean ChatModel chatModel;
    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean JavaMailSender correo;

    private static final ObjectMapper JSON = new ObjectMapper();

    private void iaDisponible() {
        when(estadoIa.disponible()).thenReturn(true);
        when(estadoIa.modeloChat()).thenReturn("modelo-de-pruebas");
        when(estadoIa.modeloEmbeddings()).thenReturn("embeddings-de-pruebas");
        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<?> textos = inv.getArgument(0);
            return textos.stream().map(t -> vector(1)).toList();
        });
    }

    private static float[] vector(int dimension) {
        float[] v = new float[1536];
        v[dimension] = 1f;
        return v;
    }

    private static String juntar(List<String> resultados) {
        return String.join("\n", resultados);
    }

    private JsonNode preguntar(Usuario usuario, String mensaje, String conversacionId) throws Exception {
        String cuerpo = JSON.writeValueAsString(conversacionId == null ? java.util.Map.of("mensaje", mensaje)
                : java.util.Map.of("mensaje", mensaje, "conversacionId", conversacionId));
        String json = mvc.perform(post("/api/asistente/mensajes").header("Authorization", bearer(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(json);
    }

    private Inmueble inmuebleEn(Inmobiliaria inmobiliaria, String codigo, String titulo, String ciudad, String precio) {
        Inmueble i = crearInmueble(inmobiliaria, codigo);
        i.setTitulo(titulo);
        i.setCiudad(ciudad);
        i.setPrecioVenta(new BigDecimal(precio));
        i.setHabitaciones((short) 3);
        return inmuebles.save(i);
    }

    // ------------------------------------------------------------------ sin IA

    @Test
    void sinIaElAsistenteAvisaQueEstaDeshabilitadoYNoLlamaAlModelo() throws Exception {
        when(estadoIa.disponible()).thenReturn(false);
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Sin IA");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/asistente/mensajes").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\": \"Hola\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("deshabilitado")));

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void sinSesionOConMensajeVacioSeRechaza() throws Exception {
        mvc.perform(post("/api/asistente/mensajes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\": \"Hola\"}"))
                .andExpect(status().isUnauthorized());

        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Validación");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        mvc.perform(post("/api/asistente/mensajes").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ presupuesto + búsqueda

    @Test
    void queLesAlcanzaAPaolaYJuanUsaElPresupuestoDeJavaYFiltraLosInmuebles() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Presupuesto");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Cliente paolaYJuan = crearCliente(inmobiliaria, asesor, "Paola y Juan", "7500000", "90000000");
        crearTasa(inmobiliaria, "Banco Ejemplo", co.predia.credito.TipoTasa.NO_VIS, "0.12");
        inmuebleEn(inmobiliaria, "FLO-1", "Apto Económico Floridablanca", "Floridablanca", "250000000");
        inmuebleEn(inmobiliaria, "FLO-2", "Apto Lujoso Floridablanca", "Floridablanca", "600000000");
        inmuebleEn(inmobiliaria, "BGA-1", "Apto Barato Bucaramanga", "Bucaramanga", "200000000");

        // Lo que calcula Java, sin pasar por el modelo: el asistente tiene que mostrar exactamente esto.
        CalculadoraPresupuesto.Presupuesto esperado = presupuestos
                .calcular(inmobiliaria.getId(), paolaYJuan.getId(), null, null, false).presupuesto();
        String topeEsperado = Formato.pesos(esperado.topeDeCompra());
        String cuotaEsperada = Formato.pesos(esperado.cuotaMaxima());

        AsistenteSimulado modelo = AsistenteSimulado.instalar(chatModel, List.of(
                new Llamada("calcularPresupuesto", "{\"cliente\": \"Paola y Juan\"}"),
                new Llamada("buscarInmuebles", "{\"paraCliente\": \"Paola y Juan\", \"ciudad\": \"Floridablanca\"}")),
                AsistenteApiTest::juntar, "no se usa");

        JsonNode r = preguntar(asesor, "¿Qué les alcanza a Paola y Juan en Floridablanca?", null);

        assertThat(r.get("herramientas")).extracting(JsonNode::asText)
                .containsExactly("calcularPresupuesto", "buscarInmuebles");
        String texto = r.get("respuesta").asText();
        assertThat(texto).contains(topeEsperado).contains(cuotaEsperada).contains("Banco Ejemplo");
        assertThat(texto).contains("tasa de ejemplo");
        // Solo el inmueble de Floridablanca que está dentro del tope; ni el caro ni el de otra ciudad.
        assertThat(texto).contains("Apto Económico Floridablanca")
                .doesNotContain("Apto Lujoso Floridablanca").doesNotContain("Apto Barato Bucaramanga");
        // Todas las cifras de la respuesta salieron de las herramientas.
        assertThat(r.get("cifrasSinRespaldo")).isEmpty();
        assertThat(r.get("aviso").isNull()).isTrue();
        assertThat(r.get("conversacionId").asText()).isNotBlank();
        assertThat(modelo.resultadosDeHerramientas()).hasSize(2);
    }

    @Test
    void simularElCreditoDevuelveLasCifrasDeLaCalculadora() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Crédito");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Paola y Juan", "7500000", "90000000");
        Inmueble apto = inmuebleEn(inmobiliaria, "CAN-1102", "Apto 1102 Cañaveral", "Floridablanca", "300000000");
        crearTasa(inmobiliaria, "Banco Ejemplo", co.predia.credito.TipoTasa.NO_VIS, "0.12");

        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("simularCredito",
                        "{\"inmueble\": \"apto 1102 de Cañaveral\", \"cliente\": \"Paola\", \"plazoAnios\": 15}")),
                AsistenteApiTest::juntar, "no se usa");

        JsonNode r = preguntar(asesor, "Simula el crédito de Paola y Juan para el 1102 a 15 años", null);

        var simulacion = simuladorReal(inmobiliaria, apto, cliente);
        String texto = r.get("respuesta").asText();
        assertThat(texto).contains(Formato.pesos(simulacion.cuotaMensual()))
                .contains(Formato.pesos(simulacion.montoFinanciado()))
                .contains(Formato.pesos(simulacion.totalAPagar()))
                .contains("la aprobación es del banco");
        assertThat(r.get("cifrasSinRespaldo")).isEmpty();
    }

    @Autowired co.predia.credito.reduccion.SimuladorReduccionService reduccion;

    @Test
    void laReduccionDeCreditoUsaLasCifrasDeLaCalculadoraYElPorcentajeSeInterpretaBien() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Reducción");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crearCliente(inmobiliaria, asesor, "Paola y Juan", "7500000", "90000000");

        // El modelo manda la tasa como 11 (porcentaje): la herramienta la convierte a 0,11.
        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("simularReduccionCredito",
                        "{\"saldo\": 150000000, \"tasaEaPorcentaje\": 11, \"cuotasPendientes\": 240, "
                                + "\"abono\": 500000, \"frecuencia\": \"mensual\", \"cliente\": \"Paola\"}")),
                AsistenteApiTest::juntar, "no se usa");

        JsonNode r = preguntar(asesor, "¿Cuánto ahorra Paola y Juan si abonan 500 mil al mes a su crédito de 150 millones?", null);

        var esperado = reduccion.simular(inmobiliaria.getId(), new co.predia.credito.reduccion.SimuladorReduccionService
                .Solicitud(null, null, new java.math.BigDecimal("150000000"), new java.math.BigDecimal("0.11"), 240,
                null, null, null, List.of(new java.math.BigDecimal("500000")), 1));
        var con = esperado.escenarios().getFirst();
        String texto = r.get("respuesta").asText();
        assertThat(con.cuotas()).isEqualTo(123);  // coincide con el simulador original
        assertThat(texto).contains(Formato.pesos(esperado.actual().cuotaInicial()))
                .contains(Formato.pesos(con.ahorroIntereses()))
                .contains(Formato.pesos(con.ingresoRequerido()))
                .contains("123 (10 años 3 meses)")
                .contains("11 % E.A.")
                .contains("no es una oferta ni una aprobación del banco");
        assertThat(r.get("herramientas").toString()).contains("simularReduccionCredito");
        assertThat(r.get("cifrasSinRespaldo")).isEmpty();
    }

    @Test
    void laReduccionConDatosInvalidosDevuelveUnErrorAlModeloEnVezDeFallar() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Reducción Error");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        // Una tasa de 1100 % (el modelo olvidó que va en porcentaje) se rechaza.
        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("simularReduccionCredito",
                        "{\"saldo\": 150000000, \"tasaEaPorcentaje\": 1100, \"cuotasPendientes\": 240, \"abono\": 500000}")),
                AsistenteApiTest::juntar, "no se usa");

        JsonNode r = preguntar(asesor, "Simula un abono de 500 mil", null);

        assertThat(r.get("respuesta").asText()).contains("error");
        assertThat(r.get("cifrasSinRespaldo")).isEmpty();
    }

    @Autowired co.predia.credito.SimuladorCreditoService simulador;

    private co.predia.credito.SimulacionCredito simuladorReal(Inmobiliaria inmobiliaria, Inmueble apto, Cliente cliente) {
        return simulador.simular(inmobiliaria.getId(), new co.predia.credito.SimuladorCreditoService.Solicitud(
                null, apto.getId(), cliente.getId(), 15, null, null));
    }

    // ------------------------------------------------------------------ cierre del negocio

    @Test
    void queLeFaltaAlDuenoDelAptoSeContestaConElEstadoQueCalculanLasReglas() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Cierre");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = inmuebleEn(inmobiliaria, "CAN-1102", "Apto 1102 Cañaveral", "Floridablanca", "300000000");
        Propietario dueno = crearPropietario(inmobiliaria, asesor, "Marta Ruiz", "marta@prueba.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inmobiliaria, asesor, apto, dueno, null);
        var esperado = estadoDocumental.calcular(negocio);

        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("estadoDocumentalNegocio",
                "{\"negocio\": \"apto 1102 de Cañaveral\"}")), AsistenteApiTest::juntar, "no se usa");

        JsonNode r = preguntar(asesor, "¿Qué le falta al dueño del apto 1102 de Cañaveral para firmar?", null);

        assertThat(r.get("herramientas")).extracting(JsonNode::asText).containsExactly("estadoDocumentalNegocio");
        String texto = r.get("respuesta").asText();
        assertThat(esperado.faltan()).isPositive();
        assertThat(texto).contains("Marta Ruiz").contains(esperado.semaforo())
                .contains("\"documentosQueFaltan\":" + esperado.faltan());
        esperado.faltantesParaElPropietario().forEach(item -> assertThat(texto).contains(item.nombre()));
        assertThat(r.get("cifrasSinRespaldo")).isEmpty();
    }

    @Test
    void elRecordatorioQuedaComoBorradorSugeridoYNoSeEnviaNada() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Recordatorio");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = inmuebleEn(inmobiliaria, "CAN-1102", "Apto 1102 Cañaveral", "Floridablanca", "300000000");
        Propietario dueno = crearPropietario(inmobiliaria, asesor, "Marta Ruiz", "marta@prueba.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inmobiliaria, asesor, apto, dueno, null);

        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("redactarRecordatorioPropietario",
                "{\"negocio\": \"" + negocio.getId() + "\"}")), AsistenteApiTest::juntar,
                "Texto que el validador rechaza, así que se usa la plantilla.");

        JsonNode r = preguntar(asesor, "Redáctale un recordatorio al dueño del 1102", null);

        assertThat(r.get("recordatoriosCreados")).hasSize(1);
        Long recordatorioId = r.get("recordatoriosCreados").get(0).asLong();
        var recordatorio = recordatorios.findById(recordatorioId).orElseThrow();
        assertThat(recordatorio.getEstado()).isEqualTo(EstadoRecordatorio.SUGERIDO);
        assertThat(recordatorio.getNegocioId()).isEqualTo(negocio.getId());
        assertThat(recordatorio.getEnviadoEn()).isNull();
        assertThat(r.get("respuesta").asText()).contains("\"enviado\":false").contains("aprobarlo");
        // Gobernanza: ni el asistente ni la herramienta envían correos; solo la aprobación del asesor.
        verify(correo, never()).send(any(jakarta.mail.internet.MimeMessage.class));
        verify(correo, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void sinDocumentosPendientesElRecordatorioNoSeCreaYElModeloRecibeElMotivo() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Sin Pendientes");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("redactarRecordatorioPropietario",
                "{\"negocio\": \"nada\"}")), AsistenteApiTest::juntar, "no se usa");

        JsonNode r = preguntar(asesor, "Recuérdale al dueño sus documentos", null);

        assertThat(r.get("recordatoriosCreados")).isEmpty();
        assertThat(r.get("respuesta").asText()).contains("error").contains("No encontré");
    }

    // ------------------------------------------------------------------ expedientes

    @Test
    void consultarExpedientesDevuelveLaRespuestaConSusCitasYLasExponeEnLaRespuesta() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Expedientes");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = inmuebleEn(inmobiliaria, "CAN-1102", "Apto 1102 Cañaveral", "Floridablanca", "300000000");
        Long documentoId = crearDocumento(inmobiliaria, apto, asesor, "promesa.pdf");
        insertarFragmento(inmobiliaria, documentoId, apto, 0, "CLÁUSULA TERCERA",
                "La venta incluye un parqueadero cubierto, identificado como P-27.", vector(0));
        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<?> textos = inv.getArgument(0);
            return textos.stream().map(t -> vector(0)).toList();
        });

        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("consultarExpedientes",
                        "{\"pregunta\": \"¿La venta incluye parqueadero?\", \"inmueble\": \"1102\"}")),
                AsistenteApiTest::juntar, "Sí, incluye un parqueadero cubierto [1].");

        JsonNode r = preguntar(asesor, "¿La venta del 1102 incluye parqueadero?", null);

        assertThat(r.get("herramientas")).extracting(JsonNode::asText).containsExactly("consultarExpedientes");
        assertThat(r.get("respuesta").asText()).contains("parqueadero cubierto [1]").contains("promesa.pdf");
        assertThat(r.get("citas")).hasSize(1);
        assertThat(r.get("citas").get(0).get("documentoId").asLong()).isEqualTo(documentoId);
        assertThat(r.get("citas").get(0).get("seccion").asText()).isEqualTo("CLÁUSULA TERCERA");
    }

    @Test
    void sinEvidenciaLaHerramientaLoDiceYElAsistenteNoInventa() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Sin Evidencia");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        inmuebleEn(inmobiliaria, "CAN-1102", "Apto 1102 Cañaveral", "Floridablanca", "300000000");

        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("consultarExpedientes",
                "{\"pregunta\": \"¿Incluye parqueadero?\", \"inmueble\": \"1102\"}")), AsistenteApiTest::juntar,
                "no se usa");

        JsonNode r = preguntar(asesor, "¿La venta del 1102 incluye parqueadero?", null);

        assertThat(r.get("respuesta").asText()).contains("\"sinEvidencia\":true")
                .contains("No tengo información suficiente");
        assertThat(r.get("citas")).isEmpty();
    }

    // ------------------------------------------------------------------ gobernanza y aislamiento

    @Test
    void lasHerramientasNuncaCruzanDatosDeOtraInmobiliariaAunqueElModeloPidaUnIdAjeno() throws Exception {
        iaDisponible();
        Inmobiliaria duena = crearInmobiliaria("Asistente Dueña");
        Inmobiliaria ajena = crearInmobiliaria("Asistente Ajena");
        Usuario asesorDuena = crearUsuario(duena, Rol.ASESOR);
        Usuario asesorAjeno = crearUsuario(ajena, Rol.ASESOR);
        Inmueble apto = inmuebleEn(duena, "CAN-1102", "Apto 1102 Cañaveral", "Floridablanca", "300000000");
        Propietario dueno = crearPropietario(duena, asesorDuena, "Marta Ruiz", "marta@prueba.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(duena, asesorDuena, apto, dueno, null);
        Cliente clienteAjeno = crearCliente(duena, asesorDuena, "Paola y Juan", "7500000", "90000000");

        AsistenteSimulado.instalar(chatModel, List.of(
                new Llamada("estadoDocumentalNegocio", "{\"negocio\": \"" + negocio.getId() + "\"}"),
                new Llamada("estadoDocumentalNegocio", "{\"negocio\": \"apto 1102 cañaveral\"}"),
                new Llamada("calcularPresupuesto", "{\"cliente\": \"" + clienteAjeno.getId() + "\"}"),
                new Llamada("calcularPresupuesto", "{\"cliente\": \"Paola y Juan\"}"),
                new Llamada("redactarRecordatorioPropietario", "{\"negocio\": \"" + negocio.getId() + "\"}")),
                AsistenteApiTest::juntar, "no se usa");

        JsonNode r = preguntar(asesorAjeno, "Muéstrame todo lo de la otra inmobiliaria", null);

        String texto = r.get("respuesta").asText();
        assertThat(texto).doesNotContain("Marta Ruiz").doesNotContain("Cañaveral").doesNotContain("7.500.000");
        assertThat(texto).contains("No encontré");
        assertThat(r.get("recordatoriosCreados")).isEmpty();
        Integer borradores = jdbc.queryForObject("SELECT count(*) FROM recordatorio WHERE inmobiliaria_id = ?",
                Integer.class, duena.getId());
        assertThat(borradores).isZero();
    }

    @Test
    void unaCifraQueElModeloEscribeSinHerramientaSeSenalaAlAsesor() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Guardia");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crearCliente(inmobiliaria, asesor, "Paola y Juan", "7500000", "90000000");
        crearTasa(inmobiliaria, "Banco Ejemplo", co.predia.credito.TipoTasa.NO_VIS, "0.12");

        AsistenteSimulado.instalar(chatModel, List.of(new Llamada("calcularPresupuesto",
                        "{\"cliente\": \"Paola y Juan\"}")),
                resultados -> "Pueden comprar hasta $999.123.456, seguro.", "no se usa");

        JsonNode r = preguntar(asesor, "¿Cuánto pueden comprar Paola y Juan?", null);

        assertThat(r.get("cifrasSinRespaldo")).extracting(JsonNode::asText).containsExactly("999.123.456");
        assertThat(r.get("aviso").asText()).contains("verifícalas");
    }

    @Test
    void laConversacionRecuerdaLosTurnosAnterioresYNoSeComparteEntreUsuarios() throws Exception {
        iaDisponible();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Memoria");
        Usuario laura = crearUsuario(inmobiliaria, Rol.ASESOR);
        Usuario otro = crearUsuario(inmobiliaria, Rol.ASESOR);
        AsistenteSimulado modelo = AsistenteSimulado.instalar(chatModel, List.of(), r -> "Entendido.", "no se usa");
        String conversacion = UUID.randomUUID().toString();

        preguntar(laura, "Hablemos de Paola y Juan", conversacion);
        preguntar(laura, "¿Y cuánto ganan?", conversacion);
        preguntar(otro, "Hola, soy otro asesor", conversacion);

        var peticiones = modelo.mensajesDeUsuarioPorPeticion();
        assertThat(peticiones).hasSize(3);
        assertThat(peticiones.get(0)).containsExactly("Hablemos de Paola y Juan");
        assertThat(peticiones.get(1)).containsExactly("Hablemos de Paola y Juan", "¿Y cuánto ganan?");
        // El otro usuario usó el mismo id de conversación, pero no ve lo que habló Laura.
        assertThat(peticiones.get(2)).containsExactly("Hola, soy otro asesor");
    }

    @Test
    void siElModeloFallaElAsesorRecibeUnErrorLimpioSinDetallesDelProveedor() throws Exception {
        iaDisponible();
        when(chatModel.getDefaultOptions()).thenReturn(
                org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 429 clave sk-secreta-123"));
        Inmobiliaria inmobiliaria = crearInmobiliaria("Asistente Falla");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/asistente/mensajes").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\": \"Hola\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sk-secreta"))));
    }

    // ------------------------------------------------------------------ apoyo de datos

    private Long crearDocumento(Inmobiliaria inmobiliaria, Inmueble inmueble, Usuario usuario, String nombre) {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        return jdbc.queryForObject("""
                INSERT INTO documento (inmobiliaria_id, inmueble_id, nombre_archivo, hash_sha256, tamano_bytes,
                                       subido_por, estado_indexacion)
                VALUES (?, ?, ?, ?, 1000, ?, 'INDEXADO') RETURNING id
                """, Long.class, inmobiliaria.getId(), inmueble.getId(), nombre, hash, usuario.getId());
    }

    private void insertarFragmento(Inmobiliaria inmobiliaria, Long documentoId, Inmueble inmueble, int orden,
                                   String seccion, String texto, float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(i > 0 ? "," : "").append(embedding[i]);
        }
        jdbc.update("""
                INSERT INTO fragmento (inmobiliaria_id, documento_id, inmueble_id, orden, pagina_inicio, pagina_fin,
                                       seccion, texto, tokens, es_ocr, embedding, embedding_modelo)
                VALUES (?, ?, ?, ?, 1, 1, ?, ?, 20, false, ?::vector, 'pruebas')
                """, inmobiliaria.getId(), documentoId, inmueble.getId(), orden, seccion, texto,
                sb.append(']').toString());
    }
}
