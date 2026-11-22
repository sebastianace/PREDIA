package co.predia.expedientes.consulta;

import co.predia.comun.ia.EstadoIa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el motor RAG (búsqueda híbrida + citas) con EmbeddingModel y ChatModel MOCKEADOS: nunca
 * llama a OpenAI. Los vectores de los fragmentos se insertan directo por SQL, para controlar la
 * similitud sin depender de embeddings reales.
 */
class ConsultaRagApiTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;
    @MockitoBean EstadoIa estadoIa;
    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean ChatModel chatModel;

    @Test
    void conEvidenciaRelevanteRespondeYDescartaCitasInvalidas() throws Exception {
        when(estadoIa.disponible()).thenReturn(true);
        when(estadoIa.modeloChat()).thenReturn("modelo-de-pruebas");
        when(embeddingModel.embed(anyList())).thenReturn(List.of(vector(0)));
        when(chatModel.call(any(Prompt.class))).thenReturn(respuestaDe(
                "El canon es de $1.800.000 mensuales [1]. También se menciona en [9], que no existe."));

        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria RAG");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "RAG-1");
        Long documentoId = crearDocumento(inmobiliaria, inmueble, asesor, "contrato.pdf");

        insertarFragmento(inmobiliaria, documentoId, inmueble, 0, "CLÁUSULA SEGUNDA",
                "El canon mensual de arrendamiento se fija en $1.800.000.", vector(0));
        insertarFragmento(inmobiliaria, documentoId, inmueble, 1, null,
                "Texto totalmente distinto, sin relación con la pregunta del asesor.", vector(1));

        mvc.perform(post("/api/consultas").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pregunta": "¿Cuánto es el canon de arrendamiento?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinEvidencia").value(false))
                .andExpect(jsonPath("$.modoManual").value(false))
                .andExpect(jsonPath("$.respuesta", org.hamcrest.Matchers.containsString("1.800.000")))
                .andExpect(jsonPath("$.citas", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.citas[0].numero").value(1))
                .andExpect(jsonPath("$.citas[0].documentoId").value(documentoId))
                .andExpect(jsonPath("$.citas[0].seccion").value("CLÁUSULA SEGUNDA"));
    }

    @Test
    void sinEvidenciaSuficienteRespondeNoSeSinLlamarAlModelo() throws Exception {
        when(estadoIa.disponible()).thenReturn(true);
        when(embeddingModel.embed(anyList())).thenReturn(List.of(vector(5)));

        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Sin Evidencia");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "RAG-2");
        Long documentoId = crearDocumento(inmobiliaria, inmueble, asesor, "otro.pdf");
        // Embedding ortogonal (similitud 0) y texto sin ninguna palabra en común con la pregunta.
        insertarFragmento(inmobiliaria, documentoId, inmueble, 0, null,
                "xilofono marciano criptografia volcan desierto.", vector(90));

        mvc.perform(post("/api/consultas").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pregunta": "mantequilla bicicleta electron"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinEvidencia").value(true))
                .andExpect(jsonPath("$.citas", org.hamcrest.Matchers.hasSize(0)));

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void sinIaDisponibleMuestraLaEvidenciaSinRedactarRespuesta() throws Exception {
        when(estadoIa.disponible()).thenReturn(false);

        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Modo Manual");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "RAG-3");
        Long documentoId = crearDocumento(inmobiliaria, inmueble, asesor, "manual.pdf");
        insertarFragmento(inmobiliaria, documentoId, inmueble, 0, "CLÁUSULA PRIMERA",
                "El apartamento tiene balcón y vista a la montaña.", null);

        mvc.perform(post("/api/consultas").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pregunta": "¿El apartamento tiene balcón?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoManual").value(true))
                .andExpect(jsonPath("$.sinEvidencia").value(false))
                .andExpect(jsonPath("$.respuesta").doesNotExist())
                .andExpect(jsonPath("$.citas", org.hamcrest.Matchers.hasSize(1)));

        verify(chatModel, never()).call(any(Prompt.class));
        verify(embeddingModel, never()).embed(anyList());
    }

    @Test
    void unaInmobiliariaNuncaVeFragmentosDeOtra() throws Exception {
        when(estadoIa.disponible()).thenReturn(true);
        when(estadoIa.modeloChat()).thenReturn("modelo-de-pruebas");
        when(embeddingModel.embed(anyList())).thenReturn(List.of(vector(0)));
        when(chatModel.call(any(Prompt.class))).thenReturn(respuestaDe("Respuesta con evidencia [1]."));

        Inmobiliaria propia = crearInmobiliaria("Inmobiliaria Aislada A");
        Inmobiliaria ajena = crearInmobiliaria("Inmobiliaria Aislada B");
        Usuario asesorPropio = crearUsuario(propia, Rol.ASESOR);
        Usuario asesorAjeno = crearUsuario(ajena, Rol.ASESOR);
        Inmueble inmueblePropio = crearInmueble(propia, "RAG-4A");
        Inmueble inmuebleAjeno = crearInmueble(ajena, "RAG-4B");

        Long docPropio = crearDocumento(propia, inmueblePropio, asesorPropio, "propio.pdf");
        Long docAjeno = crearDocumento(ajena, inmuebleAjeno, asesorAjeno, "ajeno.pdf");
        insertarFragmento(propia, docPropio, inmueblePropio, 0, null, "Documento propio de la inmobiliaria A.",
                vector(0));
        insertarFragmento(ajena, docAjeno, inmuebleAjeno, 0, null, "Documento ajeno de la inmobiliaria B.",
                vector(0));

        mvc.perform(post("/api/consultas").header("Authorization", bearer(asesorPropio))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pregunta": "¿Qué dice el documento?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citas[0].documentoId").value(docPropio));
    }

    private static float[] vector(int dimensionActiva) {
        float[] v = new float[1536];
        v[dimensionActiva] = 1f;
        return v;
    }

    private static ChatResponse respuestaDe(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }

    private Long crearDocumento(Inmobiliaria inmobiliaria, Inmueble inmueble, Usuario usuario, String nombre) {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "").substring(0, 64);
        return jdbc.queryForObject("""
                INSERT INTO documento (inmobiliaria_id, inmueble_id, nombre_archivo, hash_sha256, tamano_bytes,
                                       subido_por, estado_indexacion)
                VALUES (?, ?, ?, ?, 1000, ?, 'INDEXADO') RETURNING id
                """, Long.class, inmobiliaria.getId(), inmueble.getId(), nombre, hash, usuario.getId());
    }

    private void insertarFragmento(Inmobiliaria inmobiliaria, Long documentoId, Inmueble inmueble, int orden,
                                   String seccion, String texto, float[] embedding) {
        jdbc.update("""
                INSERT INTO fragmento (inmobiliaria_id, documento_id, inmueble_id, orden, pagina_inicio, pagina_fin,
                                       seccion, texto, tokens, es_ocr, embedding, embedding_modelo)
                VALUES (?, ?, ?, ?, 1, 1, ?, ?, 20, false, ?::vector, 'pruebas')
                """, inmobiliaria.getId(), documentoId, inmueble.getId(), orden, seccion, texto,
                aTextoVector(embedding));
    }

    private static String aTextoVector(float[] valores) {
        if (valores == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(valores[i]);
        }
        return sb.append(']').toString();
    }
}
