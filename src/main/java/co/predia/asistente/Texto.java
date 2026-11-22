package co.predia.asistente;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Normaliza texto para comparar nombres sin importar tildes, mayúsculas ni abreviaturas comunes. */
public final class Texto {

    private static final Set<String> PALABRAS_VACIAS = Set.of("de", "del", "la", "el", "los", "las", "en", "y", "al",
            "un", "una", "para", "con");

    private Texto() {
    }

    /** "Apto 1102 Cañaveral" → "apartamento 1102 canaveral". */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String limpio = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
        return Arrays.stream(limpio.split(" "))
                .map(p -> p.equals("apto") || p.equals("apt") ? "apartamento" : p)
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    /** Las palabras con contenido de lo que escribió la persona o el modelo (sin "de", "y", "la"…). */
    public static List<String> palabrasClave(String texto) {
        String normal = normalizar(texto);
        if (normal.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normal.split(" ")).filter(p -> !PALABRAS_VACIAS.contains(p)).toList();
    }

    public static boolean esNumero(String texto) {
        return texto != null && texto.strip().matches("\\d{1,18}");
    }
}
