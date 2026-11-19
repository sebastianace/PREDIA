package co.predia.expedientes.consulta;

/** Una cita que respalda la respuesta: documento, página y sección, tal como quedaron en el fragmento. */
public record Cita(int numero, Long documentoId, String nombreArchivo, int paginaInicio, int paginaFin,
                   String seccion) {

    static Cita desde(int numero, EvidenciaFragmento e) {
        return new Cita(numero, e.documentoId(), e.nombreArchivo(), e.paginaInicio(), e.paginaFin(), e.seccion());
    }
}
