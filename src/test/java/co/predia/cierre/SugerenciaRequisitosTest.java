package co.predia.cierre;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.comun.ia.EstadoIa;
import co.predia.expedientes.indexacion.ProcesadorIndexacion;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cuando se indexa un documento de un negocio y la IA reconoce su tipo y su fecha, el requisito pasa a SUGERIDO.
 * Solo el asesor lo pasa a RECIBIDO. EstadoIa, EmbeddingModel y ChatModel van mockeados: nunca llama a OpenAI.
 */
class SugerenciaRequisitosTest extends PruebaIntegracion {

    @Autowired ProcesadorIndexacion procesador;
    @MockitoBean EstadoIa estadoIa;
    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean ChatModel chatModel;

    private static ChatResponse clasificacion(String tipo, java.time.LocalDate fecha) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(
                "{\"tipoDocumento\": \"%s\", \"fechaExpedicion\": \"%s\", \"confianza\": 0.93}".formatted(tipo, fecha)))));
    }

    private void simularIa() {
        when(estadoIa.disponible()).thenReturn(true);
        when(estadoIa.modeloEmbeddings()).thenReturn("embeddings-de-pruebas");
        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<String> textos = inv.getArgument(0);
            return textos.stream().map(t -> new float[1536]).toList();
        });
    }

    private Long subir(Usuario asesor, Negocio negocio, String texto) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/documentos")
                        .file(new MockMultipartFile("archivo", "doc.pdf", "application/pdf", pdfConTexto(texto)))
                        .param("inmuebleId", negocio.getInmuebleId().toString())
                        .param("negocioId", negocio.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isAccepted()).andReturn();
        return idDe(r);
    }

    private String estado(Usuario asesor, Negocio negocio) throws Exception {
        return mvc.perform(get("/api/negocios/" + negocio.getId() + "/estado-documental")
                .header("Authorization", bearer(asesor))).andReturn().getResponse().getContentAsString();
    }

    private static String campo(String json, String codigo, String propiedad) {
        List<Object> valores = JsonPath.read(json, "$.items[?(@.codigo=='" + codigo + "')]." + propiedad);
        return valores.isEmpty() || valores.get(0) == null ? null : valores.get(0).toString();
    }

    @Test
    void laIaSugiereElRequisitoYSoloElAsesorLoDaPorRecibido() throws Exception {
        simularIa();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Sugerencia Inmobiliaria");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "SUG-1");
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Jorge Ramírez", "jorge@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inmobiliaria, asesor, inmueble, propietario, hoy().plusDays(10));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(clasificacion("CERTIFICADO_TRADICION_LIBERTAD", hoy().minusDays(5)));

        Long documentoId = subir(asesor, negocio, "Certificado de tradicion y libertad del inmueble.");
        procesador.procesarPendientes();

        String sugerido = estado(asesor, negocio);
        // La IA solo sugiere: el requisito queda SUGERIDO, con el documento y la fecha que reconoció.
        assertThat(campo(sugerido, "CERTIFICADO_TRADICION", "estado")).isEqualTo("SUGERIDO");
        assertThat(campo(sugerido, "CERTIFICADO_TRADICION", "estadoRegistrado")).isEqualTo("SUGERIDO");
        assertThat(campo(sugerido, "CERTIFICADO_TRADICION", "documentoId")).isEqualTo(documentoId.toString());
        assertThat(campo(sugerido, "CERTIFICADO_TRADICION", "fechaExpedicion")).isEqualTo(hoy().minusDays(5).toString());
        assertThat(campo(sugerido, "CERTIFICADO_TRADICION", "recibidoPor")).isNull();
        // Sigue bloqueando el "Listo para firmar" mientras el asesor no lo confirme.
        assertThat(JsonPath.<Boolean>read(sugerido, "$.listoParaFirmar")).isFalse();
        assertThat(JsonPath.<Integer>read(sugerido, "$.recibidos")).isZero();

        // El asesor lo confirma, sin escribir la fecha: se usa la que reconoció la IA.
        Long id = Long.valueOf(campo(sugerido, "CERTIFICADO_TRADICION", "id"));
        mvc.perform(put("/api/negocios/" + negocio.getId() + "/requisitos/" + id)
                        .header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accion\": \"RECIBIR\", \"documentoId\": %d}".formatted(documentoId)))
                .andExpect(status().isOk());

        String recibido = estado(asesor, negocio);
        assertThat(campo(recibido, "CERTIFICADO_TRADICION", "estado")).isEqualTo("RECIBIDO");
        assertThat(campo(recibido, "CERTIFICADO_TRADICION", "venceEl")).isEqualTo(hoy().plusDays(25).toString());
        assertThat(campo(recibido, "CERTIFICADO_TRADICION", "recibidoPor")).isEqualTo(asesor.getNombre());
    }

    @Test
    void laSugerenciaNuncaPisaLoQueElAsesorYaDecidio() throws Exception {
        simularIa();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Sugerencia No Pisa");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "SUG-2");
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Jorge Ramírez", "jorge@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inmobiliaria, asesor, inmueble, propietario, null);

        // El asesor ya recibió el certificado de tradición.
        String inicial = estado(asesor, negocio);
        mvc.perform(put("/api/negocios/" + negocio.getId() + "/requisitos/" + campo(inicial, "CERTIFICADO_TRADICION", "id"))
                .header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accion\": \"RECIBIR\", \"fechaExpedicion\": \"%s\"}".formatted(hoy().minusDays(3))));

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(clasificacion("CERTIFICADO_TRADICION_LIBERTAD", hoy().minusDays(60)));
        subir(asesor, negocio, "Otro certificado de tradicion mas viejo.");
        procesador.procesarPendientes();

        String despues = estado(asesor, negocio);
        assertThat(campo(despues, "CERTIFICADO_TRADICION", "estado")).isEqualTo("RECIBIDO");
        assertThat(campo(despues, "CERTIFICADO_TRADICION", "fechaExpedicion")).isEqualTo(hoy().minusDays(3).toString());
    }

    @Test
    void siElAsesorCorrigeElTipoLaSugerenciaSeMueveAlRequisitoCorrecto() throws Exception {
        simularIa();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Sugerencia Corrige");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "SUG-3");
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Jorge Ramírez", "jorge@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inmobiliaria, asesor, inmueble, propietario, null);
        when(chatModel.call(any(Prompt.class))).thenReturn(clasificacion("PAZ_Y_SALVO_PREDIAL", hoy()));

        Long documentoId = subir(asesor, negocio, "Paz y salvo con un nombre confuso.");
        procesador.procesarPendientes();
        assertThat(campo(estado(asesor, negocio), "PREDIAL", "estado")).isEqualTo("SUGERIDO");

        // La IA se equivocó: era el paz y salvo de administración.
        mvc.perform(put("/api/documentos/" + documentoId + "/clasificacion")
                        .header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDocumento\": \"PAZ_Y_SALVO_ADMINISTRACION\", \"fechaExpedicion\": \"%s\"}"
                                .formatted(hoy())))
                .andExpect(status().isOk());

        String corregido = estado(asesor, negocio);
        assertThat(campo(corregido, "PREDIAL", "estado")).isEqualTo("PENDIENTE");
        assertThat(campo(corregido, "ADMINISTRACION", "estado")).isEqualTo("SUGERIDO");
    }
}
