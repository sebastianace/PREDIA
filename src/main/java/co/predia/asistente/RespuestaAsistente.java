package co.predia.asistente;

import co.predia.expedientes.consulta.Cita;

import java.util.List;

/**
 * Lo que recibe el asesor: la respuesta, qué herramientas se usaron (para que pueda ver de dónde salió cada dato),
 * las citas de los expedientes y los borradores de recordatorio que quedaron esperando su aprobación.
 *
 * @param cifrasSinRespaldo números de la respuesta que no aparecen en lo que devolvieron las herramientas
 */
public record RespuestaAsistente(
        String conversacionId, String respuesta, List<String> herramientas, List<Cita> citas,
        List<Long> recordatoriosCreados, List<String> cifrasSinRespaldo, String aviso) {

    public static final String AVISO_CIFRAS =
            "Algunas cifras de esta respuesta no vienen de una herramienta de cálculo: verifícalas antes de usarlas.";
}
