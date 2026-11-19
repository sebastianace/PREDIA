package co.predia.expedientes.indexacion;

/** Copia inmutable de los datos de un trabajo tomado de la cola, para procesar fuera de la transacción. */
public record TrabajoClaim(Long id, Long inmobiliariaId, Long documentoId, Long inmuebleId, String rutaTemporal) {
}
