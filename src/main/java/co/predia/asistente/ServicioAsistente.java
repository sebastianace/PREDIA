package co.predia.asistente;

import co.predia.comun.ia.EstadoIa;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.FalloDeIaException;
import co.predia.comun.web.IaNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import co.predia.cierre.reglas.DescriptorItem;

/**
 * El asistente: el modelo conversa con el asesor y decide qué herramienta usar; las herramientas entregan los
 * datos y las cifras (de los servicios de Java). El modelo solo redacta con lo que ellas devuelven.
 *
 * <p>La conversación se guarda en memoria, atada al usuario y a su inmobiliaria (otro usuario no puede leerla
 * aunque conozca su id), con un tope de conversaciones y de mensajes; al reiniciar el servidor se pierde.
 */
@Service
public class ServicioAsistente {

    private static final Logger log = LoggerFactory.getLogger(ServicioAsistente.class);

    static final int MAXIMO_CONVERSACIONES = 500;
    static final int MAXIMO_MENSAJES_POR_CONVERSACION = 20;

    static final String SIN_IA =
            "El asistente está deshabilitado porque la IA no está disponible. El resto de Predia funciona con normalidad.";
    static final String FALLO_IA = "El asistente no pudo responder en este momento. Intenta de nuevo en unos minutos.";

    private static final String INSTRUCCION_SISTEMA = """
            Eres el asistente de Predia para asesores de una inmobiliaria en Colombia. Respondes en español \
            colombiano, claro y breve, tratando al asesor de tú.

            Reglas que nunca rompes:
            1. Todo dato sobre inmuebles, clientes, negocios, documentos o expedientes sale de las herramientas. \
            Si una herramienta puede responder, úsala; no respondas de memoria ni supongas.
            2. NUNCA calcules, sumes, redondees ni conviertas cifras (cuotas, topes, presupuestos, porcentajes, \
            plazos, fechas). Copia exactamente lo que devolvió la herramienta, con su formato (por ejemplo \
            «$300.000.000» o «29,6 %»). Si necesitas un número que ninguna herramienta dio, dilo y no lo inventes.
            3. Si una herramienta responde con 'error' o 'sinEvidencia', díselo al asesor tal cual y pregunta lo \
            que haga falta; no rellenes la respuesta con suposiciones. Si una referencia es ambigua, pregunta cuál.
            4. Cuando uses consultarExpedientes, conserva las citas [n] de la respuesta y menciona el documento y la \
            página. Si no hay evidencia, di que no tienes información suficiente en los documentos.
            5. Los recordatorios a propietarios solo quedan como BORRADOR: tú nunca los envías. Dile al asesor que \
            debe revisarlos y aprobarlos en la bandeja de Recordatorios.
            6. Los resultados de la IA son sugerencias; las reglas y el asesor deciden. Las simulaciones y los \
            presupuestos son estimaciones: la aprobación del crédito es del banco. Si la tasa es de ejemplo, avísalo.
            7. Eres solo para temas de la inmobiliaria. Si te preguntan otra cosa (deportes, famosos, clima, \
            recetas, noticias, cultura general...), no la respondas ni uses tu conocimiento general: di que solo \
            ayudas con la inmobiliaria. No pidas ni repitas datos personales que no hagan falta.

            Para «qué les alcanza a unos clientes»: llama SIEMPRE a buscarInmuebles con paraCliente (ya devuelve su \
            tope de compra) y responde con el tope y los inmuebles que devuelva; usa calcularPresupuesto solo si \
            piden el detalle del presupuesto. Nunca respondas únicamente con el presupuesto. \
            Para «qué le falta al dueño de un inmueble»: consulta el estado documental del negocio.""";

    /** Lo que se responde a un mensaje de otro tema: fijo, escrito en Java, sin llamar al modelo con herramientas. */
    public static final String FUERA_DE_TEMA = "Solo puedo ayudarte con temas de tu inmobiliaria: inmuebles, clientes "
            + "y presupuestos, simulación de créditos, expedientes y el cierre de los negocios. ¿Qué necesitas?";

    private final ChatClient chatClient;
    private final FiltroDeTema filtroDeTema;
    private final EstadoIa estadoIa;
    private final ServiciosHerramientas servicios;
    private final Clock reloj;
    private final Map<String, List<Message>> conversaciones = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
            return size() > MAXIMO_CONVERSACIONES;
        }
    };

    public ServicioAsistente(ChatClient.Builder chatClientBuilder, FiltroDeTema filtroDeTema, EstadoIa estadoIa,
                             ServiciosHerramientas servicios, Clock reloj) {
        this.chatClient = chatClientBuilder.build();
        this.filtroDeTema = filtroDeTema;
        this.estadoIa = estadoIa;
        this.servicios = servicios;
        this.reloj = reloj;
    }

    public RespuestaAsistente responder(UsuarioActual usuario, String mensaje, String conversacionId) {
        if (!estadoIa.disponible()) {
            throw new IaNoDisponibleException(SIN_IA);
        }
        String id = conversacionId == null || conversacionId.isBlank() ? UUID.randomUUID().toString()
                : conversacionId.strip();
        String clave = usuario.inmobiliariaId() + ":" + usuario.id() + ":" + id;
        List<Message> historial = historial(clave);

        // Un tema ajeno no llega al asistente: se responde un aviso fijo y no queda en el historial.
        if (filtroDeTema.esAjeno(mensaje, historial)) {
            log.info("Mensaje de otro tema: se responde el aviso fijo, sin llamar al asistente.");
            RegistroHerramientas vacio = new RegistroHerramientas();
            return new RespuestaAsistente(id, FUERA_DE_TEMA, vacio.usadas(), vacio.citas(),
                    vacio.recordatoriosCreados(), List.of(), null);
        }

        RegistroHerramientas registro = new RegistroHerramientas();
        HerramientasPredia herramientas = new HerramientasPredia(usuario, servicios, registro);

        String texto;
        try {
            texto = chatClient.prompt()
                    .system(INSTRUCCION_SISTEMA + "\n\nHoy es " + DescriptorItem.fecha(LocalDate.now(reloj))
                            + ". Hablas con " + usuario.nombre() + ".")
                    .messages(historial)
                    .user(mensaje)
                    .tools(herramientas)
                    .call().content();
        } catch (RuntimeException e) {
            throw new FalloDeIaException(FALLO_IA, e);
        }
        if (texto == null || texto.isBlank()) {
            throw new FalloDeIaException(FALLO_IA, null);
        }

        List<String> fuentes = new ArrayList<>(registro.salidas());
        fuentes.add(mensaje);
        historial.forEach(m -> fuentes.add(m.getText()));
        List<String> sinRespaldo = GuardiaCifras.sinRespaldo(texto, fuentes);
        if (!sinRespaldo.isEmpty()) {
            log.warn("El asistente escribió {} cifra(s) sin respaldo de las herramientas", sinRespaldo.size());
        }

        guardar(clave, mensaje, texto);
        return new RespuestaAsistente(id, texto, registro.usadas(), registro.citas(), registro.recordatoriosCreados(),
                sinRespaldo, sinRespaldo.isEmpty() ? null : RespuestaAsistente.AVISO_CIFRAS);
    }

    private synchronized List<Message> historial(String clave) {
        return new ArrayList<>(conversaciones.getOrDefault(clave, List.of()));
    }

    private synchronized void guardar(String clave, String mensaje, String respuesta) {
        List<Message> mensajes = new ArrayList<>(conversaciones.getOrDefault(clave, List.of()));
        mensajes.add(new UserMessage(mensaje));
        mensajes.add(new AssistantMessage(respuesta));
        int exceso = mensajes.size() - MAXIMO_MENSAJES_POR_CONVERSACION;
        conversaciones.put(clave, exceso > 0 ? new ArrayList<>(mensajes.subList(exceso, mensajes.size())) : mensajes);
    }
}
