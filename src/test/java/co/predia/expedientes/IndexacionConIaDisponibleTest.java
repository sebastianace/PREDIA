package co.predia.expedientes;

import co.predia.comun.ia.EstadoIa;
import co.predia.expedientes.indexacion.ProcesadorIndexacion;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con IA "disponible" (EstadoIa mockeado), pero sin llamar nunca a OpenAI: EmbeddingModel y ChatModel
 * van simulados (mocks), como pide el proyecto. Comprueba que los fragmentos quedan vectorizados y que
 * una clasificación que falla no tumba la indexación.
 */
class IndexacionConIaDisponibleTest extends PruebaIntegracion {

    @Autowired ProcesadorIndexacion procesador;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean EstadoIa estadoIa;
    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean ChatModel chatModel;

    @Test
    void conIaDisponibleLosFragmentosQuedanVectorizadosYLaClasificacionNoTumbaLaIndexacion() throws Exception {
        when(estadoIa.disponible()).thenReturn(true);
        when(estadoIa.modeloEmbeddings()).thenReturn("text-embedding-3-small-pruebas");
        when(embeddingModel.embed(org.mockito.ArgumentMatchers.<List<String>>any()))
                .thenAnswer(inv -> {
                    List<String> textos = inv.getArgument(0);
                    return textos.stream().map(t -> vectorFalso()).toList();
                });
        // El chat falla (simula que la IA está "disponible" pero la clasificación da error):
        // la indexación debe completarse igual, solo sin sugerencia de tipo.
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new RuntimeException("Simulado: el modelo de chat no respondió."));

        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria con IA");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "IA-1");

        byte[] pdf = pdfConTexto(parrafoLargo("Escritura pública", 10));
        MockMultipartFile archivo = new MockMultipartFile("archivo", "escritura.pdf", "application/pdf", pdf);

        MvcResult subida = mvc.perform(multipart("/api/documentos").file(archivo)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isAccepted())
                .andReturn();
        Long documentoId = leerId(subida);

        procesador.procesarPendientes();

        mvc.perform(get("/api/documentos/" + documentoId).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk());

        Boolean vectorizado = jdbc.queryForObject("SELECT vectorizado FROM documento WHERE id = ?", Boolean.class,
                documentoId);
        assertThat(vectorizado).isTrue();

        Integer sinVector = jdbc.queryForObject(
                "SELECT count(*) FROM fragmento WHERE documento_id = ? AND embedding IS NULL", Integer.class,
                documentoId);
        assertThat(sinVector).isZero();

        String modeloGuardado = jdbc.queryForObject(
                "SELECT DISTINCT embedding_modelo FROM fragmento WHERE documento_id = ?", String.class, documentoId);
        assertThat(modeloGuardado).isEqualTo("text-embedding-3-small-pruebas");

        // La clasificación falló, pero el documento quedó INDEXADO igual.
        String estado = jdbc.queryForObject("SELECT estado_indexacion FROM documento WHERE id = ?", String.class,
                documentoId);
        String clasificacion = jdbc.queryForObject(
                "SELECT clasificacion_estado FROM documento WHERE id = ?", String.class, documentoId);
        assertThat(estado).isEqualTo("INDEXADO");
        assertThat(clasificacion).isEqualTo("SIN_CLASIFICAR");
    }

    private static float[] vectorFalso() {
        float[] v = new float[1536];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) Math.random();
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private Long leerId(MvcResult resultado) throws Exception {
        Map<String, Object> cuerpo = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(resultado.getResponse().getContentAsString(), Map.class);
        return ((Number) cuerpo.get("id")).longValue();
    }
}
