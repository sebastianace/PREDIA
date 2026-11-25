package co.predia.clientes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClienteDto(Long id, String nombre, String email, String telefono, BigDecimal ingresoFamiliar,
                         BigDecimal ahorro, BigDecimal deudasMensuales, Intencion intencion, short habitacionesMin,
                         List<String> zonas, String preferencias, boolean autorizacionDatos,
                         Instant autorizacionFecha, BigDecimal topeDeCompra, String limitadoPor) {

    /**
     * Sin tope: se usa al crear o editar. {@code topeDeCompra} y {@code limitadoPor} (AHORRO o CAPACIDAD_DE_PAGO) solo
     * van en la lista, y salen de {@link CalculadoraPresupuesto} con los parámetros de la inmobiliaria.
     */
    public static ClienteDto desde(Cliente c) {
        return desde(c, null);
    }

    public static ClienteDto desde(Cliente c, CalculadoraPresupuesto.Presupuesto presupuesto) {
        return new ClienteDto(c.getId(), c.getNombre(), c.getEmail(), c.getTelefono(), c.getIngresoFamiliar(),
                c.getAhorro(), c.getDeudasMensuales(), c.getIntencion(), c.getHabitacionesMin(),
                List.copyOf(c.getZonas()), c.getPreferencias(), c.isAutorizacionDatos(), c.getAutorizacionFecha(),
                presupuesto == null ? null : presupuesto.topeDeCompra(),
                presupuesto == null ? null : presupuesto.limitante().name());
    }
}
