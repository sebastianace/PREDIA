package co.predia.comun.ia;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué proveedor de IA hay y si está disponible, según la configuración. Sin Spring ni base de datos. */
class EstadoIaTest {

    @Test
    void sinNingunaConfiguracionEsOpenAiYSinClaveEstaEnModoManual() {
        EstadoIa estado = new EstadoIa(new MockEnvironment());

        assertThat(estado.proveedor()).isEqualTo("openai");
        assertThat(estado.disponible()).isFalse();
    }

    @Test
    void conOpenAiYUnaClaveRealLaIaEstaDisponible() {
        EstadoIa estado = new EstadoIa(new MockEnvironment()
                .withProperty("spring.ai.openai.api-key", "sk-algo")
                .withProperty("spring.ai.openai.chat.options.model", "gpt-5.4-mini")
                .withProperty("spring.ai.openai.embedding.options.model", "text-embedding-3-small"));

        assertThat(estado.disponible()).isTrue();
        assertThat(estado.modeloChat()).isEqualTo("gpt-5.4-mini");
        assertThat(estado.modeloEmbeddings()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void conOpenAiElMarcadorDeSinClaveNoCuentaComoClave() {
        EstadoIa estado = new EstadoIa(new MockEnvironment().withProperty("spring.ai.openai.api-key", "sin-configurar"));

        assertThat(estado.disponible()).isFalse();
    }

    @Test
    void conOllamaNoHaceFaltaClaveYSeUsanSusModelos() {
        EstadoIa estado = new EstadoIa(new MockEnvironment()
                .withProperty("spring.ai.model.chat", "ollama")
                .withProperty("spring.ai.model.embedding", "ollama")
                .withProperty("spring.ai.openai.api-key", "sin-configurar")
                .withProperty("spring.ai.ollama.chat.options.model", "qwen3:8b")
                .withProperty("spring.ai.ollama.embedding.options.model", "bge-m3"));

        assertThat(estado.proveedor()).isEqualTo("ollama");
        assertThat(estado.disponible()).isTrue();
        assertThat(estado.modeloChat()).isEqualTo("qwen3:8b");
        assertThat(estado.modeloEmbeddings()).isEqualTo("bge-m3");
    }

    @Test
    void unProveedorDesconocidoOMezcladoImpideArrancar() {
        assertThatThrownBy(() -> new EstadoIa(new MockEnvironment().withProperty("spring.ai.model.chat", "gemini")
                .withProperty("spring.ai.model.embedding", "gemini")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PREDIA_IA_PROVEEDOR");
        assertThatThrownBy(() -> new EstadoIa(new MockEnvironment().withProperty("spring.ai.model.chat", "ollama")
                .withProperty("spring.ai.model.embedding", "openai")))
                .isInstanceOf(IllegalStateException.class);
    }
}
