package co.predia.expedientes.consulta;

import java.util.List;

/**
 * {@code mejorSimilitudVectorial} y {@code hayCoincidenciaTextual} se usan para decidir si hay
 * evidencia suficiente antes de llamarle al modelo (ver MotorRag).
 */
public record ResultadoHibrido(List<EvidenciaFragmento> evidencias, double mejorSimilitudVectorial,
                               boolean hayCoincidenciaTextual) {
}
