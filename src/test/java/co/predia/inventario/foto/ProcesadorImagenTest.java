package co.predia.inventario.foto;

import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.foto.ProcesadorImagen.Imagen;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcesadorImagenTest {

    private static byte[] imagen(int ancho, int alto, String formato, int tipo) throws IOException {
        BufferedImage i = new BufferedImage(ancho, alto, tipo);
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(30, 90, 200));
        g.fillRect(0, 0, ancho, alto);
        g.setColor(Color.ORANGE);
        g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(i, formato, salida);
        return salida.toByteArray();
    }

    private static BufferedImage decodificar(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void unaFotoHorizontalGrandeSeReduceA1600ConservandoLaProporcion() throws IOException {
        Imagen r = ProcesadorImagen.procesar(imagen(4000, 3000, "jpg", BufferedImage.TYPE_INT_RGB));

        assertThat(r.ancho()).isEqualTo(1600);
        assertThat(r.alto()).isEqualTo(1200);
        BufferedImage grande = decodificar(r.grande());
        assertThat(grande.getWidth()).isEqualTo(1600);
        BufferedImage mini = decodificar(r.miniatura());
        assertThat(mini.getWidth()).isEqualTo(480);
        assertThat(mini.getHeight()).isEqualTo(360);
    }

    @Test
    void unaFotoVerticalDeCelularTambienQuedaDentroDeLosTopesSinDeformarse() throws IOException {
        Imagen r = ProcesadorImagen.procesar(imagen(3024, 4032, "jpg", BufferedImage.TYPE_INT_RGB));

        assertThat(r.ancho()).isEqualTo(1200);
        assertThat(r.alto()).isEqualTo(1600);
        BufferedImage mini = decodificar(r.miniatura());
        assertThat(mini.getHeight()).isEqualTo(480);
        assertThat(mini.getWidth()).isEqualTo(360);
    }

    @Test
    void unaImagenPequenaNoSeAmplia() throws IOException {
        Imagen r = ProcesadorImagen.procesar(imagen(300, 200, "png", BufferedImage.TYPE_INT_RGB));

        assertThat(r.ancho()).isEqualTo(300);
        assertThat(r.alto()).isEqualTo(200);
        assertThat(decodificar(r.miniatura()).getWidth()).isEqualTo(300);
    }

    @Test
    void unaImagenCuadradaYUnaMuyAlargadaSeAceptan() throws IOException {
        Imagen cuadrada = ProcesadorImagen.procesar(imagen(2000, 2000, "png", BufferedImage.TYPE_INT_RGB));
        Imagen panoramica = ProcesadorImagen.procesar(imagen(6000, 500, "jpg", BufferedImage.TYPE_INT_RGB));

        assertThat(cuadrada.ancho()).isEqualTo(1600);
        assertThat(cuadrada.alto()).isEqualTo(1600);
        assertThat(panoramica.ancho()).isEqualTo(1600);
        assertThat(panoramica.alto()).isEqualTo(133);
    }

    @Test
    void loTransparenteDeUnPngQuedaSobreBlancoYNoNegro() throws IOException {
        BufferedImage transparente = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);   // todo transparente
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(transparente, "png", salida);

        Imagen r = ProcesadorImagen.procesar(salida.toByteArray());

        int pixel = decodificar(r.grande()).getRGB(10, 10);
        assertThat(new Color(pixel).getRed()).isGreaterThan(240);
        assertThat(new Color(pixel).getBlue()).isGreaterThan(240);
    }

    @Test
    void elResultadoSiempreEsJpegAunqueLlegueUnPngOUnBmp() throws IOException {
        for (String formato : new String[]{"png", "bmp", "gif"}) {
            Imagen r = ProcesadorImagen.procesar(imagen(640, 480, formato, BufferedImage.TYPE_INT_RGB));
            assertThat(r.grande()[0] & 0xFF).as(formato).isEqualTo(0xFF);   // cabecera JPEG: FF D8
            assertThat(r.grande()[1] & 0xFF).as(formato).isEqualTo(0xD8);
        }
    }

    @Test
    void rechazaLoQueNoEsUnaImagenAunqueSeLlameJpg() {
        byte[] texto = "esto es un contrato, no una foto".getBytes(StandardCharsets.UTF_8);
        byte[] pdf = "%PDF-1.4 falso".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ProcesadorImagen.procesar(texto)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no es una imagen válida");
        assertThatThrownBy(() -> ProcesadorImagen.procesar(pdf)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> ProcesadorImagen.procesar(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3}))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazaUnaImagenQueOcuparaDemasiadaMemoria() throws IOException {
        // 1 bit por píxel: pesa poco en bytes pero tiene 64 megapíxeles.
        byte[] enorme = imagen(8000, 8000, "png", BufferedImage.TYPE_BYTE_BINARY);

        assertThatThrownBy(() -> ProcesadorImagen.procesar(enorme)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("demasiado grande");
    }

    @Test
    void elHashCambiaConOtraImagenYSeRepiteConLaMisma() throws IOException {
        byte[] a = imagen(800, 600, "png", BufferedImage.TYPE_INT_RGB);
        byte[] b = imagen(801, 600, "png", BufferedImage.TYPE_INT_RGB);

        assertThat(ProcesadorImagen.procesar(a).hash()).isEqualTo(ProcesadorImagen.procesar(a).hash())
                .isNotEqualTo(ProcesadorImagen.procesar(b).hash()).hasSize(64);
    }
}
