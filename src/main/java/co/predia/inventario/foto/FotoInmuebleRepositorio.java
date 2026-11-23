package co.predia.inventario.foto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acceso a la tabla {@code foto_inmueble} con SQL explícito (como la búsqueda vectorial): las imágenes no se mapean en
 * una entidad para que listar inmuebles nunca cargue sus bytes. Toda consulta filtra por {@code inmobiliaria_id}.
 */
@Repository
public class FotoInmuebleRepositorio {

    public record Bytes(byte[] contenido, String hash) {
    }

    private final JdbcTemplate jdbc;

    public FotoInmuebleRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Qué fotos tiene la inmobiliaria, sin traer los bytes: se usa para listar inmuebles. */
    public Map<Long, FotoInfo> infoDe(Long inmobiliariaId) {
        Map<Long, FotoInfo> info = new HashMap<>();
        jdbc.query("SELECT inmueble_id, hash, foco_x, foco_y, ancho, alto FROM foto_inmueble WHERE inmobiliaria_id = ?",
                rs -> {
                    info.put(rs.getLong("inmueble_id"), new FotoInfo(rs.getString("hash"), rs.getInt("foco_x"),
                            rs.getInt("foco_y"), rs.getInt("ancho"), rs.getInt("alto")));
                }, inmobiliariaId);
        return info;
    }

    public Optional<FotoInfo> info(Long inmobiliariaId, Long inmuebleId) {
        List<FotoInfo> filas = jdbc.query(
                "SELECT hash, foco_x, foco_y, ancho, alto FROM foto_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ?",
                (rs, n) -> new FotoInfo(rs.getString("hash"), rs.getInt("foco_x"), rs.getInt("foco_y"),
                        rs.getInt("ancho"), rs.getInt("alto")), inmobiliariaId, inmuebleId);
        return filas.stream().findFirst();
    }

    public Optional<Bytes> leer(Long inmobiliariaId, Long inmuebleId, boolean miniatura) {
        String columna = miniatura ? "miniatura" : "grande";
        List<Bytes> filas = jdbc.query(
                "SELECT " + columna + " AS contenido, hash FROM foto_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ?",
                (rs, n) -> new Bytes(rs.getBytes("contenido"), rs.getString("hash")), inmobiliariaId, inmuebleId);
        return filas.stream().findFirst();
    }

    public void guardar(Long inmobiliariaId, Long inmuebleId, Long usuarioId, ProcesadorImagen.Imagen imagen,
                        int focoX, int focoY) {
        jdbc.update("""
                INSERT INTO foto_inmueble (inmueble_id, inmobiliaria_id, grande, miniatura, ancho, alto, foco_x, foco_y,
                                           hash, subida_por, actualizada_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (inmueble_id) DO UPDATE SET grande = EXCLUDED.grande, miniatura = EXCLUDED.miniatura,
                    ancho = EXCLUDED.ancho, alto = EXCLUDED.alto, foco_x = EXCLUDED.foco_x, foco_y = EXCLUDED.foco_y,
                    hash = EXCLUDED.hash, subida_por = EXCLUDED.subida_por, actualizada_en = now()
                """, inmuebleId, inmobiliariaId, imagen.grande(), imagen.miniatura(), imagen.ancho(), imagen.alto(),
                focoX, focoY, imagen.hash(), usuarioId);
    }

    public boolean fijarFoco(Long inmobiliariaId, Long inmuebleId, int focoX, int focoY) {
        return jdbc.update("UPDATE foto_inmueble SET foco_x = ?, foco_y = ?, actualizada_en = now() "
                + "WHERE inmobiliaria_id = ? AND inmueble_id = ?", focoX, focoY, inmobiliariaId, inmuebleId) > 0;
    }

    public boolean borrar(Long inmobiliariaId, Long inmuebleId) {
        return jdbc.update("DELETE FROM foto_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ?",
                inmobiliariaId, inmuebleId) > 0;
    }
}
