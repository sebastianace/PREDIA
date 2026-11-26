package co.predia.expedientes.carga;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationFileAttachment;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Revisión estructural del PDF: sin Spring ni base de datos. */
class InspectorPdfTest {

    private final InspectorPdf inspector = new InspectorPdf();

    @TempDir
    Path directorio;

    @Test
    void unPdfNormalNoTieneMotivoDeRechazo() throws IOException {
        assertThat(motivoDe(documento -> {
        })).isEmpty();
    }

    @Test
    void unEnlaceNormalAUnaPaginaWebNoSeRechaza() throws IOException {
        // Un enlace con una dirección es contenido pasivo: solo se rechazan las acciones que ejecutan algo.
        assertThat(motivoDe(documento -> {
            PDAnnotationLink enlace = new PDAnnotationLink();
            enlace.setAction(new org.apache.pdfbox.pdmodel.interactive.action.PDActionURI());
            agregarAnotacion(documento, enlace);
        })).isEmpty();
    }

    @Test
    void rechazaJavaScriptAlAbrirElDocumento() throws IOException {
        assertThat(motivoDe(documento -> documento.getDocumentCatalog()
                .setOpenAction(new PDActionJavaScript("app.alert('hola');"))))
                .hasValueSatisfying(m -> assertThat(m).contains("JavaScript"));
    }

    @Test
    void rechazaJavaScriptEscondidoEnUnaAnotacionDeUnaPagina() throws IOException {
        assertThat(motivoDe(documento -> {
            PDAnnotationLink enlace = new PDAnnotationLink();
            enlace.setAction(new PDActionJavaScript("this.print();"));
            agregarAnotacion(documento, enlace);
        })).hasValueSatisfying(m -> assertThat(m).contains("JavaScript"));
    }

    @Test
    void rechazaUnaAccionQueLanzaProgramas() throws IOException {
        assertThat(motivoDe(documento -> documento.getDocumentCatalog().setOpenAction(new PDActionLaunch())))
                .hasValueSatisfying(m -> assertThat(m).contains("abrir otros programas"));
    }

    @Test
    void rechazaArchivosIncrustados() throws IOException {
        assertThat(motivoDe(documento -> {
            try {
                PDComplexFileSpecification especificacion = new PDComplexFileSpecification();
                especificacion.setFile("adjunto.txt");
                especificacion.setEmbeddedFile(
                        new PDEmbeddedFile(documento, new ByteArrayInputStream("hola".getBytes(StandardCharsets.UTF_8))));
                PDAnnotationFileAttachment adjunto = new PDAnnotationFileAttachment();
                adjunto.setFile(especificacion);
                agregarAnotacion(documento, adjunto);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        })).hasValueSatisfying(m -> assertThat(m).contains("incrustados"));
    }

    @Test
    void reconoceLaFirmaPdfYRechazaLoQueSoloLlevaLaExtension() throws IOException {
        Path pdf = directorio.resolve("bueno.pdf");
        Files.write(pdf, bytesDe(documento -> {
        }));
        Path imagenDisfrazada = directorio.resolve("falso.pdf");
        Files.write(imagenDisfrazada, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13});
        Path ejecutable = directorio.resolve("programa.pdf");
        Files.write(ejecutable, "MZ\u0090\u0000\u0003\u0000 esto es un ejecutable".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(inspector.tieneFirmaPdf(pdf)).isTrue();
        assertThat(inspector.tieneFirmaPdf(imagenDisfrazada)).isFalse();
        assertThat(inspector.tieneFirmaPdf(ejecutable)).isFalse();
    }

    @Test
    void aceptaUnPdfConBasuraCortaAntesDeLaFirma() throws IOException {
        byte[] original = bytesDe(documento -> {
        });
        byte[] conBasura = new byte[original.length + 20];
        System.arraycopy("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n".getBytes(StandardCharsets.US_ASCII), 0, conBasura, 0, 20);
        System.arraycopy(original, 0, conBasura, 20, original.length);
        Path ruta = directorio.resolve("con-basura.pdf");
        Files.write(ruta, conBasura);

        assertThat(inspector.tieneFirmaPdf(ruta)).isTrue();
    }

    // --- ayudas -------------------------------------------------------------------------------------------------

    private java.util.Optional<String> motivoDe(Consumer<PDDocument> preparar) throws IOException {
        // Se guarda y se vuelve a cargar: se revisa lo que de verdad viaja en el archivo, incluidos los flujos de objetos.
        try (PDDocument cargado = Loader.loadPDF(bytesDe(preparar))) {
            return inspector.motivoDeRechazo(cargado);
        }
    }

    private static byte[] bytesDe(Consumer<PDDocument> preparar) throws IOException {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage());
            preparar.accept(documento);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private static void agregarAnotacion(PDDocument documento, PDAnnotation anotacion) {
        try {
            PDPage pagina = documento.getPage(0);
            List<PDAnnotation> anotaciones = new ArrayList<>(pagina.getAnnotations());
            anotaciones.add(anotacion);
            pagina.setAnnotations(anotaciones);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
