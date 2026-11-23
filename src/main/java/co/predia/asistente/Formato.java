package co.predia.asistente;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Cómo se escriben las cifras que ve el asesor: pesos colombianos ("$300.000.000") y porcentajes ("29,6 %").
 * Las herramientas del asistente las entregan ya escritas así, para que el modelo las copie y no las reformule.
 */
public final class Formato {

    private static final DecimalFormatSymbols SIMBOLOS = simbolos();

    private Formato() {
    }

    private static DecimalFormatSymbols simbolos() {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return s;
    }

    /** "$300.000.000"; sin decimales, porque los pesos de Predia siempre se redondean en Java. */
    public static String pesos(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        return "$" + new DecimalFormat("#,##0", SIMBOLOS).format(valor.setScale(0, RoundingMode.HALF_UP));
    }

    /** Una fracción (0,296) se escribe como "29,6 %"; hasta dos decimales y sin ceros sobrantes. */
    public static String porcentaje(BigDecimal fraccion) {
        if (fraccion == null) {
            return null;
        }
        return new DecimalFormat("#,##0.##", SIMBOLOS).format(fraccion.multiply(BigDecimal.valueOf(100))) + " %";
    }

    public static String anios(int anios) {
        return anios == 1 ? "1 año" : anios + " años";
    }

    /** 125 → «10 años 5 meses»: los meses los calcula Java y aquí solo se escriben. */
    public static String plazo(int meses) {
        if (meses <= 0) {
            return "0 meses";
        }
        int anios = meses / 12;
        int resto = meses % 12;
        String m = resto == 1 ? "1 mes" : resto + " meses";
        if (anios == 0) {
            return m;
        }
        return resto == 0 ? anios(anios) : anios(anios) + " " + m;
    }
}
