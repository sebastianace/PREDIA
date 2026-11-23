package co.predia.inventario.foto;

import co.predia.comun.web.ReglaNegocioException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * Convierte lo que sube el asesor en dos JPEG reducidos. Nunca se conserva el archivo original: lo que llega se decodifica
 * (así se descarta cualquier cosa que no sea una imagen real), se aplana sobre blanco, se reduce y se vuelve a codificar,
 * lo que además elimina los metadatos (ubicación GPS, cámara). Acepta JPEG, PNG, GIF y BMP; la interfaz convierte antes
 * cualquier otro formato que el navegador sepa leer.
 */
public final class ProcesadorImagen {

    public static final int LADO_GRANDE = 1600;
    public static final int LADO_MINIATURA = 480;
    /** Tope de píxeles decodificados: evita que una imagen diminuta en bytes pero enorme en píxeles agote la memoria. */
    static final long MAX_PIXELES = 50_000_000L;
    private static final float CALIDAD = 0.82f;

    public record Imagen(byte[] grande, byte[] miniatura, int ancho, int alto, String hash) {
    }

    private ProcesadorImagen() {
    }

    public static Imagen procesar(byte[] original) {
        BufferedImage origen = leer(original);
        BufferedImage grande = reducir(origen, LADO_GRANDE);
        BufferedImage miniatura = reducir(origen, LADO_MINIATURA);
        byte[] bytesGrande = codificar(grande);
        return new Imagen(bytesGrande, codificar(miniatura), grande.getWidth(), grande.getHeight(), sha256(bytesGrande));
    }

    private static BufferedImage leer(byte[] bytes) {
        try (ImageInputStream flujo = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(flujo);
            if (!lectores.hasNext()) {
                throw invalida();
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(flujo, true, true);
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > MAX_PIXELES) {
                    throw new ReglaNegocioException("La imagen es demasiado grande (más de 50 megapíxeles). Reduce su tamaño.");
                }
                BufferedImage imagen = lector.read(0);
                if (imagen == null || imagen.getWidth() < 1 || imagen.getHeight() < 1) {
                    throw invalida();
                }
                return imagen;
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ReglaNegocioException regla) {
                throw regla;
            }
            throw invalida();
        }
    }

    private static ReglaNegocioException invalida() {
        return new ReglaNegocioException("El archivo no es una imagen válida. Sube una foto JPG o PNG.");
    }

    /** Reduce por mitades (más nítido que un salto grande) y nunca amplía: una foto pequeña se queda como está. */
    static BufferedImage reducir(BufferedImage origen, int ladoMaximo) {
        int ancho = origen.getWidth();
        int alto = origen.getHeight();
        double escala = Math.min(1.0, (double) ladoMaximo / Math.max(ancho, alto));
        int destinoAncho = Math.max(1, (int) Math.round(ancho * escala));
        int destinoAlto = Math.max(1, (int) Math.round(alto * escala));

        BufferedImage actual = aRgb(origen);
        while (actual.getWidth() / 2 >= destinoAncho && actual.getHeight() / 2 >= destinoAlto) {
            actual = redimensionar(actual, actual.getWidth() / 2, actual.getHeight() / 2);
        }
        if (actual.getWidth() != destinoAncho || actual.getHeight() != destinoAlto) {
            actual = redimensionar(actual, destinoAncho, destinoAlto);
        }
        return actual;
    }

    private static BufferedImage aRgb(BufferedImage origen) {
        if (origen.getType() == BufferedImage.TYPE_INT_RGB) {
            return origen;
        }
        BufferedImage rgb = new BufferedImage(origen.getWidth(), origen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);              // lo transparente (PNG) queda sobre blanco, no negro
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(origen, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage redimensionar(BufferedImage origen, int ancho, int alto) {
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(origen, 0, 0, ancho, alto, null);
        g.dispose();
        return destino;
    }

    private static byte[] codificar(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream flujo = new MemoryCacheImageOutputStream(salida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(CALIDAD);
            parametros.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
            flujo.flush();
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo codificar la imagen.", e);
        } finally {
            escritor.dispose();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
