package co.predia.inventario;

import co.predia.inventario.foto.FotoInfo;

import java.math.BigDecimal;

/** Vista de un inmueble para la API (sin el vector de embedding). */
public record InmuebleDto(
        Long id, String codigo, String titulo, TipoInmueble tipo, String direccion, String barrio, String ciudad,
        Short estrato, BigDecimal areaM2, short habitaciones, short banos, short parqueaderos,
        boolean aceptaMascotas, BigDecimal valorAdministracion, Operacion operacion, BigDecimal precioVenta,
        BigDecimal canonArriendo, boolean esVis, boolean propiedadHorizontal, String descripcion,
        EstadoInmueble estado, FotoInfo foto, String planoVersion) {

    public static InmuebleDto desde(Inmueble i) {
        return new InmuebleDto(i.getId(), i.getCodigo(), i.getTitulo(), i.getTipo(), i.getDireccion(), i.getBarrio(),
                i.getCiudad(), i.getEstrato(), i.getAreaM2(), i.getHabitaciones(), i.getBanos(), i.getParqueaderos(),
                i.isAceptaMascotas(), i.getValorAdministracion(), i.getOperacion(), i.getPrecioVenta(),
                i.getCanonArriendo(), i.isEsVis(), i.isPropiedadHorizontal(), i.getDescripcion(), i.getEstado(), null, null);
    }

    /** La misma vista con la información de su foto (null si no tiene). */
    public InmuebleDto conFoto(FotoInfo foto) {
        return new InmuebleDto(id, codigo, titulo, tipo, direccion, barrio, ciudad, estrato, areaM2, habitaciones, banos,
                parqueaderos, aceptaMascotas, valorAdministracion, operacion, precioVenta, canonArriendo, esVis,
                propiedadHorizontal, descripcion, estado, foto, planoVersion);
    }

    /** La misma vista con la versión de su plano (null si no tiene). */
    public InmuebleDto conPlano(String planoVersion) {
        return new InmuebleDto(id, codigo, titulo, tipo, direccion, barrio, ciudad, estrato, areaM2, habitaciones, banos,
                parqueaderos, aceptaMascotas, valorAdministracion, operacion, precioVenta, canonArriendo, esVis,
                propiedadHorizontal, descripcion, estado, foto, planoVersion);
    }
}
