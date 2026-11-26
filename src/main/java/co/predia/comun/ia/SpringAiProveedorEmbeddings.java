package co.predia.comun.ia;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementación con Spring AI: sirve para el proveedor activo (OpenAI u Ollama). Ajusta cada vector a la
 * dimensión de la base (ver {@link AjusteDimension}).
 */
@Service
public class SpringAiProveedorEmbeddings implements ProveedorEmbeddings {

    private final EmbeddingModel modelo;
    private final EstadoIa estadoIa;

    public SpringAiProveedorEmbeddings(EmbeddingModel modelo, EstadoIa estadoIa) {
        this.modelo = modelo;
        this.estadoIa = estadoIa;
    }

    @Override
    public List<float[]> generar(List<String> textos) {
        return modelo.embed(textos).stream().map(AjusteDimension::ajustar).toList();
    }

    @Override
    public String modelo() {
        return estadoIa.modeloEmbeddings();
    }
}
