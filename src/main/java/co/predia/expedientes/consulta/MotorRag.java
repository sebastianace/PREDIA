package co.predia.expedientes.consulta;

import co.predia.comun.ia.EstadoIa;
import co.predia.comun.ia.ProveedorEmbeddings;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responde preguntas sobre los expedientes con evidencia recuperada (RAG híbrido). El modelo solo
 * recibe los fragmentos ya filtrados por inmobiliaria y permisos; nunca el PDF completo. Si no hay
 * evidencia suficiente, responde que no lo sabe sin llamar al modelo. Sin IA, muestra la evidencia
 * encontrada sin redactar respuesta (modo manual).
 */
@Service
public class MotorRag {

    private static final Logger log = LoggerFactory.getLogger(MotorRag.class);
    private static final Pattern PATRON_CITA = Pattern.compile("\\[(\\d+)]");
    private static final String SIN_EVIDENCIA =
            "No tengo información suficiente en los documentos indexados para responder esa pregunta.";
    private static final String ERROR_IA =
            "No fue posible redactar la respuesta en este momento. Esta es la evidencia encontrada.";

    private static final String INSTRUCCION_SISTEMA = """
            Respondes preguntas sobre expedientes inmobiliarios en Colombia usando SOLO los fragmentos \
            de evidencia numerados que te entregan a continuación ([1], [2]…), nunca el documento \
            completo ni lo que sepas de memoria. Cada afirmación debe citar el número del fragmento que \
            la respalda, por ejemplo: "El canon es de $1.800.000 [2]." Si la evidencia no alcanza para \
            responder con certeza, dilo explícitamente: "No tengo información suficiente para responder \
            eso." No inventes datos que no estén en los fragmentos.""";

    private final BusquedaHibrida busqueda;
    private final EstadoIa estadoIa;
    private final ProveedorEmbeddings proveedorEmbeddings;
    private final ChatClient chatClient;
    private final ParametrosInmobiliariaRepository parametros;
    private final ConsultaRagRepository consultas;

    public MotorRag(BusquedaHibrida busqueda, EstadoIa estadoIa, ProveedorEmbeddings proveedorEmbeddings,
                    ChatClient.Builder chatClientBuilder, ParametrosInmobiliariaRepository parametros,
                    ConsultaRagRepository consultas) {
        this.busqueda = busqueda;
        this.estadoIa = estadoIa;
        this.proveedorEmbeddings = proveedorEmbeddings;
        this.chatClient = chatClientBuilder.build();
        this.parametros = parametros;
        this.consultas = consultas;
    }

    @Transactional
    public RespuestaConsulta responder(UsuarioActual usuario, String pregunta, Long inmuebleId) {
        boolean iaDisponible = estadoIa.disponible();
        float[] embeddingPregunta = iaDisponible ? proveedorEmbeddings.generar(List.of(pregunta)).get(0) : null;

        // La pregunta se vectoriza con el modelo actual y solo se compara con fragmentos indexados con ese mismo modelo.
        String modelo = iaDisponible ? proveedorEmbeddings.modelo() : null;
        ResultadoHibrido resultado = busqueda.buscar(usuario.inmobiliariaId(), usuario.rol().name(), inmuebleId,
                pregunta, modelo, embeddingPregunta);
        String aviso = avisoVectoresAntiguos(resultado, modelo);

        if (resultado.evidencias().isEmpty() || !hayEvidenciaSuficiente(usuario.inmobiliariaId(), resultado)) {
            guardar(usuario, inmuebleId, pregunta, null, resultado.evidencias(), true, !iaDisponible);
            return new RespuestaConsulta(SIN_EVIDENCIA, List.of(), true, !iaDisponible, aviso);
        }

        if (!iaDisponible) {
            List<Cita> citas = numerarTodas(resultado.evidencias());
            guardar(usuario, inmuebleId, pregunta, null, resultado.evidencias(), false, true);
            return new RespuestaConsulta(null, citas, false, true, aviso);
        }

        String respuesta;
        List<Cita> citas;
        try {
            respuesta = chatClient.prompt().system(INSTRUCCION_SISTEMA)
                    .user(construirMensaje(pregunta, resultado.evidencias()))
                    .call().content();
            citas = extraerCitas(respuesta, resultado.evidencias());
        } catch (Exception e) {
            log.warn("No se pudo generar la respuesta con IA: {}", e.getMessage());
            respuesta = ERROR_IA;
            citas = numerarTodas(resultado.evidencias());
        }

        guardar(usuario, inmuebleId, pregunta, respuesta, resultado.evidencias(), false, false);
        return new RespuestaConsulta(respuesta, citas, false, false, aviso);
    }

    private static String avisoVectoresAntiguos(ResultadoHibrido resultado, String modelo) {
        int n = resultado.fragmentosConOtroModelo();
        if (n == 0) {
            return null;
        }
        return "%d fragmento%s se indexó con otro modelo de embeddings y no entra en la búsqueda por significado "
                .formatted(n, n == 1 ? "" : "s") + "(solo se encuentra por palabras). Un administrador puede regenerar los "
                + "vectores con el modelo actual (" + modelo + ").";
    }

    private boolean hayEvidenciaSuficiente(Long inmobiliariaId, ResultadoHibrido resultado) {
        BigDecimal umbral = parametros.findById(inmobiliariaId)
                .map(ParametrosInmobiliaria::getUmbralSimilitudRag)
                .orElse(new BigDecimal("0.300"));
        return resultado.mejorSimilitudVectorial() >= umbral.doubleValue() || resultado.hayCoincidenciaTextual();
    }

    private static String construirMensaje(String pregunta, List<EvidenciaFragmento> evidencias) {
        StringBuilder contexto = new StringBuilder();
        for (int i = 0; i < evidencias.size(); i++) {
            EvidenciaFragmento e = evidencias.get(i);
            String seccion = e.seccion() != null && !e.seccion().isBlank() ? ", sección: " + e.seccion() : "";
            contexto.append("[%d] (%s, página %d-%d%s):\n%s\n\n"
                    .formatted(i + 1, e.nombreArchivo(), e.paginaInicio(), e.paginaFin(), seccion, e.texto()));
        }
        return "Evidencia recuperada:\n\n" + contexto + "\nPregunta del asesor: " + pregunta;
    }

    private static List<Cita> extraerCitas(String respuesta, List<EvidenciaFragmento> evidencias) {
        if (respuesta == null) {
            return numerarTodas(evidencias);
        }
        Matcher m = PATRON_CITA.matcher(respuesta);
        Set<Integer> numeros = new LinkedHashSet<>();
        while (m.find()) {
            numeros.add(Integer.parseInt(m.group(1)));
        }
        List<Cita> citas = new ArrayList<>();
        for (int numero : numeros) {
            // Se descartan los números que el modelo haya citado pero que no correspondan a evidencia real.
            if (numero >= 1 && numero <= evidencias.size()) {
                citas.add(Cita.desde(numero, evidencias.get(numero - 1)));
            }
        }
        return citas.isEmpty() ? numerarTodas(evidencias) : citas;
    }

    private static List<Cita> numerarTodas(List<EvidenciaFragmento> evidencias) {
        List<Cita> citas = new ArrayList<>(evidencias.size());
        for (int i = 0; i < evidencias.size(); i++) {
            citas.add(Cita.desde(i + 1, evidencias.get(i)));
        }
        return citas;
    }

    private void guardar(UsuarioActual usuario, Long inmuebleId, String pregunta, String respuesta,
                         List<EvidenciaFragmento> evidencias, boolean sinEvidencia, boolean modoManual) {
        List<Long> ids = evidencias.stream().map(EvidenciaFragmento::fragmentoId).toList();
        consultas.save(new ConsultaRag(usuario.inmobiliariaId(), usuario.id(), inmuebleId, pregunta, respuesta, ids,
                sinEvidencia, modoManual, estadoIa.modeloChat()));
    }
}
