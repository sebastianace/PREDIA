package co.predia.asistente;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Red de seguridad del principio «los números salen de Java, no del modelo»: revisa que cada cifra de la
 * respuesta del asistente aparezca en lo que devolvieron las herramientas (o en lo que escribió el asesor).
 * Lo que no esté respaldado se señala para que el asesor lo verifique; no se corrige a escondidas.
 *
 * <p>Se comparan solo los dígitos, sin separadores ni ceros de más: «$300.000.000» respalda «300 millones».
 * Las cifras de un solo dígito (numeración, «1 año») no se revisan.
 */
public final class GuardiaCifras {

    private static final Pattern NUMERO = Pattern.compile("\\d[\\d.,]*\\d|\\d");

    private GuardiaCifras() {
    }

    public static List<String> sinRespaldo(String respuesta, Collection<String> fuentes) {
        Set<String> respaldadas = new LinkedHashSet<>();
        for (String fuente : fuentes) {
            for (String numero : numerosDe(fuente)) {
                respaldadas.add(clave(numero));
            }
        }
        List<String> sinRespaldo = new ArrayList<>();
        for (String numero : numerosDe(respuesta)) {
            String digitos = digitos(numero);
            if (digitos.length() < 2 || respaldadas.contains(clave(numero))) {
                continue;
            }
            if (!sinRespaldo.contains(numero)) {
                sinRespaldo.add(numero);
            }
        }
        return sinRespaldo;
    }

    private static List<String> numerosDe(String texto) {
        List<String> numeros = new ArrayList<>();
        if (texto == null) {
            return numeros;
        }
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            numeros.add(m.group());
        }
        return numeros;
    }

    /** Solo los dígitos, sin ceros a la izquierda ("01" → "1"). */
    private static String digitos(String numero) {
        String d = numero.replaceAll("\\D", "").replaceFirst("^0+(?=\\d)", "");
        return d;
    }

    /** Los dígitos sin los ceros finales: «300» (de «300 millones») coincide con «300000000». */
    private static String clave(String numero) {
        String d = digitos(numero).replaceFirst("0+$", "");
        return d.isEmpty() ? "0" : d;
    }
}
