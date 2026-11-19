package co.predia.expedientes;

import co.predia.expedientes.indexacion.ProcesadorIndexacion;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentoApiTest extends PruebaIntegracion {

    @Autowired ProcesadorIndexacion procesador;

    @Test
    void unaInmobiliariaNoVeNiBorraLosDocumentosDeOtra() throws Exception {
        Inmobiliaria propia = crearInmobiliaria("Inmobiliaria Documentos A");
        Inmobiliaria ajena = crearInmobiliaria("Inmobiliaria Documentos B");
        Usuario asesorPropio = crearUsuario(propia, Rol.ASESOR);
        Usuario asesorAjeno = crearUsuario(ajena, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(propia, "DOC-A1");

        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "x.pdf", "application/pdf", pdfConTexto("Texto de prueba."));
        MvcResult subida = mvc.perform(multipart("/api/documentos").file(archivo)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesorPropio)))
                .andExpect(status().isAccepted()).andReturn();
        Long documentoId = leerId(subida);

        // El asesor de la otra inmobiliaria no lo ve.
        mvc.perform(get("/api/documentos/" + documentoId).header("Authorization", bearer(asesorAjeno)))
                .andExpect(status().isNotFound());

        // Ni puede borrarlo.
        mvc.perform(delete("/api/documentos/" + documentoId).header("Authorization", bearer(asesorAjeno)))
                .andExpect(status().isNotFound());

        // El dueño sí puede borrarlo (y los fragmentos se van con él, sin error de llave foránea).
        procesador.procesarPendientes();
        mvc.perform(delete("/api/documentos/" + documentoId).header("Authorization", bearer(asesorPropio)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/documentos/" + documentoId).header("Authorization", bearer(asesorPropio)))
                .andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeClasificarUnDocumentoQueTodaviaNoEstaIndexado() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Clasificacion");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "DOC-C1");

        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "x.pdf", "application/pdf", pdfConTexto("Texto de prueba."));
        MvcResult subida = mvc.perform(multipart("/api/documentos").file(archivo)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isAccepted()).andReturn();
        Long documentoId = leerId(subida);

        // Todavía está EN_COLA: no se puede confirmar la clasificación.
        mvc.perform(put("/api/documentos/" + documentoId + "/clasificacion")
                        .header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDocumento": "CEDULA", "fechaExpedicion": "2020-01-01"}"""))
                .andExpect(status().isUnprocessableEntity());

        procesador.procesarPendientes();

        mvc.perform(put("/api/documentos/" + documentoId + "/clasificacion")
                        .header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDocumento": "CEDULA", "fechaExpedicion": "2020-01-01"}"""))
                .andExpect(status().isOk());
    }

    @SuppressWarnings("unchecked")
    private Long leerId(MvcResult resultado) throws Exception {
        Map<String, Object> cuerpo = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(resultado.getResponse().getContentAsString(), Map.class);
        return ((Number) cuerpo.get("id")).longValue();
    }
}
