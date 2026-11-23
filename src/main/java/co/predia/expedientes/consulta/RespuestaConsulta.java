package co.predia.expedientes.consulta;

import java.util.List;

/** {@code aviso} (puede ser null) avisa de algo que afecta la calidad de la búsqueda, como vectores de otro modelo. */
public record RespuestaConsulta(String respuesta, List<Cita> citas, boolean sinEvidencia, boolean modoManual,
                                String aviso) {
}
