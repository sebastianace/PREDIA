package co.predia.inventario.foto;

/**
 * Lo que la interfaz necesita saber de la foto de un inmueble. {@code version} es el hash de la imagen: cambia cuando se
 * sube otra, y así el navegador reutiliza la que ya descargó mientras no cambie.
 */
public record FotoInfo(String version, int focoX, int focoY, int ancho, int alto) {
}
