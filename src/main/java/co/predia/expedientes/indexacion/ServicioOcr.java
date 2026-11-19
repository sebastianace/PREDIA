package co.predia.expedientes.indexacion;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * OCR con Tesseract (tess4j), en español. Se crea una instancia nueva por reconocimiento: el motor de
 * Tesseract no es seguro para compartir entre hilos, y el volumen de páginas escaneadas es bajo.
 */
@Component
public class ServicioOcr {

    private final PropiedadesOcr propiedades;

    public ServicioOcr(PropiedadesOcr propiedades) {
        this.propiedades = propiedades;
    }

    public String reconocer(BufferedImage imagen) {
        Tesseract tesseract = new Tesseract();
        if (propiedades.tessdataPath() != null && !propiedades.tessdataPath().isBlank()) {
            tesseract.setDatapath(propiedades.tessdataPath());
        }
        tesseract.setLanguage(propiedades.idioma());
        try {
            return tesseract.doOCR(imagen).trim();
        } catch (TesseractException e) {
            throw new OcrException("No se pudo leer el texto de la página escaneada: " + e.getMessage(), e);
        }
    }

    public static class OcrException extends RuntimeException {
        public OcrException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
