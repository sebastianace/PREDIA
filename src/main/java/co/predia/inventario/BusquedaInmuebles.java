package co.predia.inventario;

import co.predia.comun.ia.EstadoIa;
import co.predia.comun.ia.ProveedorEmbeddings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Búsqueda de inmuebles en dos pasos. Primero, SQL filtra lo no negociable: solo DISPONIBLE, de la
 * inmobiliaria del usuario, dentro del presupuesto y con las habitaciones pedidas. Después, pgvector ordena
 * lo que quedó por similitud con lo que pidió el cliente. Un inmueble que no esté disponible nunca se ofrece.
 */
@Service
public class BusquedaInmuebles {

    private static final Logger log = LoggerFactory.getLogger(BusquedaInmuebles.class);

    private final JdbcTemplate jdbc;
    private final InmuebleRepository inmuebles;
    private final InmuebleEmbeddings embeddings;
    private final ProveedorEmbeddings proveedor;
    private final EstadoIa estadoIa;

    public BusquedaInmuebles(JdbcTemplate jdbc, InmuebleRepository inmuebles, InmuebleEmbeddings embeddings,
                             ProveedorEmbeddings proveedor, EstadoIa estadoIa) {
        this.jdbc = jdbc;
        this.inmuebles = inmuebles;
        this.embeddings = embeddings;
        this.proveedor = proveedor;
        this.estadoIa = estadoIa;
    }

    public record Solicitud(Operacion operacion, BigDecimal precioMax, Integer habitacionesMin, String ciudad,
                            String descripcion) {
    }

    public record Resultado(InmuebleDto inmueble, Integer coincidenciaPct) {
    }

    public record Respuesta(List<Resultado> resultados, boolean ordenadoPorSimilitud, String aviso) {
    }

    private record Filtro(String where, List<Object> argumentos) {
    }

    public Respuesta buscar(Long inmobiliariaId, Solicitud s) {
        Operacion operacion = s.operacion() == null ? Operacion.VENTA : s.operacion();
        Filtro filtro = construirFiltro(inmobiliariaId, operacion, s);
        String descripcion = s.descripcion() == null ? "" : s.descripcion().strip();

        Map<Long, Double> similitudes = new LinkedHashMap<>();
        boolean porSimilitud = false;
        String aviso = null;

        if (!descripcion.isEmpty() && estadoIa.disponible()) {
            try {
                // Un vector de otro modelo no se puede comparar con el de la descripción: se recalcula como si faltara.
                List<Object> argsPendientes = new ArrayList<>(filtro.argumentos());
                argsPendientes.add(estadoIa.modeloEmbeddings());
                List<Long> sinVector = jdbc.queryForList(
                        "SELECT id FROM inmueble WHERE " + filtro.where()
                                + " AND (embedding IS NULL OR embedding_modelo IS DISTINCT FROM ?)", Long.class,
                        argsPendientes.toArray());
                embeddings.asegurar(inmobiliariaId, sinVector);
                String vector = InmuebleEmbeddings.aTextoVector(proveedor.generar(List.of(descripcion)).get(0));

                List<Object> args = new ArrayList<>();
                args.add(vector);
                args.addAll(filtro.argumentos());
                args.add(vector);
                jdbc.query("SELECT id, 1 - (embedding <=> ?::vector) AS similitud FROM inmueble WHERE "
                                + filtro.where() + " ORDER BY embedding <=> ?::vector NULLS LAST, "
                                + "COALESCE(precio_venta, canon_arriendo), id",
                        rs -> {
                            long id = rs.getLong("id");
                            double similitud = rs.getDouble("similitud");
                            // wasNull() se refiere a la última columna leída: tiene que ser la similitud.
                            boolean sinVectorAsociado = rs.wasNull();
                            similitudes.put(id, sinVectorAsociado ? null : similitud);
                        }, args.toArray());
                porSimilitud = true;
            } catch (Exception e) {
                log.warn("No se pudo ordenar por similitud; se ordena por precio: {}", e.getMessage());
                similitudes.clear();
                aviso = "No se pudo ordenar por similitud; los resultados van ordenados por precio.";
            }
        } else if (!descripcion.isEmpty()) {
            aviso = "La IA no está disponible: los resultados van ordenados por precio, sin porcentaje de coincidencia.";
        }

        if (!porSimilitud) {
            jdbc.query("SELECT id FROM inmueble WHERE " + filtro.where() + " ORDER BY COALESCE(precio_venta, "
                            + "canon_arriendo), id",
                    rs -> {
                        similitudes.put(rs.getLong("id"), null);
                    }, filtro.argumentos().toArray());
        }

        Map<Long, Inmueble> porId = new LinkedHashMap<>();
        inmuebles.findAllById(similitudes.keySet()).forEach(i -> porId.put(i.getId(), i));
        List<Resultado> resultados = new ArrayList<>();
        similitudes.forEach((id, similitud) -> {
            Inmueble inmueble = porId.get(id);
            if (inmueble != null) {
                resultados.add(new Resultado(InmuebleDto.desde(inmueble), porcentaje(similitud)));
            }
        });
        return new Respuesta(resultados, porSimilitud, aviso);
    }

    private static Filtro construirFiltro(Long inmobiliariaId, Operacion operacion, Solicitud s) {
        StringBuilder where = new StringBuilder("inmobiliaria_id = ? AND estado = 'DISPONIBLE' AND operacion = ?");
        List<Object> args = new ArrayList<>(List.of(inmobiliariaId, operacion.name()));
        if (s.precioMax() != null) {
            // El nombre de la columna sale de un enum, nunca del texto que envía el usuario.
            where.append(" AND ").append(operacion == Operacion.VENTA ? "precio_venta" : "canon_arriendo")
                    .append(" <= ?");
            args.add(s.precioMax());
        }
        if (s.habitacionesMin() != null && s.habitacionesMin() > 0) {
            where.append(" AND habitaciones >= ?");
            args.add(s.habitacionesMin());
        }
        if (s.ciudad() != null && !s.ciudad().isBlank()) {
            where.append(" AND lower(ciudad) = lower(?)");
            args.add(s.ciudad().strip());
        }
        return new Filtro(where.toString(), args);
    }

    private static Integer porcentaje(Double similitud) {
        if (similitud == null) {
            return null;
        }
        return (int) Math.max(0, Math.min(100, Math.round(similitud * 100)));
    }
}
