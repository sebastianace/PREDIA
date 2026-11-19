package co.predia.expedientes;

import co.predia.expedientes.indexacion.TipoDocumentoSugerido;

import java.time.LocalDate;

/**
 * Se publica cuando un documento de un negocio queda clasificado (por la IA, como sugerencia, o corregido por el
 * asesor). Así el módulo de cierre puede sugerir el requisito que corresponde sin que el de expedientes lo conozca.
 */
public record DocumentoClasificado(Long documentoId, Long inmobiliariaId, Long negocioId,
                                   TipoDocumentoSugerido tipo, LocalDate fechaExpedicion) {
}
