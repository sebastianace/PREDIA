package co.predia.comun.ia;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.beans.factory.config.BeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/** El ajuste de «pensar» de Ollama: apagado por defecto, y solo toca las propiedades del chat de Ollama. */
class AjustesOllamaTest {

    @Test
    void sinPensarDesactivaElModoDePensarDelModelo() {
        OllamaChatProperties propiedades = new OllamaChatProperties();

        AjustesOllama.modoDePensarDeOllama(false).postProcessAfterInitialization(propiedades, "propiedades");

        assertThat(propiedades.getOptions().getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
    }

    @Test
    void conPensarLoDejaActivado() {
        OllamaChatProperties propiedades = new OllamaChatProperties();

        AjustesOllama.modoDePensarDeOllama(true).postProcessAfterInitialization(propiedades, "propiedades");

        assertThat(propiedades.getOptions().getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.ENABLED);
    }

    @Test
    void cualquierOtroBeanQuedaIgual() {
        BeanPostProcessor ajuste = AjustesOllama.modoDePensarDeOllama(false);
        Object otro = new Object();

        assertThat(ajuste.postProcessAfterInitialization(otro, "otro")).isSameAs(otro);
    }
}
