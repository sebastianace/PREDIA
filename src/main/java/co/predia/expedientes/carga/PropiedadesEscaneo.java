package co.predia.expedientes.carga;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Antivirus para los PDF que se suben. {@code modo} es DESACTIVADO (por defecto) o CLAMAV; con CLAMAV cada archivo se
 * envía al servicio {@code clamd} de {@code host:puerto}. Si {@code obligatorio} es true y el antivirus no responde,
 * el archivo no se acepta: nunca se da por limpio un archivo que no se pudo verificar.
 */
@ConfigurationProperties("predia.expedientes.escaneo")
public record PropiedadesEscaneo(String modo, String host, int puerto, boolean obligatorio, int tiempoMaximoSegundos) {

    public PropiedadesEscaneo {
        if (modo == null || modo.isBlank()) {
            modo = "DESACTIVADO";
        }
        if (host == null || host.isBlank()) {
            host = "localhost";
        }
        if (puerto <= 0) {
            puerto = 3310;
        }
        if (tiempoMaximoSegundos <= 0) {
            tiempoMaximoSegundos = 30;
        }
    }
}
