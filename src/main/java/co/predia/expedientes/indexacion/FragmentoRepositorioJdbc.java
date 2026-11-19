package co.predia.expedientes.indexacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Lectura y escritura de fragmentos con SQL explícito (JdbcTemplate), para garantizar siempre el
 * filtro por inmobiliaria y poder escribir el vector de pgvector sin depender de un ORM. Es el
 * equivalente en Java del "Pgvector.EntityFrameworkCore" que menciona el documento de requerimientos.
 */
@Repository
public class FragmentoRepositorioJdbc {

    private final JdbcTemplate jdbc;

    public FragmentoRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Guarda los fragmentos de un documento. Si {@code embeddings} es null, se guardan sin vector
     * (modo manual): quedan disponibles para la búsqueda de texto y se pueden vectorizar después.
     */
    public void guardarTodos(Long inmobiliariaId, Long documentoId, Long inmuebleId,
                             List<FragmentoCandidato> candidatos, List<float[]> embeddings, String modeloEmbeddings) {
        String sql = """
                INSERT INTO fragmento
                    (inmobiliaria_id, documento_id, inmueble_id, orden, pagina_inicio, pagina_fin, seccion,
                     texto, tokens, es_ocr, embedding, embedding_modelo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?)
                """;
        jdbc.batchUpdate(sql, candidatos, candidatos.size(), (ps, candidato) -> {
            int i = candidatos.indexOf(candidato);
            String vectorTexto = embeddings == null ? null : aTextoVector(embeddings.get(i));
            ps.setLong(1, inmobiliariaId);
            ps.setLong(2, documentoId);
            ps.setLong(3, inmuebleId);
            ps.setInt(4, candidato.orden());
            ps.setInt(5, candidato.paginaInicio());
            ps.setInt(6, candidato.paginaFin());
            ps.setString(7, candidato.seccion());
            ps.setString(8, candidato.texto());
            ps.setInt(9, candidato.tokens());
            ps.setBoolean(10, candidato.esOcr());
            ps.setString(11, vectorTexto);
            ps.setString(12, embeddings == null ? null : modeloEmbeddings);
        });
    }

    public int contarPorDocumento(Long documentoId) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM fragmento WHERE documento_id = ?", Integer.class,
                documentoId);
        return total == null ? 0 : total;
    }

    public void borrarPorDocumento(Long documentoId) {
        jdbc.update("DELETE FROM fragmento WHERE documento_id = ?", documentoId);
    }

    /** Formato de texto que pgvector entiende al convertir con "::vector": "[0.12,-0.03,...]". */
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
