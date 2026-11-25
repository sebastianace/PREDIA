package co.predia.perfil;

import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.foto.ProcesadorImagen;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El recorte y la reducción de una foto de perfil: sin Spring ni base de datos. */
class ProcesadorPerfilTest {

    /** Una imagen horizontal: mitad izquierda roja, mitad derecha azul. */
    private static byte[] horizontal(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, ancho / 2, alto);
        g.setColor(Color.BLUE);
        g.fillRect(ancho / 2, 0, ancho - ancho / 2, alto);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private static BufferedImage leer(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void laFotoQuedaCuadradaEnDosTamanosSinImportarLaForma() throws IOException {
        ProcesadorImagen.Imagen imagen = ProcesadorImagen.procesarPerfil(horizontal(1000, 600), 50, 50);

        BufferedImage grande = leer(imagen.grande());
        BufferedImage miniatura = leer(imagen.miniatura());
        assertThat(grande.getWidth()).isEqualTo(grande.getHeight()).isEqualTo(ProcesadorImagen.LADO_PERFIL);
        assertThat(miniatura.getWidth()).isEqualTo(miniatura.getHeight()).isEqualTo(ProcesadorImagen.LADO_PERFIL_MINIATURA);
        assertThat(imagen.hash()).hasSize(64);
    }

    @Test
    void elPuntoDeInteresDecideQueParteSeConserva() throws IOException {
        byte[] original = horizontal(1000, 400);

        BufferedImage izquierda = leer(ProcesadorImagen.procesarPerfil(original, 0, 50).grande());
        BufferedImage derecha = leer(ProcesadorImagen.procesarPerfil(original, 100, 50).grande());

        // A la izquierda queda el cuadrado rojo; a la derecha, el azul.
        assertThat(new Color(izquierda.getRGB(izquierda.getWidth() / 2, izquierda.getHeight() / 2)).getRed()).isGreaterThan(200);
        assertThat(new Color(derecha.getRGB(derecha.getWidth() / 2, derecha.getHeight() / 2)).getBlue()).isGreaterThan(200);
    }

    @Test
    void unaFotoPequenaNoSeAmplia() throws IOException {
        BufferedImage grande = leer(ProcesadorImagen.procesarPerfil(horizontal(200, 100), 50, 50).grande());

        assertThat(grande.getWidth()).isEqualTo(100);
    }

    @Test
    void unPuntoDeInteresFueraDeRangoSeAcotaEnVezDeFallar() throws IOException {
        ProcesadorImagen.Imagen imagen = ProcesadorImagen.procesarPerfil(horizontal(800, 400), -30, 250);

        assertThat(leer(imagen.grande()).getWidth()).isEqualTo(400);
    }

    @Test
    void loQueNoEsUnaImagenSeRechaza() {
        assertThatThrownBy(() -> ProcesadorImagen.procesarPerfil("no soy una imagen".getBytes(), 50, 50))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
