package co.predia.comun.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Indica si la IA está disponible. Sin API key Predia sigue funcionando en modo manual:
 * formularios, búsqueda por texto y plantillas en lugar de redacción con IA.
 */
@Component
public class EstadoIa {

    static final String SIN_CLAVE = "sin-configurar";

    private final boolean disponible;
    private final String modeloChat;
    private final String modeloEmbeddings;

    public EstadoIa(@Value("${spring.ai.openai.api-key:" + SIN_CLAVE + "}") String apiKey,
                    @Value("${spring.ai.openai.chat.options.model:}") String modeloChat,
                    @Value("${spring.ai.openai.embedding.options.model:}") String modeloEmbeddings) {
        this.disponible = apiKey != null && !apiKey.isBlank() && !SIN_CLAVE.equals(apiKey);
        this.modeloChat = modeloChat;
        this.modeloEmbeddings = modeloEmbeddings;
    }

    public boolean disponible() {
        return disponible;
    }

    public String modeloChat() {
        return modeloChat;
    }

    public String modeloEmbeddings() {
        return modeloEmbeddings;
    }
}
