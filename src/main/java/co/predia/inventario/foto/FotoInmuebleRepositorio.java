package co.predia.inventario.foto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La portada de un inmueble: su primera FOTO por orden en la galería ({@code imagen_inmueble}). Solo lectura y con SQL
 * explícito para que listar inmuebles nunca cargue bytes. Toda consulta filtra por {@code inmobiliaria_id}.
 */
@Repository
public class FotoInmuebleRepositorio {

    public record Bytes(byte[] contenido, String hash) {
    }

    private final JdbcTemplate jdbc;

    public FotoInmuebleRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Qué portada tiene cada inmueble de la inmobiliaria, sin traer los bytes: se usa para listar inmuebles. */
    public Map<Long, FotoInfo> infoDe(Long inmobiliariaId) {
        Map<Long, FotoInfo> info = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (inmueble_id) inmueble_id, hash, foco_x, foco_y, ancho, alto
                FROM imagen_inmueble WHERE inmobiliaria_id = ? AND tipo = 'FOTO'
                ORDER BY inmueble_id, orden, id
                """, rs -> {
                    info.put(rs.getLong("inmueble_id"), new FotoInfo(rs.getString("hash"), rs.getInt("foco_x"),
                            rs.getInt("foco_y"), rs.getInt("ancho"), rs.getInt("alto")));
                }, inmobiliariaId);
        return info;
    }

    public Optional<FotoInfo> info(Long inmobiliariaId, Long inmuebleId) {
        List<FotoInfo> filas = jdbc.query("""
                SELECT hash, foco_x, foco_y, ancho, alto FROM imagen_inmueble
                WHERE inmobiliaria_id = ? AND inmueble_id = ? AND tipo = 'FOTO' ORDER BY orden, id LIMIT 1
                """, (rs, n) -> new FotoInfo(rs.getString("hash"), rs.getInt("foco_x"), rs.getInt("foco_y"),
                        rs.getInt("ancho"), rs.getInt("alto")), inmobiliariaId, inmuebleId);
        return filas.stream().findFirst();
    }

    public Optional<Bytes> leer(Long inmobiliariaId, Long inmuebleId, boolean miniatura) {
        String columna = miniatura ? "miniatura" : "grande";
        List<Bytes> filas = jdbc.query("SELECT " + columna + " AS contenido, hash FROM imagen_inmueble "
                        + "WHERE inmobiliaria_id = ? AND inmueble_id = ? AND tipo = 'FOTO' ORDER BY orden, id LIMIT 1",
                (rs, n) -> new Bytes(rs.getBytes("contenido"), rs.getString("hash")), inmobiliariaId, inmuebleId);
        return filas.stream().findFirst();
    }
}
