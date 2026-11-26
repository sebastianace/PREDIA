package co.predia.comun.ia;

import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ajuste solo para Ollama: los modelos que «piensan» antes de responder (como qwen3) gastan diez veces más tokens
 * y la respuesta tarda un minuto. Spring no puede enlazar ese valor desde application.yml, así que se fija al crear
 * las propiedades del chat. Con {@code OLLAMA_PENSAR=true} se vuelve al comportamiento del modelo.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = EstadoIa.OLLAMA)
class AjustesOllama {

    @Bean
    static BeanPostProcessor modoDePensarDeOllama(@Value("${predia.ia.ollama.pensar:false}") boolean pensar) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (bean instanceof OllamaChatProperties propiedades) {
                    propiedades.getOptions()
                            .setThinkOption(pensar ? ThinkOption.ThinkBoolean.ENABLED : ThinkOption.ThinkBoolean.DISABLED);
                }
                return bean;
            }
        };
    }
}
