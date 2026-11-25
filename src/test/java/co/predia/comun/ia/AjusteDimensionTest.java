package co.predia.comun.ia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El relleno con ceros que lleva un vector de 1024 dimensiones a las 1536 de la base. Sin Spring ni base de datos. */
class AjusteDimensionTest {

    @Test
    void unVectorDeLaDimensionExactaSeDejaIgual() {
        float[] vector = vector(AjusteDimension.DIMENSION_COLUMNA, 1);

        assertThat(AjusteDimension.ajustar(vector)).isSameAs(vector);
    }

    @Test
    void unVectorMasCortoSeRellenaConCerosAlFinal() {
        float[] vector = vector(1024, 7);

        float[] ajustado = AjusteDimension.ajustar(vector);

        assertThat(ajustado).hasSize(1536);
        assertThat(ajustado).startsWith(vector);
        for (int i = 1024; i < 1536; i++) {
            assertThat(ajustado[i]).isZero();
        }
    }

    @Test
    void elRellenoNoCambiaLaSimilitudCoseno() {
        float[] a = vector(1024, 3);
        float[] b = vector(1024, 11);

        double antes = coseno(a, b);
        double despues = coseno(AjusteDimension.ajustar(a), AjusteDimension.ajustar(b));

        assertThat(despues).isEqualTo(antes, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void unVectorMasLargoSeRechazaEnVezDeRecortarse() {
        assertThatThrownBy(() -> AjusteDimension.ajustar(new float[3072]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3072");
    }

    private static float[] vector(int dimension, long semilla) {
        java.util.Random azar = new java.util.Random(semilla);
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = azar.nextFloat() - 0.5f;
        }
        return vector;
    }

    private static double coseno(float[] a, float[] b) {
        double producto = 0, normaA = 0, normaB = 0;
        for (int i = 0; i < a.length; i++) {
            producto += (double) a[i] * b[i];
            normaA += (double) a[i] * a[i];
            normaB += (double) b[i] * b[i];
        }
        return producto / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }
}
