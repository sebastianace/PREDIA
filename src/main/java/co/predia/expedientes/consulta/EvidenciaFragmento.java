package co.predia.expedientes.consulta;

/** Un fragmento recuperado, con lo necesario para mostrarlo y citarlo (documento, página, sección). */
public record EvidenciaFragmento(Long fragmentoId, Long documentoId, String nombreArchivo, int paginaInicio,
                                 int paginaFin, String seccion, String texto, double similitudVectorial) {
}
