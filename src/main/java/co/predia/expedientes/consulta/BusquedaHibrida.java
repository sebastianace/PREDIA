package co.predia.expedientes.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Búsqueda híbrida (vectorial + texto en español) sobre los fragmentos ya indexados, siempre filtrada
 * por inmobiliaria y por el rol del usuario (roles_permitidos). SQL explícito con JdbcTemplate: el
 * filtro de seguridad queda escrito en cada consulta, no delegado a un ORM.
 */
@Repository
public class BusquedaHibrida {

    private static final int TOP_POR_LISTA = 20;
    private static final int MAX_RESULTADOS = 5;
    private static final int MAX_POR_PAGINA = 2;

    private static final String SQL_BASE = """
            SELECT f.id, f.documento_id, d.nombre_archivo, f.pagina_inicio, f.pagina_fin, f.seccion, f.texto
            FROM fragmento f
            JOIN documento d ON d.id = f.documento_id
            WHERE f.inmobiliaria_id = ? AND ? = ANY(f.roles_permitidos)
              AND (?::bigint IS NULL OR f.inmueble_id = ?)
            """;

    private final JdbcTemplate jdbc;

    public BusquedaHibrida(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ResultadoHibrido buscar(Long inmobiliariaId, String rol, Long inmuebleId, String pregunta,
                                   float[] embeddingPregunta) {
        List<EvidenciaFragmento> porVector =
                embeddingPregunta == null ? List.of() : buscarVectorial(inmobiliariaId, rol, inmuebleId, embeddingPregunta);
        List<EvidenciaFragmento> porTexto = buscarTextual(inmobiliariaId, rol, inmuebleId, pregunta);

        Map<Long, EvidenciaFragmento> porId = new LinkedHashMap<>();
        porVector.forEach(e -> porId.put(e.fragmentoId(), e));
        porTexto.forEach(e -> porId.putIfAbsent(e.fragmentoId(), e));

        List<Long> ordenVectorial = porVector.stream().map(EvidenciaFragmento::fragmentoId).toList();
        List<Long> ordenTextual = porTexto.stream().map(EvidenciaFragmento::fragmentoId).toList();
        List<Long> fusionado = FusionRRF.fusionar(ordenVectorial, ordenTextual);

        List<EvidenciaFragmento> elegidos = new ArrayList<>();
        Map<String, Integer> usosPorPagina = new LinkedHashMap<>();
        for (Long id : fusionado) {
            if (elegidos.size() >= MAX_RESULTADOS) {
                break;
            }
            EvidenciaFragmento evidencia = porId.get(id);
            String clavePagina = evidencia.documentoId() + ":" + evidencia.paginaInicio();
            int usos = usosPorPagina.getOrDefault(clavePagina, 0);
            if (usos >= MAX_POR_PAGINA) {
                continue;
            }
            elegidos.add(evidencia);
            usosPorPagina.put(clavePagina, usos + 1);
        }

        double mejorSimilitud = porVector.isEmpty() ? 0 : porVector.get(0).similitudVectorial();
        return new ResultadoHibrido(elegidos, mejorSimilitud, !porTexto.isEmpty());
    }

    private List<EvidenciaFragmento> buscarVectorial(Long inmobiliariaId, String rol, Long inmuebleId,
                                                      float[] embedding) {
        String vector = aTextoVector(embedding);
        RowMapper<EvidenciaFragmento> mapeador = (rs, i) -> new EvidenciaFragmento(rs.getLong("id"),
                rs.getLong("documento_id"), rs.getString("nombre_archivo"), rs.getInt("pagina_inicio"),
                rs.getInt("pagina_fin"), rs.getString("seccion"), rs.getString("texto"),
                1 - rs.getDouble("distancia"));
        String sqlConSimilitud = SQL_BASE.replace("SELECT f.id,", "SELECT f.id, (f.embedding <=> ?::vector) AS distancia,")
                + " AND f.embedding IS NOT NULL ORDER BY f.embedding <=> ?::vector LIMIT ?";
        return jdbc.query(sqlConSimilitud, mapeador, vector, inmobiliariaId, rol, inmuebleId, inmuebleId, vector,
                TOP_POR_LISTA);
    }

    private List<EvidenciaFragmento> buscarTextual(Long inmobiliariaId, String rol, Long inmuebleId,
                                                    String pregunta) {
        String sql = SQL_BASE
                + " AND f.tsv @@ websearch_to_tsquery('spanish', ?) "
                + "ORDER BY ts_rank(f.tsv, websearch_to_tsquery('spanish', ?)) DESC LIMIT ?";
        RowMapper<EvidenciaFragmento> mapeador = (rs, i) -> new EvidenciaFragmento(rs.getLong("id"),
                rs.getLong("documento_id"), rs.getString("nombre_archivo"), rs.getInt("pagina_inicio"),
                rs.getInt("pagina_fin"), rs.getString("seccion"), rs.getString("texto"), 0);
        return jdbc.query(sql, mapeador, inmobiliariaId, rol, inmuebleId, inmuebleId, pregunta, pregunta,
                TOP_POR_LISTA);
    }

    private static String aTextoVector(float[] valores) {
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
