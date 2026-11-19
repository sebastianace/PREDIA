package co.predia.expedientes.indexacion;

/**
 * Lo que sugiere la IA al clasificar un documento, a partir solo de sus primeros fragmentos.
 * fechaExpedicion va en texto (AAAA-MM-DD) para no obligar al modelo a un formato de fecha nativo;
 * se interpreta y valida en Java.
 */
public record ClasificacionSugerida(TipoDocumentoSugerido tipoDocumento, String fechaExpedicion, double confianza) {
}
