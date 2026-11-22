package co.predia.expedientes.indexacion;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el OCR con una imagen generada en la propia prueba (sin archivos binarios en el repo).
 * Usa el Tesseract instalado en la máquina (ver README: brew install tesseract tesseract-lang).
 */
class ServicioOcrTest {

    @Test
    void reconoceUnaPalabraClaraEnMayusculas() {
        PropiedadesOcr propiedades = new PropiedadesOcr(
                System.getenv().getOrDefault("TESSDATA_PREFIX", "/opt/homebrew/share/tessdata"), "spa", 300f);
        ServicioOcr servicio = new ServicioOcr(propiedades);

        BufferedImage imagen = imagenConTexto("CERTIFICADO DE TRADICION");

        String texto = servicio.reconocer(imagen);

        assertThat(texto.toUpperCase()).contains("CERTIFICADO");
    }

    private static BufferedImage imagenConTexto(String texto) {
        BufferedImage imagen = new BufferedImage(1600, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 60));
        g.drawString(texto, 40, 150);
        g.dispose();
        return imagen;
    }
}
