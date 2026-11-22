package co.predia.comun.ia;

import java.util.List;

/**
 * Genera embeddings a partir de texto. Detrás de una interfaz para poder cambiar de proveedor
 * (por ejemplo, a uno local) sin tocar el resto del código.
 */
public interface ProveedorEmbeddings {

    /** Un vector por cada texto de la lista, en el mismo orden. */
    List<float[]> generar(List<String> textos);

    /** Nombre del modelo usado, para guardarlo junto con cada vector. */
    String modelo();
}
