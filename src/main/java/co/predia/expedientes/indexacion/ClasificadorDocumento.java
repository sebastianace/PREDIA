package co.predia.expedientes.indexacion;

import co.predia.comun.ia.EstadoIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Clasifica un documento (tipo y fecha de expedición) a partir solo de sus primeros fragmentos
 * ya recuperados —nunca del PDF completo—. El resultado queda SUGERIDO: nunca se guarda como
 * confirmado por su cuenta. Si la IA no está disponible o falla, simplemente no hay sugerencia.
 */
@Component
public class ClasificadorDocumento {

    private static final Logger log = LoggerFactory.getLogger(ClasificadorDocumento.class);

    private static final String INSTRUCCION_SISTEMA = """
            Clasificas documentos de expedientes inmobiliarios en Colombia. Lees solo el fragmento de \
            texto que te entregan (nunca el documento completo) y respondes con el tipo que mejor \
            encaja y, si el propio texto lo indica, su fecha de expedición. Si no reconoces el tipo con \
            claridad, responde OTRO con una confianza baja. No inventes una fecha que no esté escrita \
            en el texto; en ese caso, deja fechaExpedicion vacía.""";

    private final ChatClient chatClient;
    private final EstadoIa estadoIa;

    public ClasificadorDocumento(ChatClient.Builder builder, EstadoIa estadoIa) {
        this.chatClient = builder.build();
        this.estadoIa = estadoIa;
    }

    public Optional<ClasificacionSugerida> clasificar(List<String> primerosFragmentos) {
        if (!estadoIa.disponible() || primerosFragmentos.isEmpty()) {
            return Optional.empty();
        }
        try {
            String contenido = String.join("\n\n---\n\n", primerosFragmentos);
            ClasificacionSugerida resultado = chatClient.prompt()
                    .system(INSTRUCCION_SISTEMA)
                    .user("Clasifica este documento a partir de sus primeros fragmentos:\n\n" + contenido)
                    .call()
                    .entity(ClasificacionSugerida.class);
            return Optional.ofNullable(resultado);
        } catch (Exception e) {
            // Una clasificación fallida no debe tumbar la indexación: el documento queda sin clasificar.
            log.warn("No se pudo clasificar el documento con IA: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
