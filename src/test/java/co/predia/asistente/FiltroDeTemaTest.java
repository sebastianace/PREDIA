package co.predia.asistente;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cómo se lee la respuesta del filtro y qué contexto recibe: sin Spring ni modelo. */
class FiltroDeTemaTest {

    @Test
    void soloEsAjenoSiLaRespuestaDiceAjeno() {
        assertThat(FiltroDeTema.interpretar("AJENO")).isTrue();
        assertThat(FiltroDeTema.interpretar("  ajeno.\n")).isTrue();
        assertThat(FiltroDeTema.interpretar("Clasificación: AJENO")).isTrue();
        assertThat(FiltroDeTema.interpretar("INMOBILIARIA")).isFalse();
    }

    @Test
    void anteCualquierDudaElMensajePasa() {
        assertThat(FiltroDeTema.interpretar(null)).isFalse();
        assertThat(FiltroDeTema.interpretar("")).isFalse();
        assertThat(FiltroDeTema.interpretar("no sé qué decirte")).isFalse();
        // Si nombra las dos, no se bloquea una pregunta que podría ser legítima.
        assertThat(FiltroDeTema.interpretar("AJENO o INMOBILIARIA")).isFalse();
    }

    @Test
    void ignoraElPensamientoDelModeloSiLoTrae() {
        assertThat(FiltroDeTema.interpretar("<think>Esto parece del trabajo, no AJENO.</think>\nINMOBILIARIA")).isFalse();
        assertThat(FiltroDeTema.interpretar("<think>La pregunta es de INMOBILIARIA?\nNo.</think>AJENO")).isTrue();
    }

    @Test
    void laEntradaLlevaElMensajeYLoUltimoQueDijoElAsistente() {
        List<Message> historial = List.of(new UserMessage("¿Qué hay en Cabecera?"),
                new AssistantMessage("Hay un apartamento en Cabecera de $380.000.000."),
                new UserMessage("otra cosa"), new AssistantMessage("Dime."));

        String entrada = FiltroDeTema.entrada("  y el de Provenza?  ", historial);

        assertThat(entrada).contains("Lo último que dijo el asistente: «Dime.»")
                .contains("Mensaje del asesor: «y el de Provenza?»");
    }

    @Test
    void sinHistorialSoloVaElMensajeYElContextoLargoSeRecorta() {
        assertThat(FiltroDeTema.entrada("hola", List.of())).doesNotContain("Lo último").contains("«hola»");

        String largo = "palabra ".repeat(200);
        String entrada = FiltroDeTema.entrada("sí", List.of(new AssistantMessage(largo)));

        assertThat(entrada.length()).isLessThan(500);
        assertThat(entrada).contains("…");
    }
}
