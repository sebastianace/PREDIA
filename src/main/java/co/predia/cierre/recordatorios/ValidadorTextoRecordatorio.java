package co.predia.cierre.recordatorios;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Revisa un mensaje a un propietario contra lo que de verdad falta: (1) que mencione TODOS los documentos de la
 * lista, (2) que NO mencione documentos del catálogo que no están en la lista, y (3) que no traiga números que no
 * salgan de la propia lista (fechas, plazos): los números los pone Java, no el modelo. Lógica pura, sin Spring.
 */
public final class ValidadorTextoRecordatorio {

    private static final Pattern NUMEROS = Pattern.compile("\\d+");

    private ValidadorTextoRecordatorio() {
    }

    /** Un documento de la lista: su nombre y sus palabras clave. Con cualquiera de las dos cuenta como mencionado. */
    public record Documento(String nombre, List<String> palabrasClave) {
    }

    /** Un documento del catálogo que NO se está pidiendo: si el texto lo nombra, es un error. */
    public record NoPedido(String nombre, List<String> palabrasClave) {
    }

    public record Resultado(List<String> problemas) {
        public boolean esValido() {
            return problemas.isEmpty();
        }
    }

    public static Resultado validar(String texto, List<Documento> pedidos, List<NoPedido> noPedidos,
                                    Set<String> numerosPermitidos) {
        String limpio = normalizar(texto);
        List<String> problemas = new ArrayList<>();

        for (Documento d : pedidos) {
            boolean mencionado = limpio.contains(normalizar(d.nombre()))
                    || d.palabrasClave().stream().anyMatch(k -> limpio.contains(normalizar(k)));
            if (!mencionado) {
                problemas.add("No menciona un documento que sí falta: " + d.nombre() + ".");
            }
        }
        for (NoPedido n : noPedidos) {
            n.palabrasClave().stream().filter(k -> limpio.contains(normalizar(k))).findFirst()
                    .ifPresent(k -> problemas.add("Menciona «" + k + "» (" + n.nombre()
                            + "), pero ese documento no está pendiente."));
        }
        Set<String> sobrantes = new HashSet<>();
        Matcher m = NUMEROS.matcher(texto);
        while (m.find()) {
            if (!numerosPermitidos.contains(m.group())) {
                sobrantes.add(m.group());
            }
        }
        if (!sobrantes.isEmpty()) {
            problemas.add("Trae números que no salen de la lista de pendientes: " + String.join(", ",
                    sobrantes.stream().sorted().toList()) + ".");
        }
        return new Resultado(problemas);
    }

    /** Todos los números (grupos de dígitos) que aparecen en las frases dadas. */
    public static Set<String> numerosDe(List<String> frases) {
        Set<String> numeros = new HashSet<>();
        for (String frase : frases) {
            Matcher m = NUMEROS.matcher(frase);
            while (m.find()) {
                numeros.add(m.group());
            }
        }
        return numeros;
    }

    static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }
}
