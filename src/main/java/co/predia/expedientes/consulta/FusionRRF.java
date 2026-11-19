package co.predia.expedientes.consulta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion: combina dos listas ordenadas (vectorial y textual) de ids de fragmento en
 * una sola, dando más peso a lo que aparece bien ubicado en varias listas. Lógica pura, sin Spring.
 */
public final class FusionRRF {

    static final int K = 60;

    private FusionRRF() {
    }

    public static List<Long> fusionar(List<Long> ordenVectorial, List<Long> ordenTextual) {
        Map<Long, Double> puntaje = new LinkedHashMap<>();
        acumular(puntaje, ordenVectorial);
        acumular(puntaje, ordenTextual);
        return puntaje.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static void acumular(Map<Long, Double> puntaje, List<Long> orden) {
        for (int i = 0; i < orden.size(); i++) {
            puntaje.merge(orden.get(i), 1.0 / (K + i + 1), Double::sum);
        }
    }
}
