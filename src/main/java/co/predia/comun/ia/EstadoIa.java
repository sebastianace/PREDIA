package co.predia.comun.ia;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Indica si la IA está disponible y con qué proveedor. Con OpenAI, sin API key Predia sigue funcionando en modo
 * manual: formularios, búsqueda por texto y plantillas en lugar de redacción con IA. Con Ollama (modelos locales)
 * no hay clave: la IA se da por disponible y, si el servidor está apagado, cada uso falla con el aviso de siempre
 * (502 en el asistente, reintentos en la indexación).
 */
@Component
public class EstadoIa {

    static final String SIN_CLAVE = "sin-configurar";
    public static final String OPENAI = "openai";
    public static final String OLLAMA = "ollama";

    private final String proveedor;
    private final boolean disponible;
    private final String modeloChat;
    private final String modeloEmbeddings;

    public EstadoIa(Environment entorno) {
        this.proveedor = entorno.getProperty("spring.ai.model.chat", OPENAI).trim().toLowerCase();
        String proveedorEmbeddings = entorno.getProperty("spring.ai.model.embedding", OPENAI).trim().toLowerCase();
        if (!proveedor.equals(proveedorEmbeddings) || !(proveedor.equals(OPENAI) || proveedor.equals(OLLAMA))) {
            throw new IllegalStateException("PREDIA_IA_PROVEEDOR debe ser «openai» u «ollama» (chat=" + proveedor
                    + ", embeddings=" + proveedorEmbeddings + ").");
        }
        if (proveedor.equals(OLLAMA)) {
            this.disponible = true;
            this.modeloChat = entorno.getProperty("spring.ai.ollama.chat.options.model", "");
            this.modeloEmbeddings = entorno.getProperty("spring.ai.ollama.embedding.options.model", "");
        } else {
            String clave = entorno.getProperty("spring.ai.openai.api-key", SIN_CLAVE);
            this.disponible = clave != null && !clave.isBlank() && !SIN_CLAVE.equals(clave);
            this.modeloChat = entorno.getProperty("spring.ai.openai.chat.options.model", "");
            this.modeloEmbeddings = entorno.getProperty("spring.ai.openai.embedding.options.model", "");
        }
    }

    public boolean disponible() {
        return disponible;
    }

    public String proveedor() {
        return proveedor;
    }

    public String modeloChat() {
        return modeloChat;
    }

    public String modeloEmbeddings() {
        return modeloEmbeddings;
    }
}
