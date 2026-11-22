package co.predia.expedientes;

import co.predia.expedientes.indexacion.EstadoTrabajo;
import co.predia.expedientes.indexacion.ProcesadorIndexacion;
import co.predia.expedientes.indexacion.TrabajoIndexacion;
import co.predia.expedientes.indexacion.TrabajoIndexacionRepository;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin API key de OpenAI (perfil "test"), así que esto prueba el requerimiento base sin IA: subida,
 * extracción con PDFBox, fragmentación, borrado del temporal y el estado de indexación visible.
 * No llama a OpenAI en ningún momento.
 */
class IndexacionAsincronaTest extends PruebaIntegracion {

    @Autowired ProcesadorIndexacion procesador;
    @Autowired TrabajoIndexacionRepository trabajoRepositorio;
    @Autowired JdbcTemplate jdbc;

    @Test
    void subirUnPdfLoIndexaYBorraElTemporal() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Expedientes");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "EXP-1");

        byte[] pdf = pdfConTexto(parrafoLargo("Contrato de arrendamiento", 40));
        MockMultipartFile archivo = new MockMultipartFile("archivo", "contrato.pdf", "application/pdf", pdf);

        MvcResult subida = mvc.perform(multipart("/api/documentos").file(archivo)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estadoIndexacion").value("EN_COLA"))
                .andExpect(jsonPath("$.nombreArchivo").value("contrato.pdf"))
                .andReturn();

        Long documentoId = leerId(subida);
        TrabajoIndexacion trabajo = trabajoRepositorio.findByDocumentoId(documentoId).orElseThrow();
        String rutaTemporal = trabajo.getRutaTemporal();
        assertThat(new File(rutaTemporal)).exists();

        // Se procesa la cola directamente (en vez de esperar al @Scheduled) para que la prueba sea rápida.
        procesador.procesarPendientes();

        mvc.perform(get("/api/documentos/" + documentoId).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoIndexacion").value("INDEXADO"))
                .andExpect(jsonPath("$.paginas").value(1))
                .andExpect(jsonPath("$.paginasOcr").value(0))
                .andExpect(jsonPath("$.vectorizado").value(false)); // no hay API key en las pruebas

        assertThat(new File(rutaTemporal)).doesNotExist();

        Integer fragmentos = jdbc.queryForObject("SELECT count(*) FROM fragmento WHERE documento_id = ?",
                Integer.class, documentoId);
        assertThat(fragmentos).isGreaterThan(0);

        TrabajoIndexacion actualizado = trabajoRepositorio.findByDocumentoId(documentoId).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoTrabajo.TERMINADO);
    }

    @Test
    void subirElMismoPdfDosVecesNoDuplicaElTrabajo() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Duplicados");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "EXP-2");
        byte[] pdf = pdfConTexto("Un documento cualquiera.");

        MockMultipartFile primero = new MockMultipartFile("archivo", "a.pdf", "application/pdf", pdf);
        MvcResult r1 = mvc.perform(multipart("/api/documentos").file(primero)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isAccepted()).andReturn();
        Long primerId = leerId(r1);

        MockMultipartFile segundo = new MockMultipartFile("archivo", "b.pdf", "application/pdf", pdf);
        MvcResult r2 = mvc.perform(multipart("/api/documentos").file(segundo)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isOk()) // no 202: no se creó un trabajo nuevo
                .andReturn();
        Long segundoId = leerId(r2);

        assertThat(segundoId).isEqualTo(primerId);
    }

    @Test
    void rechazaUnArchivoQueNoEsPdf() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Validacion");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "EXP-3");

        MockMultipartFile archivo = new MockMultipartFile("archivo", "foto.png", "image/png", "no-es-un-pdf".getBytes());

        mvc.perform(multipart("/api/documentos").file(archivo)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noSePuedeSubirUnDocumentoAUnInmuebleDeOtraInmobiliaria() throws Exception {
        Inmobiliaria propia = crearInmobiliaria("Inmobiliaria Propia");
        Inmobiliaria ajena = crearInmobiliaria("Inmobiliaria Ajena");
        Usuario asesor = crearUsuario(propia, Rol.ASESOR);
        Inmueble inmuebleAjeno = crearInmueble(ajena, "AJENO-1");

        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "x.pdf", "application/pdf", pdfConTexto("Texto."));

        mvc.perform(multipart("/api/documentos").file(archivo)
                        .param("inmuebleId", inmuebleAjeno.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unaPaginaEscaneadaSeLeeConOcrYQuedaMarcadaComoTal() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria OCR");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "EXP-OCR");

        byte[] pdfEscaneado = pdfEscaneado("CERTIFICADO DE TRADICION");
        MockMultipartFile archivo =
                new MockMultipartFile("archivo", "certificado.pdf", "application/pdf", pdfEscaneado);

        MvcResult subida = mvc.perform(multipart("/api/documentos").file(archivo)
                        .param("inmuebleId", inmueble.getId().toString())
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isAccepted())
                .andReturn();
        Long documentoId = leerId(subida);

        procesador.procesarPendientes();

        mvc.perform(get("/api/documentos/" + documentoId).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoIndexacion").value("INDEXADO"))
                .andExpect(jsonPath("$.paginasOcr").value(1));

        String texto = jdbc.queryForObject("SELECT texto FROM fragmento WHERE documento_id = ? LIMIT 1",
                String.class, documentoId);
        assertThat(texto.toUpperCase()).contains("CERTIFICADO");

        Boolean esOcr = jdbc.queryForObject("SELECT es_ocr FROM fragmento WHERE documento_id = ? LIMIT 1",
                Boolean.class, documentoId);
        assertThat(esOcr).isTrue();
    }

    /** Un PDF de una sola página con una imagen (sin texto): obliga a pasar por OCR. */
    private static byte[] pdfEscaneado(String texto) throws Exception {
        BufferedImage imagen = new BufferedImage(1600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 60));
        g.drawString(texto, 40, 200);
        g.dispose();

        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            PDImageXObject imagenPdf = LosslessFactory.createFromImage(documento, imagen);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.drawImage(imagenPdf, 0, pagina.getMediaBox().getHeight() - 150,
                        pagina.getMediaBox().getWidth(), 150);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    private Long leerId(MvcResult resultado) throws Exception {
        Map<String, Object> cuerpo = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(resultado.getResponse().getContentAsString(), Map.class);
        return ((Number) cuerpo.get("id")).longValue();
    }
}
