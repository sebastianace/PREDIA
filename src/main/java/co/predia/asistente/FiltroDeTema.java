package co.predia.asistente;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decide, ANTES de llamar al asistente, si un mensaje trata de la inmobiliaria o es de otro tema (deportes, famosos,
 * clima, recetas, política...). Lo hace una llamada aparte con un prompt corto y una sola palabra de respuesta, no
 * la instrucción larga del asistente: un modelo pequeño (8B) que ya conversó sobre un negocio tiende a seguir
 * contestando lo que le pregunten, y la regla «solo temas de la inmobiliaria» del prompt principal no alcanza.
 *
 * <p>Si el mensaje es ajeno, el asistente responde un aviso fijo escrito en Java: no se llama al modelo con
 * herramientas y el mensaje no entra en el historial. Si el filtro falla o duda, el mensaje pasa (nunca se bloquea
 * una pregunta legítima por un error); la instrucción del asistente y la guardia de cifras siguen ahí.
 */
@Component
public class FiltroDeTema {

    private static final Logger log = LoggerFactory.getLogger(FiltroDeTema.class);
    private static final Pattern PENSAMIENTO = Pattern.compile("(?s)<think>.*?</think>");
    private static final int LARGO_CONTEXTO = 300;

    static final String INSTRUCCION = """
            Clasificas mensajes que un asesor escribe en el asistente de una inmobiliaria de Colombia. Responde \
            ÚNICAMENTE con una palabra: INMOBILIARIA o AJENO.

            INMOBILIARIA es todo lo del trabajo en la inmobiliaria: inmuebles en venta o arriendo, precios, \
            clientes compradores, propietarios o vendedores, contratos, escrituras, certificados y otros \
            documentos, expedientes, presupuesto de un cliente, créditos hipotecarios y sus simulaciones, abonos, \
            cierre de negocios, requisitos, recordatorios y la fecha de firma. También son INMOBILIARIA los saludos, \
            las gracias, las preguntas sobre qué puede hacer el asistente y las respuestas cortas que continúan una \
            conversación sobre esos temas (por ejemplo «y el otro?», «sí», «el de Cabecera»).

            AJENO es todo lo demás: deportes y partidos, famosos, edades o biografías de personas, clima, noticias, \
            política, recetas, chistes, poemas o cuentos, programación, matemáticas generales, ciencia, historia y \
            cultura general, salud, viajes o cualquier cosa que no sea del trabajo en la inmobiliaria.

            Ejemplos: «cuántos años tiene messi» → AJENO. «cómo quedó el bucaramanga ayer» → AJENO. «dame una receta \
            de sancocho» → AJENO. «quién es el presidente» → AJENO. «qué le falta al dueño del apto 1102» → \
            INMOBILIARIA. «qué les alcanza a Paola y Juan» → INMOBILIARIA. «hola» → INMOBILIARIA. «simula un \
            crédito a 15 años» → INMOBILIARIA.""";

    private final ChatClient chatClient;

    public FiltroDeTema(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** {@code true} si el mensaje es de otro tema y no debe llegar al asistente. */
    public boolean esAjeno(String mensaje, List<Message> historial) {
        try {
            String respuesta = chatClient.prompt().system(INSTRUCCION).user(entrada(mensaje, historial)).call().content();
            return interpretar(respuesta);
        } catch (RuntimeException e) {
            log.warn("No se pudo clasificar el tema del mensaje; pasa al asistente: {}", e.getMessage());
            return false;
        }
    }

    /** El mensaje y, como contexto, lo último que dijo el asistente (una respuesta corta depende de eso). */
    static String entrada(String mensaje, List<Message> historial) {
        String contexto = "";
        for (int i = historial.size() - 1; i >= 0; i--) {
            if (historial.get(i) instanceof AssistantMessage a && a.getText() != null) {
                String texto = a.getText().strip().replaceAll("\\s+", " ");
                contexto = "Lo último que dijo el asistente: «"
                        + (texto.length() > LARGO_CONTEXTO ? texto.substring(0, LARGO_CONTEXTO) + "…" : texto) + "»\n";
                break;
            }
        }
        return contexto + "Mensaje del asesor: «" + mensaje.strip() + "»\nClasificación:";
    }

    /** Solo es ajeno si la respuesta dice AJENO y no INMOBILIARIA; ante cualquier duda, el mensaje pasa. */
    static boolean interpretar(String respuesta) {
        if (respuesta == null) {
            return false;
        }
        String limpia = PENSAMIENTO.matcher(respuesta).replaceAll("").toUpperCase(Locale.ROOT);
        return limpia.contains("AJENO") && !limpia.contains("INMOBILIARIA");
    }
}
