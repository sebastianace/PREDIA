package co.predia.inventario.foto;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acceso a {@code plano_inmueble} con SQL explícito (no se mapea en una entidad para no cargar los bytes). */
@Repository
public class PlanoInmuebleRepositorio {

    public record Plano(byte[] contenido, String hash) {
    }

    private final JdbcTemplate jdbc;

    public PlanoInmuebleRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** La versión (hash) del plano de cada inmueble que lo tiene, sin traer los bytes. */
    public Map<Long, String> versionesDe(Long inmobiliariaId) {
        Map<Long, String> versiones = new HashMap<>();
        jdbc.query("SELECT inmueble_id, hash FROM plano_inmueble WHERE inmobiliaria_id = ?",
                rs -> { versiones.put(rs.getLong("inmueble_id"), rs.getString("hash")); }, inmobiliariaId);
        return versiones;
    }

    public Optional<String> version(Long inmobiliariaId, Long inmuebleId) {
        List<String> filas = jdbc.query("SELECT hash FROM plano_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ?",
                (rs, n) -> rs.getString("hash"), inmobiliariaId, inmuebleId);
        return filas.stream().findFirst();
    }

    public Optional<Plano> leer(Long inmobiliariaId, Long inmuebleId) {
        List<Plano> filas = jdbc.query(
                "SELECT imagen, hash FROM plano_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ?",
                (rs, n) -> new Plano(rs.getBytes("imagen"), rs.getString("hash")), inmobiliariaId, inmuebleId);
        return filas.stream().findFirst();
    }

    public void guardar(Long inmobiliariaId, Long inmuebleId, Long usuarioId, ProcesadorImagen.Plano plano) {
        jdbc.update("""
                INSERT INTO plano_inmueble (inmueble_id, inmobiliaria_id, imagen, ancho, alto, hash, subido_por, actualizada_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (inmueble_id) DO UPDATE SET imagen = EXCLUDED.imagen, ancho = EXCLUDED.ancho,
                    alto = EXCLUDED.alto, hash = EXCLUDED.hash, subido_por = EXCLUDED.subido_por, actualizada_en = now()
                """, inmuebleId, inmobiliariaId, plano.imagen(), plano.ancho(), plano.alto(), plano.hash(), usuarioId);
    }

    public boolean borrar(Long inmobiliariaId, Long inmuebleId) {
        return jdbc.update("DELETE FROM plano_inmueble WHERE inmobiliaria_id = ? AND inmueble_id = ?",
                inmobiliariaId, inmuebleId) > 0;
    }
}
