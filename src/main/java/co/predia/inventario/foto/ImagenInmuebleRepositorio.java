package co.predia.inventario.foto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * La galería de un inmueble ({@code imagen_inmueble}) con SQL explícito: las imágenes no se mapean en una entidad para
 * que listar nunca cargue sus bytes. Toda consulta filtra por {@code inmobiliaria_id} y por {@code inmueble_id}, así que
 * una imagen de otro inmueble o de otra inmobiliaria nunca se alcanza con un id ajeno.
 */
@Repository
public class ImagenInmuebleRepositorio {

    public record Bytes(byte[] contenido, String hash) {
    }

    private static final String COLUMNAS = "id, tipo, orden, etiqueta, hash, foco_x, foco_y, ancho, alto";

    private static final RowMapper<ImagenInfo> A_INFO = (rs, n) -> new ImagenInfo(rs.getLong("id"),
            TipoImagen.valueOf(rs.getString("tipo")), rs.getInt("orden"), rs.getString("etiqueta"), rs.getString("hash"),
            rs.getInt("foco_x"), rs.getInt("foco_y"), rs.getInt("ancho"), rs.getInt("alto"));

    private final JdbcTemplate jdbc;

    public ImagenInmuebleRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ImagenInfo> listar(Long inmobiliariaId, Long inmuebleId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM imagen_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ? "
                + "ORDER BY orden, id", A_INFO, inmobiliariaId, inmuebleId);
    }

    public Optional<ImagenInfo> obtener(Long inmobiliariaId, Long inmuebleId, Long imagenId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM imagen_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ? "
                + "AND id = ?", A_INFO, inmobiliariaId, inmuebleId, imagenId).stream().findFirst();
    }

    public int contar(Long inmobiliariaId, Long inmuebleId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM imagen_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ?",
                Integer.class, inmobiliariaId, inmuebleId);
        return n == null ? 0 : n;
    }

    /** Agrega la imagen al final de la galería y devuelve su id. */
    public Long agregar(Long inmobiliariaId, Long inmuebleId, Long usuarioId, TipoImagen tipo, String etiqueta,
                        ProcesadorImagen.Imagen imagen, int focoX, int focoY) {
        return jdbc.queryForObject("""
                INSERT INTO imagen_inmueble (inmueble_id, inmobiliaria_id, tipo, orden, etiqueta, grande, miniatura,
                                             ancho, alto, foco_x, foco_y, hash, subida_por)
                VALUES (?, ?, ?, (SELECT COALESCE(MAX(orden) + 1, 0) FROM imagen_inmueble
                                  WHERE inmobiliaria_id = ? AND inmueble_id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, inmuebleId, inmobiliariaId, tipo.name(), inmobiliariaId, inmuebleId, etiqueta,
                imagen.grande(), imagen.miniatura(), imagen.ancho(), imagen.alto(), focoX, focoY, imagen.hash(), usuarioId);
    }

    public Optional<Bytes> leer(Long inmobiliariaId, Long inmuebleId, Long imagenId, boolean miniatura) {
        String columna = miniatura ? "miniatura" : "grande";
        return jdbc.query("SELECT " + columna + " AS contenido, hash FROM imagen_inmueble "
                        + "WHERE inmobiliaria_id = ? AND inmueble_id = ? AND id = ?",
                (rs, n) -> new Bytes(rs.getBytes("contenido"), rs.getString("hash")),
                inmobiliariaId, inmuebleId, imagenId).stream().findFirst();
    }

    public boolean actualizar(Long inmobiliariaId, Long inmuebleId, Long imagenId, TipoImagen tipo, String etiqueta,
                              int focoX, int focoY) {
        return jdbc.update("UPDATE imagen_inmueble SET tipo = ?, etiqueta = ?, foco_x = ?, foco_y = ? "
                        + "WHERE inmobiliaria_id = ? AND inmueble_id = ? AND id = ?", tipo.name(), etiqueta, focoX, focoY,
                inmobiliariaId, inmuebleId, imagenId) > 0;
    }

    /** Fija el orden: la primera imagen de la lista queda en 0, y así sucesivamente. */
    public void ordenar(Long inmobiliariaId, Long inmuebleId, List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE imagen_inmueble SET orden = ? WHERE inmobiliaria_id = ? AND inmueble_id = ? AND id = ?",
                    i, inmobiliariaId, inmuebleId, ids.get(i));
        }
    }

    public boolean borrar(Long inmobiliariaId, Long inmuebleId, Long imagenId) {
        boolean borrada = jdbc.update("DELETE FROM imagen_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ? AND id = ?",
                inmobiliariaId, inmuebleId, imagenId) > 0;
        if (borrada) {
            // Sin huecos en el orden: 0, 1, 2…
            jdbc.update("""
                    UPDATE imagen_inmueble i SET orden = r.n
                    FROM (SELECT id, row_number() OVER (ORDER BY orden, id) - 1 AS n FROM imagen_inmueble
                          WHERE inmobiliaria_id = ? AND inmueble_id = ?) r
                    WHERE i.id = r.id
                    """, inmobiliariaId, inmuebleId);
        }
        return borrada;
    }
}
