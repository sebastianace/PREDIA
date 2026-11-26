package co.predia.perfil;

import co.predia.inventario.foto.ProcesadorImagen;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Las fotos de perfil, con SQL explícito para que listar personas nunca cargue bytes. Toda consulta filtra por
 * {@code inmobiliaria_id}: una foto de otra inmobiliaria no se alcanza ni con el id correcto.
 */
@Repository
public class FotoPerfilRepositorio {

    public record Bytes(byte[] contenido, String hash) {
    }

    private final JdbcTemplate jdbc;

    public FotoPerfilRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Crea la foto o reemplaza la que había. */
    public void guardar(Long inmobiliariaId, SujetoFoto sujeto, Long sujetoId, ProcesadorImagen.Imagen imagen, Long usuarioId) {
        jdbc.update("""
                INSERT INTO foto_perfil (inmobiliaria_id, sujeto, sujeto_id, grande, miniatura, hash, actualizado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (inmobiliaria_id, sujeto, sujeto_id)
                DO UPDATE SET grande = EXCLUDED.grande, miniatura = EXCLUDED.miniatura, hash = EXCLUDED.hash,
                              actualizado_por = EXCLUDED.actualizado_por, actualizado_en = now()
                """, inmobiliariaId, sujeto.name(), sujetoId, imagen.grande(), imagen.miniatura(), imagen.hash(), usuarioId);
    }

    /** La versión (hash) de la foto de cada persona de ese tipo, sin traer los bytes: sirve para listar. */
    public Map<Long, String> versiones(Long inmobiliariaId, SujetoFoto sujeto) {
        Map<Long, String> versiones = new HashMap<>();
        jdbc.query("SELECT sujeto_id, hash FROM foto_perfil WHERE inmobiliaria_id = ? AND sujeto = ?",
                rs -> {
                    versiones.put(rs.getLong("sujeto_id"), rs.getString("hash"));
                }, inmobiliariaId, sujeto.name());
        return versiones;
    }

    public Optional<String> version(Long inmobiliariaId, SujetoFoto sujeto, Long sujetoId) {
        List<String> filas = jdbc.query(
                "SELECT hash FROM foto_perfil WHERE inmobiliaria_id = ? AND sujeto = ? AND sujeto_id = ?",
                (rs, n) -> rs.getString("hash"), inmobiliariaId, sujeto.name(), sujetoId);
        return filas.stream().findFirst();
    }

    public Optional<Bytes> leer(Long inmobiliariaId, SujetoFoto sujeto, Long sujetoId, boolean miniatura) {
        String columna = miniatura ? "miniatura" : "grande";
        List<Bytes> filas = jdbc.query("SELECT " + columna + " AS contenido, hash FROM foto_perfil "
                        + "WHERE inmobiliaria_id = ? AND sujeto = ? AND sujeto_id = ?",
                (rs, n) -> new Bytes(rs.getBytes("contenido"), rs.getString("hash")),
                inmobiliariaId, sujeto.name(), sujetoId);
        return filas.stream().findFirst();
    }

    public boolean borrar(Long inmobiliariaId, SujetoFoto sujeto, Long sujetoId) {
        return jdbc.update("DELETE FROM foto_perfil WHERE inmobiliaria_id = ? AND sujeto = ? AND sujeto_id = ?",
                inmobiliariaId, sujeto.name(), sujetoId) > 0;
    }
}
