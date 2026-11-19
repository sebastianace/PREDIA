package co.predia.cierre.recordatorios;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.cierre.reglas.DescriptorItem;
import co.predia.comun.ia.EstadoIa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con IA "disponible" (mockeada: nunca llama a OpenAI), el texto que redacta el modelo solo se acepta si pasa la
 * validación en Java. Si menciona un documento que no falta, olvida uno que sí falta, o trae números nuevos, se
 * usa la plantilla. En todos los casos el recordatorio queda SUGERIDO y no se envía nada.
 */
class RecordatorioConIaTest extends PruebaIntegracion {

    @MockitoBean EstadoIa estadoIa;
    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean ChatModel chatModel;
    @MockitoBean JavaMailSender correo;

    private Usuario asesor;
    private Negocio negocio;

    /** Falta: tradición (vencida hace 1 día), administración y promesa. */
    private void preparar(String nombre) throws Exception {
        when(estadoIa.disponible()).thenReturn(true);
        Inmobiliaria inmobiliaria = crearInmobiliaria(nombre);
        asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "IA-" + Math.abs(nombre.hashCode() % 1000));
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Jorge Ramírez", "jorge@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        negocio = crearNegocio(inmobiliaria, asesor, inmueble, propietario, null);
        ParametrosInmobiliaria p = parametros.findById(inmobiliaria.getId()).orElseThrow();
        p.actualizar(p.getPctCuotaMaxIngreso(), p.getPctCuotaInicial(), p.getPctCuotaInicialVis(),
                p.getPlazoDefaultAnios(), 0, p.getCiudadesConValorizacion(), p.getUmbralSimilitudRag(),
                p.getEmailRemitente());
        parametros.save(p);
        recibir("CEDULA_VENDEDOR", null);
        recibir("ESCRITURA_ADQUISICION", null);
        recibir("PREDIAL", hoy().toString());
        recibir("CERTIFICADO_TRADICION", hoy().minusDays(31).toString());
    }

    private void recibir(String codigo, String fecha) throws Exception {
        String estado = mvc.perform(get("/api/negocios/" + negocio.getId() + "/estado-documental")
                .header("Authorization", bearer(asesor))).andReturn().getResponse().getContentAsString();
        Number id = ((List<Number>) JsonPath.read(estado, "$.items[?(@.codigo=='" + codigo + "')].id")).get(0);
        mvc.perform(put("/api/negocios/" + negocio.getId() + "/requisitos/" + id)
                .header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                .content(fecha == null ? "{\"accion\": \"RECIBIR\"}"
                        : "{\"accion\": \"RECIBIR\", \"fechaExpedicion\": \"%s\"}".formatted(fecha)));
    }

    private void iaResponde(String asunto, String cuerpo) throws Exception {
        String json = new ObjectMapper().writeValueAsString(Map.of("asunto", asunto, "cuerpo", cuerpo));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    private String generar() throws Exception {
        return mvc.perform(post("/api/negocios/" + negocio.getId() + "/recordatorios")
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static String venció() {
        return DescriptorItem.fecha(hoy().minusDays(1));
    }

    @Test
    void unTextoDeLaIaQuePasaLaValidacionSeUsaTalCual() throws Exception {
        preparar("IaValido");
        String texto = "Estimado Jorge: le escribimos para avanzar con la venta. Todavía necesitamos: el "
                + "Certificado de tradición y libertad (venció el " + venció() + "; se necesita uno expedido hace "
                + "máximo 30 días), el Paz y salvo de administración y la Promesa de compraventa firmada. Gracias.";
        iaResponde("Documentos para su venta", texto);

        String json = generar();

        assertThat(JsonPath.<String>read(json, "$.origenTexto")).isEqualTo("IA");
        assertThat(JsonPath.<String>read(json, "$.cuerpo")).isEqualTo(texto);
        assertThat(JsonPath.<String>read(json, "$.asunto")).isEqualTo("Documentos para su venta");
        assertThat(JsonPath.<String>read(json, "$.estado")).isEqualTo("SUGERIDO");
        verifyNoInteractions(correo);
    }

    @Test
    void siLaIaMencionaUnDocumentoQueNoFaltaSeUsaLaPlantilla() throws Exception {
        preparar("IaMencionaDeMas");
        iaResponde("Documentos", "Necesitamos el certificado de tradición y libertad, el paz y salvo de "
                + "administración, la promesa de compraventa y la carta de levantamiento de la hipoteca.");

        String json = generar();

        assertThat(JsonPath.<String>read(json, "$.origenTexto")).isEqualTo("PLANTILLA");
        assertThat(JsonPath.<String>read(json, "$.cuerpo").toLowerCase()).doesNotContain("hipoteca");
        verifyNoInteractions(correo);
    }

    @Test
    void siLaIaOlvidaUnDocumentoQueSiFaltaSeUsaLaPlantilla() throws Exception {
        preparar("IaOlvida");
        iaResponde("Documentos", "Necesitamos el certificado de tradición y libertad y el paz y salvo de administración.");

        String json = generar();

        assertThat(JsonPath.<String>read(json, "$.origenTexto")).isEqualTo("PLANTILLA");
        assertThat(JsonPath.<String>read(json, "$.cuerpo")).contains("Promesa de compraventa firmada");
    }

    @Test
    void siLaIaInventaUnNumeroOUnPlazoSeUsaLaPlantilla() throws Exception {
        preparar("IaInventaNumero");
        iaResponde("Documentos", "Necesitamos el certificado de tradición y libertad (máximo 7777 días), el paz y "
                + "salvo de administración y la promesa de compraventa.");

        String json = generar();

        assertThat(JsonPath.<String>read(json, "$.origenTexto")).isEqualTo("PLANTILLA");
        assertThat(JsonPath.<String>read(json, "$.cuerpo")).doesNotContain("7777");
    }

    @Test
    void siLaIaFallaSeUsaLaPlantillaYElRecordatorioIgualSeCrea() throws Exception {
        preparar("IaFalla");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Simulado: sin respuesta"));

        String json = generar();

        assertThat(JsonPath.<String>read(json, "$.origenTexto")).isEqualTo("PLANTILLA");
        assertThat(JsonPath.<List<Object>>read(json, "$.items")).hasSize(3);
        verifyNoInteractions(correo);
    }
}
