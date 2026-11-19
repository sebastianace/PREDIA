package co.predia.inventario;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Datos para crear o editar un inmueble. El precio que corresponde a la operación es obligatorio. */
public record SolicitudInmueble(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 150) String titulo,
        @NotNull TipoInmueble tipo,
        @NotBlank @Size(max = 200) String direccion,
        @NotBlank @Size(max = 100) String barrio,
        @NotBlank @Size(max = 100) String ciudad,
        @Min(1) @Max(6) Integer estrato,
        @NotNull @DecimalMin("1") BigDecimal areaM2,
        @NotNull @Min(0) @Max(30) Integer habitaciones,
        @NotNull @Min(0) @Max(30) Integer banos,
        @NotNull @Min(0) @Max(30) Integer parqueaderos,
        boolean aceptaMascotas,
        @DecimalMin("0") BigDecimal valorAdministracion,
        @NotNull Operacion operacion,
        @DecimalMin("1") BigDecimal precioVenta,
        @DecimalMin("1") BigDecimal canonArriendo,
        boolean esVis,
        boolean propiedadHorizontal,
        @Size(max = 4000) String descripcion,
        @NotNull EstadoInmueble estado) {
}
