package co.predia.clientes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClienteDto(Long id, String nombre, String email, String telefono, BigDecimal ingresoFamiliar,
                         BigDecimal ahorro, BigDecimal deudasMensuales, Intencion intencion, short habitacionesMin,
                         List<String> zonas, String preferencias, boolean autorizacionDatos,
                         Instant autorizacionFecha, BigDecimal topeDeCompra, String limitadoPor,
                         String fotoVersion) {

    /**
     * {@code topeDeCompra} y {@code limitadoPor} (AHORRO o CAPACIDAD_DE_PAGO) solo van en la lista, y salen de
     * {@link CalculadoraPresupuesto} con los parámetros de la inmobiliaria. {@code fotoVersion} es el hash de la foto de
     * perfil (null si no tiene): con él la interfaz sabe cuándo volver a pedirla.
     */
    public static ClienteDto desde(Cliente c, CalculadoraPresupuesto.Presupuesto presupuesto, String fotoVersion) {
        return new ClienteDto(c.getId(), c.getNombre(), c.getEmail(), c.getTelefono(), c.getIngresoFamiliar(),
                c.getAhorro(), c.getDeudasMensuales(), c.getIntencion(), c.getHabitacionesMin(),
                List.copyOf(c.getZonas()), c.getPreferencias(), c.isAutorizacionDatos(), c.getAutorizacionFecha(),
                presupuesto == null ? null : presupuesto.topeDeCompra(),
                presupuesto == null ? null : presupuesto.limitante().name(), fotoVersion);
    }
}
