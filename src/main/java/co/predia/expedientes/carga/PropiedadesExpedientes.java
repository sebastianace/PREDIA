package co.predia.expedientes.carga;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dónde se guardan los PDF mientras se indexan, y el tamaño máximo que se acepta. */
@ConfigurationProperties("predia.expedientes")
public record PropiedadesExpedientes(String directorioTemporal, int tamanoMaximoMb) {

    public PropiedadesExpedientes {
        if (directorioTemporal == null || directorioTemporal.isBlank()) {
            directorioTemporal = System.getProperty("java.io.tmpdir") + "/predia/documentos";
        }
        if (tamanoMaximoMb <= 0) {
            tamanoMaximoMb = 20;
        }
    }

    public long tamanoMaximoBytes() {
        return tamanoMaximoMb * 1024L * 1024L;
    }
}
