package co.predia.inventario.foto;

/**
 * Lo que la interfaz necesita saber de una imagen de la galería de un inmueble. {@code version} es el hash de la
 * imagen: cambia si se sube otra, y así el navegador reutiliza la que ya descargó mientras no cambie.
 */
public record ImagenInfo(Long id, TipoImagen tipo, int orden, String etiqueta, String version, int focoX, int focoY,
                         int ancho, int alto) {
}
