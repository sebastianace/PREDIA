package co.predia.comun.ia;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/** Implementación con Spring AI / OpenAI (text-embedding-3-small por defecto). */
@Service
public class OpenAiProveedorEmbeddings implements ProveedorEmbeddings {

    private final EmbeddingModel modelo;
    private final EstadoIa estadoIa;

    public OpenAiProveedorEmbeddings(EmbeddingModel modelo, EstadoIa estadoIa) {
        this.modelo = modelo;
        this.estadoIa = estadoIa;
    }

    @Override
    public List<float[]> generar(List<String> textos) {
        return modelo.embed(textos);
    }

    @Override
    public String modelo() {
        return estadoIa.modeloEmbeddings();
    }
}
