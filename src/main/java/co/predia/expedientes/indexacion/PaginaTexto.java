package co.predia.expedientes.indexacion;

/** El texto de una página, ya sea extraído directamente o reconocido por OCR. */
public record PaginaTexto(int numero, String texto, boolean esOcr) {
}
