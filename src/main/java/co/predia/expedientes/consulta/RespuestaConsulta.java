package co.predia.expedientes.consulta;

import java.util.List;

public record RespuestaConsulta(String respuesta, List<Cita> citas, boolean sinEvidencia, boolean modoManual) {
}
