package co.predia.soporte;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Un modelo de lenguaje de mentira para probar el asistente SIN llamar a OpenAI. Hace lo que haría el modelo real:
 * ejecuta las herramientas que le llegan en la petición (las reales de Predia, con la base de datos real) con los
 * argumentos del guion y redacta la respuesta final con lo que ellas devolvieron.
 *
 * <p>Una petición sin herramientas (la que hace el motor RAG por dentro) recibe {@code textoRag}.
 */
public final class AsistenteSimulado {

    /** Una llamada a una herramienta, con sus argumentos como JSON. */
    public record Llamada(String herramienta, String argumentosJson) {
    }

    private final List<List<String>> mensajesDeUsuario = new ArrayList<>();
    private final List<String> resultadosDeHerramientas = new ArrayList<>();

    private AsistenteSimulado() {
    }

    /** Los textos de usuario que recibió el modelo en cada petición del asistente (incluye el historial). */
    public List<List<String>> mensajesDeUsuarioPorPeticion() {
        return mensajesDeUsuario;
    }

    public List<String> resultadosDeHerramientas() {
        return resultadosDeHerramientas;
    }

    /**
     * @param redactor recibe lo que devolvió cada herramienta (JSON) y devuelve el texto final del modelo
     */
    public static AsistenteSimulado instalar(ChatModel chatModel, List<Llamada> guion,
                                             Function<List<String>, String> redactor, String textoRag) {
        AsistenteSimulado simulado = new AsistenteSimulado();
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocacion -> {
            Prompt prompt = invocacion.getArgument(0);
            List<ToolCallback> herramientas = prompt.getOptions() instanceof ToolCallingChatOptions o
                    ? o.getToolCallbacks() : List.of();
            if (herramientas.isEmpty()) {
                return respuesta(textoRag);
            }
            simulado.mensajesDeUsuario.add(prompt.getInstructions().stream()
                    .filter(m -> m.getMessageType() == MessageType.USER).map(Message::getText).toList());
            Map<String, ToolCallback> porNombre = herramientas.stream().collect(
                    Collectors.toMap(h -> h.getToolDefinition().name(), h -> h));
            List<String> resultados = new ArrayList<>();
            for (Llamada llamada : guion) {
                ToolCallback herramienta = porNombre.get(llamada.herramienta());
                if (herramienta == null) {
                    throw new IllegalStateException("El asistente no ofrece la herramienta " + llamada.herramienta());
                }
                resultados.add(herramienta.call(llamada.argumentosJson()));
            }
            simulado.resultadosDeHerramientas.addAll(resultados);
            return respuesta(redactor.apply(resultados));
        });
        return simulado;
    }

    private static ChatResponse respuesta(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }
}
