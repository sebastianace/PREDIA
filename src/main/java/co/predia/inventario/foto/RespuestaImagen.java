package co.predia.inventario.foto;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

/**
 * Cómo se entrega una imagen al navegador. Hay dos casos y se tratan distinto:
 * <ul>
 *   <li><b>La portada</b> ({@code .../foto}): la dirección es siempre la misma pero la imagen cambia cuando el asesor
 *   sube otra o cambia el orden. Si se guardara 30 días, el navegador seguiría mostrando la vieja. Se manda con
 *   {@code no-cache} y un ETag: el navegador pregunta siempre y, si no cambió, el servidor responde 304 sin bytes.</li>
 *   <li><b>Una imagen de la galería por su id</b>: ese id siempre devuelve los mismos bytes (subir otra crea otro id),
 *   así que sí puede guardarse mucho tiempo.</li>
 * </ul>
 */
public final class RespuestaImagen {

    private RespuestaImagen() {
    }

    /** La portada: siempre se revalida con el ETag (el hash de la imagen y el tamaño pedido). */
    public static ResponseEntity<byte[]> portada(byte[] contenido, String hash, boolean miniatura, String sinCambios) {
        String etag = "\"" + hash + (miniatura ? "-m" : "-g") + "\"";
        CacheControl cache = CacheControl.noCache().cachePrivate();
        if (etag.equals(sinCambios)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).eTag(etag).cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff").body(contenido);
    }

    /** Una imagen de la galería por su id: sus bytes nunca cambian, se puede guardar 30 días. */
    public static ResponseEntity<byte[]> deGaleria(byte[] contenido) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff").body(contenido);
    }
}
