package co.predia.expedientes.indexacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Tesseract (tess4j). En macOS: brew install tesseract tesseract-lang y
 * export TESSDATA_PREFIX=/opt/homebrew/share/tessdata (ver README).
 */
@ConfigurationProperties("predia.ocr")
public record PropiedadesOcr(String tessdataPath, String idioma, float dpi) {

    public PropiedadesOcr {
        if (idioma == null || idioma.isBlank()) {
            idioma = "spa";
        }
        if (dpi <= 0) {
            dpi = 300f;
        }
    }
}
