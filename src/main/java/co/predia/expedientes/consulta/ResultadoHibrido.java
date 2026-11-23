package co.predia.expedientes.consulta;

import java.util.List;

/**
 * {@code mejorSimilitudVectorial} y {@code hayCoincidenciaTextual} se usan para decidir si hay
 * evidencia suficiente antes de llamarle al modelo (ver MotorRag). {@code fragmentosConOtroModelo} cuenta los
 * fragmentos al alcance del usuario cuyo vector se calculó con otro modelo de embeddings: no entran en la búsqueda
 * por significado (comparar vectores de modelos distintos no tiene sentido) y solo se encuentran por palabras.
 */
public record ResultadoHibrido(List<EvidenciaFragmento> evidencias, double mejorSimilitudVectorial,
                               boolean hayCoincidenciaTextual, int fragmentosConOtroModelo) {
}
