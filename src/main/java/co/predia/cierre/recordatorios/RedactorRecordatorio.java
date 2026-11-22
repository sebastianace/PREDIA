package co.predia.cierre.recordatorios;

import co.predia.comun.ia.EstadoIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redacta el recordatorio. La IA recibe SOLO la lista de pendientes que entregó el motor de reglas, y su texto se
 * valida en Java (menciona todo lo que falta, no menciona nada más, no trae números nuevos). Si la validación
 * falla, o no hay IA, se usa una plantilla determinista. El asesor revisa y aprueba antes de enviar nada.
 */
@Component
public class RedactorRecordatorio {

    private static final Logger log = LoggerFactory.getLogger(RedactorRecordatorio.class);
    private static final int LARGO_MAXIMO_CUERPO = 3000;

    private static final String INSTRUCCION = """
            Redactas recordatorios cordiales y claros, en español colombiano y tratando de "usted", para el \
            propietario que vende un inmueble con una inmobiliaria. Recibirás la lista EXACTA de documentos \
            pendientes, con el estado de cada uno. Reglas: menciona todos los documentos de la lista, con su \
            nombre tal cual; copia las fechas y los plazos exactamente como están escritos en la lista, sin \
            cambiarlos ni calcular otros; no menciones ningún documento que no esté en la lista; no inventes \
            requisitos, plazos ni valores; no prometas nada; no uses números que no estén en la lista. Sé amable \
            y breve, y firma con el nombre del asesor.""";

    private final ChatClient chatClient;
    private final EstadoIa estadoIa;

    public RedactorRecordatorio(ChatClient.Builder builder, EstadoIa estadoIa) {
        this.chatClient = builder.build();
        this.estadoIa = estadoIa;
    }

    public record Entrada(String propietario, String inmueble, String asesor, String inmobiliaria,
                          List<ItemRecordatorio> items, List<ValidadorTextoRecordatorio.Documento> pedidos,
                          List<ValidadorTextoRecordatorio.NoPedido> noPedidos) {
    }

    public record Borrador(String asunto, String cuerpo, OrigenTexto origen) {
    }

    /** Lo que estructura el modelo: un asunto y un cuerpo. */
    record RecordatorioIa(String asunto, String cuerpo) {
    }

    public Borrador redactar(Entrada e) {
        if (estadoIa.disponible()) {
            try {
                RecordatorioIa ia = chatClient.prompt().system(INSTRUCCION).user(mensaje(e)).call()
                        .entity(RecordatorioIa.class);
                if (ia != null && ia.asunto() != null && ia.cuerpo() != null) {
                    String completo = ia.asunto() + "\n" + ia.cuerpo();
                    var resultado = ValidadorTextoRecordatorio.validar(completo, e.pedidos(), e.noPedidos(),
                            numerosPermitidos(e));
                    if (resultado.esValido() && ia.cuerpo().length() <= LARGO_MAXIMO_CUERPO) {
                        return new Borrador(ia.asunto().strip(), ia.cuerpo().strip(), OrigenTexto.IA);
                    }
                    log.info("El texto de la IA no pasó la validación; se usa la plantilla ({} problema(s)).",
                            resultado.problemas().size());
                }
            } catch (Exception ex) {
                log.warn("No se pudo redactar el recordatorio con IA; se usa la plantilla: {}", ex.getMessage());
            }
        }
        return plantilla(e);
    }

    /** Los únicos números permitidos son los que ya están en la lista (fechas, plazos) y en el nombre del inmueble. */
    public static Set<String> numerosPermitidos(Entrada e) {
        List<String> frases = new ArrayList<>();
        e.items().forEach(i -> {
            frases.add(i.nombre());
            frases.add(i.detalle());
        });
        frases.add(e.inmueble());
        return ValidadorTextoRecordatorio.numerosDe(frases);
    }

    static Borrador plantilla(Entrada e) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("Hola, ").append(e.propietario()).append(":\n\n")
                .append("Le escribimos de ").append(e.inmobiliaria()).append(" sobre la venta de ")
                .append(e.inmueble()).append(". Para poder avanzar hacia la firma, todavía necesitamos los ")
                .append("siguientes documentos:\n\n");
        for (ItemRecordatorio i : e.items()) {
            cuerpo.append("- ").append(i.nombre()).append(": ").append(i.detalle()).append('\n');
        }
        cuerpo.append("\nCuando los tenga, puede enviárnoslos respondiendo a este correo o entregándolos a su asesor. ")
                .append("Si ya los envió, por favor ignore este mensaje.\n\n")
                .append("Gracias por su colaboración.\n")
                .append(e.asesor()).append('\n')
                .append(e.inmobiliaria());
        return new Borrador("Documentos pendientes para la venta de " + e.inmueble(), cuerpo.toString(),
                OrigenTexto.PLANTILLA);
    }

    private static String mensaje(Entrada e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Propietario: ").append(e.propietario()).append('\n')
                .append("Inmueble: ").append(e.inmueble()).append('\n')
                .append("Asesor: ").append(e.asesor()).append('\n')
                .append("Inmobiliaria: ").append(e.inmobiliaria()).append("\n\nDocumentos pendientes:\n");
        e.items().forEach(i -> sb.append("- ").append(i.nombre()).append(": ").append(i.detalle()).append('\n'));
        return sb.toString();
    }
}
