package co.predia.comun.ia;

/**
 * Lleva un vector a la dimensión fija de las columnas de PostgreSQL (1536). Los modelos locales dan menos (bge-m3
 * da 1024): se rellena con ceros, y eso no cambia el producto punto ni las normas, así que la similitud coseno
 * queda exactamente igual. Lo contrario, recortar, sí la alteraría: un vector más largo se rechaza.
 */
public final class AjusteDimension {

    public static final int DIMENSION_COLUMNA = 1536;

    private AjusteDimension() {
    }

    public static float[] ajustar(float[] vector) {
        if (vector.length == DIMENSION_COLUMNA) {
            return vector;
        }
        if (vector.length > DIMENSION_COLUMNA) {
            throw new IllegalStateException("El modelo de embeddings devolvió " + vector.length
                    + " dimensiones y la base solo admite " + DIMENSION_COLUMNA
                    + ". Elige un modelo de " + DIMENSION_COLUMNA + " dimensiones o menos.");
        }
        return java.util.Arrays.copyOf(vector, DIMENSION_COLUMNA);
    }
}
