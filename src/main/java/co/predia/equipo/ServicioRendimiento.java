package co.predia.equipo;

import co.predia.perfil.FotoPerfilRepositorio;
import co.predia.perfil.SujetoFoto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cuánto vendió cada asesor. Todo sale de los negocios cerrados (estado FIRMADO con su registro de cierre), nunca de un
 * número escrito a mano; los cálculos son SQL con el tenant siempre en el filtro. Una venta cuenta para el asesor que abrió
 * el negocio (el negocio guarda su asesor y no se cambia).
 */
@Service
public class ServicioRendimiento {

    public record Rendimiento(Long usuarioId, String nombre, String rol, String fotoVersion, int vendidos,
                              BigDecimal valorVendido, int abiertos, int cancelados, Integer diasPromedioCierre,
                              Integer tasaDeCierre) {
    }

    public record CierreReciente(Long negocioId, String inmueble, BigDecimal valor, Instant firmadoEn) {
    }

    private final JdbcTemplate jdbc;
    private final FotoPerfilRepositorio fotos;
    private final Clock reloj;

    public ServicioRendimiento(JdbcTemplate jdbc, FotoPerfilRepositorio fotos, Clock reloj) {
        this.jdbc = jdbc;
        this.fotos = fotos;
        this.reloj = reloj;
    }

    /** Los asesores de la inmobiliaria (y los administradores que abrieron negocios) con sus cifras del periodo. */
    public List<Rendimiento> equipo(Long inmobiliariaId, Periodo periodo) {
        Timestamp desde = Timestamp.from(periodo.desde(reloj));
        Timestamp hasta = Timestamp.from(periodo.hasta(reloj));
        Map<Long, String> foto = fotos.versiones(inmobiliariaId, SujetoFoto.USUARIO);
        return jdbc.query("""
                SELECT u.id, u.nombre, u.rol,
                       COUNT(n.id) FILTER (WHERE n.estado = 'FIRMADO' AND n.firmado_en >= ? AND n.firmado_en < ?) AS vendidos,
                       COALESCE(SUM(n.valor_cierre) FILTER (WHERE n.estado = 'FIRMADO' AND n.firmado_en >= ? AND n.firmado_en < ?), 0) AS valor,
                       COUNT(n.id) FILTER (WHERE n.estado = 'ABIERTO') AS abiertos,
                       COUNT(n.id) FILTER (WHERE n.estado = 'CANCELADO' AND n.cancelado_en >= ? AND n.cancelado_en < ?) AS cancelados,
                       AVG(EXTRACT(EPOCH FROM (n.firmado_en - n.creado_en)) / 86400.0)
                           FILTER (WHERE n.estado = 'FIRMADO' AND n.firmado_en >= ? AND n.firmado_en < ?) AS dias
                FROM usuario u
                LEFT JOIN negocio n ON n.asesor_id = u.id AND n.inmobiliaria_id = u.inmobiliaria_id
                WHERE u.inmobiliaria_id = ? AND u.activo AND u.rol IN ('ASESOR', 'ADMIN')
                  AND (u.rol = 'ASESOR' OR EXISTS (SELECT 1 FROM negocio x WHERE x.asesor_id = u.id))
                GROUP BY u.id, u.nombre, u.rol
                ORDER BY vendidos DESC, valor DESC, u.nombre
                """, (rs, i) -> {
                    int vendidos = rs.getInt("vendidos");
                    int cancelados = rs.getInt("cancelados");
                    BigDecimal dias = rs.getBigDecimal("dias");
                    Long id = rs.getLong("id");
                    return new Rendimiento(id, rs.getString("nombre"), rs.getString("rol"), foto.get(id), vendidos,
                            rs.getBigDecimal("valor"), rs.getInt("abiertos"), cancelados,
                            dias == null ? null : (int) Math.round(dias.doubleValue()),
                            tasa(vendidos, cancelados));
                }, desde, hasta, desde, hasta, desde, hasta, desde, hasta, inmobiliariaId);
    }

    /** Las cifras de una sola persona (puede no aparecer si es un administrador sin negocios). */
    public Rendimiento de(Long inmobiliariaId, Long usuarioId, Periodo periodo, String nombre, String rol) {
        return equipo(inmobiliariaId, periodo).stream().filter(r -> r.usuarioId().equals(usuarioId)).findFirst()
                .orElse(new Rendimiento(usuarioId, nombre, rol, fotos.version(inmobiliariaId, SujetoFoto.USUARIO, usuarioId)
                        .orElse(null), 0, BigDecimal.ZERO, 0, 0, null, null));
    }

    public List<CierreReciente> ultimosCierres(Long inmobiliariaId, Long usuarioId, Periodo periodo, int limite) {
        return jdbc.query("""
                SELECT n.id, i.titulo, n.valor_cierre, n.firmado_en
                FROM negocio n JOIN inmueble i ON i.id = n.inmueble_id
                WHERE n.inmobiliaria_id = ? AND n.asesor_id = ? AND n.estado = 'FIRMADO'
                  AND n.firmado_en >= ? AND n.firmado_en < ?
                ORDER BY n.firmado_en DESC LIMIT ?
                """, (rs, i) -> new CierreReciente(rs.getLong("id"), rs.getString("titulo"), rs.getBigDecimal("valor_cierre"),
                        rs.getTimestamp("firmado_en").toInstant()),
                inmobiliariaId, usuarioId, Timestamp.from(periodo.desde(reloj)), Timestamp.from(periodo.hasta(reloj)), limite);
    }

    /** Qué porcentaje de los negocios que se resolvieron (vendidos o cancelados) terminó en venta; null si no hay ninguno. */
    static Integer tasa(int vendidos, int cancelados) {
        int resueltos = vendidos + cancelados;
        return resueltos == 0 ? null : (int) Math.round(100.0 * vendidos / resueltos);
    }
}
