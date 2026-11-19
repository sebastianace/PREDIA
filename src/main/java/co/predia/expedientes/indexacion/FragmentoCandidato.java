package co.predia.expedientes.indexacion;

/** Un fragmento listo para guardarse: aún sin vector (se le agrega después, si hay IA disponible). */
public record FragmentoCandidato(
        int orden, int paginaInicio, int paginaFin, String seccion, String texto, int tokens, boolean esOcr) {
}
