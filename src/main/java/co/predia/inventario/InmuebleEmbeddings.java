package co.predia.inventario;

import co.predia.comun.ia.EstadoIa;
import co.predia.comun.ia.ProveedorEmbeddings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mantiene el vector (pgvector) con el que se ordena la búsqueda de inmuebles por similitud. Si no hay IA,
 * o si falla, el inmueble queda sin vector (nunca con uno desactualizado) y la búsqueda lo ordena por precio.
 */
@Component
public class InmuebleEmbeddings {

    private static final Logger log = LoggerFactory.getLogger(InmuebleEmbeddings.class);
    private static final int TAMANO_LOTE = 64;

    private final JdbcTemplate jdbc;
    private final InmuebleRepository inmuebles;
    private final ProveedorEmbeddings proveedor;
    private final EstadoIa estadoIa;

    public InmuebleEmbeddings(JdbcTemplate jdbc, InmuebleRepository inmuebles, ProveedorEmbeddings proveedor,
                              EstadoIa estadoIa) {
        this.jdbc = jdbc;
        this.inmuebles = inmuebles;
        this.proveedor = proveedor;
        this.estadoIa = estadoIa;
    }

    /** Recalcula el vector de un inmueble recién creado o editado. */
    public void actualizar(Inmueble inmueble) {
        if (!estadoIa.disponible()) {
            borrarVector(inmueble.getId(), inmueble.getInmobiliariaId());
            return;
        }
        try {
            guardar(inmueble, proveedor.generar(List.of(texto(inmueble))).get(0));
        } catch (Exception e) {
            log.warn("No se pudo calcular el vector del inmueble {}: {}", inmueble.getId(), e.getMessage());
            borrarVector(inmueble.getId(), inmueble.getInmobiliariaId());
        }
    }

    /** Calcula, por lotes, el vector de los inmuebles de la inmobiliaria que todavía no lo tienen. */
    public void asegurar(Long inmobiliariaId, List<Long> ids) {
        if (ids.isEmpty() || !estadoIa.disponible()) {
            return;
        }
        List<Inmueble> pendientes = new ArrayList<>(inmuebles.findAllById(ids).stream()
                .filter(i -> i.getInmobiliariaId().equals(inmobiliariaId)).toList());
        for (int desde = 0; desde < pendientes.size(); desde += TAMANO_LOTE) {
            List<Inmueble> lote = pendientes.subList(desde, Math.min(desde + TAMANO_LOTE, pendientes.size()));
            List<float[]> vectores = proveedor.generar(lote.stream().map(InmuebleEmbeddings::texto).toList());
            for (int i = 0; i < lote.size(); i++) {
                guardar(lote.get(i), vectores.get(i));
            }
        }
    }

    /** El texto que se vectoriza: lo que un cliente podría pedir, escrito de forma natural. */
    static String texto(Inmueble i) {
        StringBuilder sb = new StringBuilder();
        sb.append(i.getTitulo()).append(". ").append(i.getTipo().name().toLowerCase()).append(" en ")
                .append(i.getBarrio()).append(", ").append(i.getCiudad()).append(". ")
                .append(i.getHabitaciones()).append(" habitaciones, ").append(i.getBanos()).append(" baños, ")
                .append(i.getParqueaderos() == 0 ? "sin parqueadero" : i.getParqueaderos() + " parqueaderos")
                .append(", ").append(i.getAreaM2().stripTrailingZeros().toPlainString()).append(" m². ");
        if (i.getEstrato() != null) {
            sb.append("Estrato ").append(i.getEstrato()).append(". ");
        }
        sb.append(i.isAceptaMascotas() ? "Acepta mascotas. " : "No acepta mascotas. ");
        sb.append(i.getDescripcion());
        return sb.toString();
    }

    private void guardar(Inmueble inmueble, float[] vector) {
        jdbc.update("UPDATE inmueble SET embedding = ?::vector, embedding_modelo = ? "
                        + "WHERE id = ? AND inmobiliaria_id = ?",
                aTextoVector(vector), estadoIa.modeloEmbeddings(), inmueble.getId(), inmueble.getInmobiliariaId());
    }

    private void borrarVector(Long id, Long inmobiliariaId) {
        jdbc.update("UPDATE inmueble SET embedding = NULL, embedding_modelo = NULL "
                + "WHERE id = ? AND inmobiliaria_id = ?", id, inmobiliariaId);
    }

    static String aTextoVector(float[] valores) {
        StringBuilder sb = new StringBuilder(valores.length * 10).append('[');
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(valores[i]);
        }
        return sb.append(']').toString();
    }
}
