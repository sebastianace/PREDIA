package co.predia.expedientes.indexacion;

import co.predia.comun.ia.EstadoIa;
import co.predia.comun.ia.ProveedorEmbeddings;
import co.predia.comun.web.FalloDeIaException;
import co.predia.comun.web.IaNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Vuelve a calcular los vectores de los fragmentos de una inmobiliaria con el modelo de embeddings actual, a partir del
 * texto ya guardado: no hace falta volver a subir ningún PDF (eso solo es necesario si cambia la extracción o el OCR).
 * Cubre los fragmentos indexados con otro modelo y los que quedaron sin vector por haberse indexado en modo manual.
 * Trabaja por tandas acotadas para no dejar la petición esperando minutos; se repite hasta que no queden pendientes.
 */
@Service
public class ServicioRegeneracionVectores {

    private static final Logger log = LoggerFactory.getLogger(ServicioRegeneracionVectores.class);
    static final int POR_LLAMADA = 300;
    static final int LOTE = 50;

    public record Resultado(int regenerados, int pendientes, String modelo) {
    }

    private final JdbcTemplate jdbc;
    private final EstadoIa estadoIa;
    private final ProveedorEmbeddings proveedor;

    public ServicioRegeneracionVectores(JdbcTemplate jdbc, EstadoIa estadoIa, ProveedorEmbeddings proveedor) {
        this.jdbc = jdbc;
        this.estadoIa = estadoIa;
        this.proveedor = proveedor;
    }

    public Resultado regenerar(Long inmobiliariaId) {
        if (!estadoIa.disponible()) {
            throw new IaNoDisponibleException("La IA no está disponible: no se pueden regenerar los vectores.");
        }
        String modelo = proveedor.modelo();
        List<Map<String, Object>> pendientes = jdbc.queryForList("""
                SELECT id, texto FROM fragmento
                WHERE inmobiliaria_id = ? AND (embedding IS NULL OR embedding_modelo IS DISTINCT FROM ?)
                ORDER BY id LIMIT ?
                """, inmobiliariaId, modelo, POR_LLAMADA);

        int regenerados = 0;
        try {
            for (int desde = 0; desde < pendientes.size(); desde += LOTE) {
                List<Map<String, Object>> lote = pendientes.subList(desde, Math.min(desde + LOTE, pendientes.size()));
                List<float[]> vectores = proveedor.generar(lote.stream().map(f -> (String) f.get("texto")).toList());
                for (int i = 0; i < lote.size(); i++) {
                    jdbc.update("UPDATE fragmento SET embedding = ?::vector, embedding_modelo = ? "
                                    + "WHERE id = ? AND inmobiliaria_id = ?",
                            aTexto(vectores.get(i)), modelo, ((Number) lote.get(i).get("id")).longValue(), inmobiliariaId);
                    regenerados++;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Se regeneraron {} vectores antes de que fallara el proveedor: {}", regenerados,
                    e.getClass().getSimpleName());
            throw new FalloDeIaException("El proveedor de IA falló a mitad de la regeneración. Lo ya regenerado "
                    + "se conserva; vuelve a intentarlo.", e);
        }

        // Un documento pasa a «vectorizado» cuando ya ninguno de sus fragmentos le falta el vector.
        jdbc.update("""
                UPDATE documento d SET vectorizado = TRUE
                WHERE d.inmobiliaria_id = ? AND NOT d.vectorizado
                  AND NOT EXISTS (SELECT 1 FROM fragmento f WHERE f.documento_id = d.id AND f.embedding IS NULL)
                """, inmobiliariaId);

        Integer quedan = jdbc.queryForObject("""
                SELECT count(*) FROM fragmento
                WHERE inmobiliaria_id = ? AND (embedding IS NULL OR embedding_modelo IS DISTINCT FROM ?)
                """, Integer.class, inmobiliariaId, modelo);
        return new Resultado(regenerados, quedan == null ? 0 : quedan, modelo);
    }

    private static String aTexto(float[] valores) {
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
