package co.predia.cierre.reglas;

import co.predia.cierre.catalogo.ReglaVigencia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Las frases que describen el estado de un requisito, escritas en Java: las fechas y los plazos que ve el asesor
 * (y que reciben los propietarios en los recordatorios) nunca los redacta el modelo de lenguaje.
 */
public final class DescriptorItem {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));

    private DescriptorItem() {
    }

    public static String fecha(LocalDate fecha) {
        return FECHA.format(fecha);
    }

    public static String detalle(EstadoRequisito estado, LocalDate venceEl, ReglaVigencia regla, Integer vigenciaDias,
                                 LocalDate fechaFirma) {
        return switch (estado) {
            case PENDIENTE -> "Aún no lo hemos recibido.";
            case SUGERIDO -> "Se subió un documento que parece corresponder; falta que el asesor lo confirme.";
            case VENCIDO -> "Venció el " + fecha(venceEl) + ". " + exigencia(regla, vigenciaDias);
            case POR_VENCER -> {
                String antesDeLaFirma = fechaFirma != null && venceEl.isBefore(fechaFirma)
                        ? ", antes de la fecha de firma (" + fecha(fechaFirma) + ")" : "";
                yield "Vence el " + fecha(venceEl) + antesDeLaFirma + ". Conviene entregar uno más reciente.";
            }
            case RECIBIDO -> venceEl == null ? "Recibido." : "Recibido; vigente hasta el " + fecha(venceEl) + ".";
            case NO_APLICA -> "No aplica a este negocio.";
        };
    }

    private static String exigencia(ReglaVigencia regla, Integer vigenciaDias) {
        return switch (regla) {
            case DIAS_DESDE_EXPEDICION -> "Se necesita uno expedido hace máximo " + vigenciaDias + " días.";
            case ANIO_EN_CURSO -> "Se necesita uno del año en curso.";
            case MES_EN_CURSO -> "Se necesita uno del mes en curso.";
            case NINGUNA -> "";
        };
    }

    public static String textoSemaforo(MotorReglasCierre.Semaforo s) {
        if (s.listoParaFirmar()) {
            return "Listo para firmar";
        }
        if (s.obligatoriosQueAplican() == 0) {
            return "Sin requisitos activos";
        }
        return s.faltan() == 1 ? "Falta 1 documento" : "Faltan " + s.faltan() + " documentos";
    }
}
