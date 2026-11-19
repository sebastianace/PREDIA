package co.predia.clientes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClienteDto(Long id, String nombre, String email, String telefono, BigDecimal ingresoFamiliar,
                         BigDecimal ahorro, BigDecimal deudasMensuales, Intencion intencion, short habitacionesMin,
                         List<String> zonas, String preferencias, boolean autorizacionDatos,
                         Instant autorizacionFecha) {

    public static ClienteDto desde(Cliente c) {
        return new ClienteDto(c.getId(), c.getNombre(), c.getEmail(), c.getTelefono(), c.getIngresoFamiliar(),
                c.getAhorro(), c.getDeudasMensuales(), c.getIntencion(), c.getHabitacionesMin(),
                List.copyOf(c.getZonas()), c.getPreferencias(), c.isAutorizacionDatos(), c.getAutorizacionFecha());
    }
}
