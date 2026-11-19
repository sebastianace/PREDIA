package co.predia.cierre.reglas;

import co.predia.cierre.catalogo.ReglaAplica;
import co.predia.cierre.catalogo.ReglaVigencia;
import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.EstadoRegistrado;
import co.predia.cierre.negocio.HipotecaVigente;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Servicio de reglas del cierre del negocio: decide, con fechas y sin IA, qué requisitos aplican, cuáles están
 * vigentes y si el negocio está listo para firmar. Es lógica pura (sin Spring ni base de datos) para poder
 * probarla con una fecha de "hoy" fija.
 *
 * <pre>
 *  1. Si no aplica (por las reglas del negocio) o el asesor lo marcó como no aplica → NO_APLICA.
 *  2. Si no está RECIBIDO → PENDIENTE o SUGERIDO (la IA solo sugiere; la vigencia no se evalúa).
 *  3. Si está RECIBIDO se calcula hasta cuándo vale:
 *       vence antes de hoy                              → VENCIDO
 *       vence antes de la firma o en menos de N días    → POR_VENCER
 *       si no                                           → RECIBIDO
 * </pre>
 */
public final class MotorReglasCierre {

    private MotorReglasCierre() {
    }

    /** Lo del catálogo que importa para decidir. */
    public record Requisito(ReglaAplica reglaAplica, boolean obligatorio, ReglaVigencia reglaVigencia,
                            Integer vigenciaDias) {
    }

    /** Lo que registró una persona (o sugirió la IA) sobre ese requisito en el negocio. */
    public record Registro(EstadoRegistrado estado, LocalDate fechaExpedicion) {
    }

    /** Los datos del negocio, del inmueble y del vendedor que usan las reglas de aplicación. */
    public record Contexto(boolean inmueblePropiedadHorizontal, boolean ciudadConValorizacion,
                           boolean vendedorPersonaJuridica, EstadoCivil estadoCivilVendedor,
                           boolean actuaPorApoderado, HipotecaVigente hipoteca, LocalDate fechaFirma,
                           int diasAlerta, LocalDate hoy) {
    }

    public record Evaluacion(EstadoRequisito estado, LocalDate venceEl, boolean obligatorio) {
    }

    public record Semaforo(boolean listoParaFirmar, int faltan, int obligatoriosQueAplican, int recibidos,
                           int porcentajeAvance) {
    }

    public static boolean aplica(ReglaAplica regla, Contexto c) {
        return switch (regla) {
            case SIEMPRE -> true;
            case SI_VALORIZACION -> c.ciudadConValorizacion();
            case SI_PROPIEDAD_HORIZONTAL -> c.inmueblePropiedadHorizontal();
            // Con la hipoteca "desconocida" no se exige: el asesor la confirma al revisar el certificado de tradición.
            case SI_HIPOTECA -> c.hipoteca() == HipotecaVigente.SI;
            case SI_CASADO_O_UNION -> !c.vendedorPersonaJuridica()
                    && (c.estadoCivilVendedor() == EstadoCivil.CASADO
                    || c.estadoCivilVendedor() == EstadoCivil.UNION_MARITAL);
            case SI_APODERADO -> c.actuaPorApoderado();
            case SI_PERSONA_JURIDICA -> c.vendedorPersonaJuridica();
        };
    }

    public static Evaluacion evaluar(Requisito requisito, Registro registro, Contexto c) {
        if (!aplica(requisito.reglaAplica(), c) || registro.estado() == EstadoRegistrado.NO_APLICA_MANUAL) {
            return new Evaluacion(EstadoRequisito.NO_APLICA, null, requisito.obligatorio());
        }
        if (registro.estado() == EstadoRegistrado.PENDIENTE) {
            return new Evaluacion(EstadoRequisito.PENDIENTE, null, requisito.obligatorio());
        }
        if (registro.estado() == EstadoRegistrado.SUGERIDO) {
            return new Evaluacion(EstadoRequisito.SUGERIDO, null, requisito.obligatorio());
        }

        LocalDate venceEl = venceEl(requisito, registro.fechaExpedicion());
        if (venceEl == null) {
            return new Evaluacion(EstadoRequisito.RECIBIDO, null, requisito.obligatorio());
        }
        if (venceEl.isBefore(c.hoy())) {
            return new Evaluacion(EstadoRequisito.VENCIDO, venceEl, requisito.obligatorio());
        }
        boolean venceAntesDeLaFirma = c.fechaFirma() != null && venceEl.isBefore(c.fechaFirma());
        boolean venceProntoSegunAlerta = ChronoUnit.DAYS.between(c.hoy(), venceEl) < c.diasAlerta();
        EstadoRequisito estado = venceAntesDeLaFirma || venceProntoSegunAlerta
                ? EstadoRequisito.POR_VENCER : EstadoRequisito.RECIBIDO;
        return new Evaluacion(estado, venceEl, requisito.obligatorio());
    }

    /** Último día en que el documento sigue siendo válido, o null si no tiene vigencia (o falta la fecha). */
    public static LocalDate venceEl(Requisito requisito, LocalDate fechaExpedicion) {
        if (requisito.reglaVigencia() == ReglaVigencia.NINGUNA || fechaExpedicion == null) {
            return null;
        }
        return switch (requisito.reglaVigencia()) {
            case DIAS_DESDE_EXPEDICION -> fechaExpedicion.plusDays(requisito.vigenciaDias());
            case ANIO_EN_CURSO -> LocalDate.of(fechaExpedicion.getYear(), 12, 31);
            case MES_EN_CURSO -> YearMonth.from(fechaExpedicion).atEndOfMonth();
            case NINGUNA -> null;
        };
    }

    /**
     * "Listo para firmar" cuando todos los requisitos obligatorios que aplican están RECIBIDO (vigentes).
     * Los no obligatorios (prácticas recomendadas) se muestran pero no cuentan.
     */
    public static Semaforo semaforo(List<Evaluacion> evaluaciones) {
        List<Evaluacion> obligatorios = evaluaciones.stream()
                .filter(e -> e.obligatorio() && e.estado() != EstadoRequisito.NO_APLICA).toList();
        int recibidos = (int) obligatorios.stream().filter(e -> e.estado() == EstadoRequisito.RECIBIDO).count();
        int faltan = obligatorios.size() - recibidos;
        int avance = obligatorios.isEmpty() ? 0
                : BigDecimal.valueOf(recibidos * 100L).divide(BigDecimal.valueOf(obligatorios.size()), 0,
                RoundingMode.HALF_UP).intValue();
        return new Semaforo(!obligatorios.isEmpty() && faltan == 0, faltan, obligatorios.size(), recibidos, avance);
    }
}
