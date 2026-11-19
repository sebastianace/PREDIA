package co.predia.expedientes.indexacion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Extracción de texto y render de páginas con Apache PDFBox. Una página se considera "sin texto útil"
 * (y por lo tanto candidata a OCR) cuando tiene menos de {@link #MIN_CARACTERES_UTILES} caracteres no vacíos.
 */
@Component
public class ExtractorPdf {

    private static final int MIN_CARACTERES_UTILES = 30;

    /** Abre el PDF. El llamador es responsable de cerrarlo (try-with-resources). */
    public PDDocument abrir(File archivo) throws IOException {
        return Loader.loadPDF(archivo);
    }

    public int contarPaginas(PDDocument documento) {
        return documento.getNumberOfPages();
    }

    public boolean esEncriptado(PDDocument documento) {
        return documento.isEncrypted();
    }

    /** Texto de una página (1-indexada), tal como lo extrae PDFBox directamente del PDF. */
    public String extraerTexto(PDDocument documento, int numeroPagina) throws IOException {
        PDFTextStripper extractor = new PDFTextStripper();
        extractor.setStartPage(numeroPagina);
        extractor.setEndPage(numeroPagina);
        return extractor.getText(documento).trim();
    }

    public boolean tieneTextoUtil(String texto) {
        return texto != null && texto.replaceAll("\\s+", "").length() >= MIN_CARACTERES_UTILES;
    }

    /** Renderiza una página (1-indexada) como imagen en escala de grises, lista para el OCR. */
    public BufferedImage renderizar(PDDocument documento, int numeroPagina, float dpi) throws IOException {
        PDFRenderer renderizador = new PDFRenderer(documento);
        return renderizador.renderImageWithDPI(numeroPagina - 1, dpi, ImageType.GRAY);
    }
}
