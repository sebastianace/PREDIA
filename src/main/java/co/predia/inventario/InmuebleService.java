package co.predia.inventario;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import org.springframework.stereotype.Service;

/**
 * Crea y edita inmuebles. No es transaccional a propósito: el cálculo del vector (una llamada externa)
 * ocurre después de guardar, sin mantener abierta una transacción de base de datos.
 */
@Service
public class InmuebleService {

    private final InmuebleRepository inmuebles;
    private final InmuebleEmbeddings embeddings;

    public InmuebleService(InmuebleRepository inmuebles, InmuebleEmbeddings embeddings) {
        this.inmuebles = inmuebles;
        this.embeddings = embeddings;
    }

    public Inmueble crear(Long inmobiliariaId, SolicitudInmueble s) {
        String codigo = s.codigo().strip();
        if (inmuebles.existsByInmobiliariaIdAndCodigo(inmobiliariaId, codigo)) {
            throw new ReglaNegocioException("Ya existe un inmueble con el código «%s».".formatted(codigo));
        }
        validarPrecio(s);
        Inmueble inmueble = new Inmueble(inmobiliariaId, codigo, s.titulo().strip(), s.tipo(), s.operacion());
        aplicar(inmueble, s);
        Inmueble guardado = inmuebles.save(inmueble);
        embeddings.actualizar(guardado);
        return guardado;
    }

    public Inmueble actualizar(Long inmobiliariaId, Long id, SolicitudInmueble s) {
        Inmueble inmueble = inmuebles.findByIdAndInmobiliariaId(id, inmobiliariaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", id));
        String codigo = s.codigo().strip();
        if (!codigo.equals(inmueble.getCodigo()) && inmuebles.existsByInmobiliariaIdAndCodigo(inmobiliariaId, codigo)) {
            throw new ReglaNegocioException("Ya existe un inmueble con el código «%s».".formatted(codigo));
        }
        validarPrecio(s);
        inmueble.setCodigo(codigo);
        inmueble.setTitulo(s.titulo().strip());
        inmueble.setTipo(s.tipo());
        inmueble.setOperacion(s.operacion());
        aplicar(inmueble, s);
        Inmueble guardado = inmuebles.save(inmueble);
        embeddings.actualizar(guardado);
        return guardado;
    }

    private static void validarPrecio(SolicitudInmueble s) {
        if (s.operacion() == Operacion.VENTA && s.precioVenta() == null) {
            throw new ReglaNegocioException("Un inmueble en venta necesita su precio de venta.");
        }
        if (s.operacion() == Operacion.ARRIENDO && s.canonArriendo() == null) {
            throw new ReglaNegocioException("Un inmueble en arriendo necesita su canon.");
        }
    }

    private static void aplicar(Inmueble i, SolicitudInmueble s) {
        i.setDireccion(s.direccion().strip());
        i.setBarrio(s.barrio().strip());
        i.setCiudad(s.ciudad().strip());
        i.setEstrato(s.estrato() == null ? null : s.estrato().shortValue());
        i.setAreaM2(s.areaM2());
        i.setHabitaciones(s.habitaciones().shortValue());
        i.setBanos(s.banos().shortValue());
        i.setParqueaderos(s.parqueaderos().shortValue());
        i.setAceptaMascotas(s.aceptaMascotas());
        i.setValorAdministracion(s.valorAdministracion() == null ? java.math.BigDecimal.ZERO : s.valorAdministracion());
        i.setPrecioVenta(s.operacion() == Operacion.VENTA ? s.precioVenta() : null);
        i.setCanonArriendo(s.operacion() == Operacion.ARRIENDO ? s.canonArriendo() : null);
        i.setEsVis(s.esVis());
        i.setPropiedadHorizontal(s.propiedadHorizontal());
        i.setDescripcion(s.descripcion() == null ? "" : s.descripcion().strip());
        i.setEstado(s.estado());
    }
}
