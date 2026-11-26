package co.predia.expedientes.carga;

import java.nio.file.Path;

/**
 * Revisa un archivo recién subido en busca de virus. Una implementación que no puede verificar el archivo lanza
 * {@link co.predia.comun.web.EscaneoNoDisponibleException}; nunca devuelve «limpio» por un fallo.
 */
public interface EscanerArchivos {

    /**
     * @param amenaza nombre de la amenaza detectada, o null si el archivo está limpio
     * @param verificado false cuando no se escaneó de verdad (antivirus desactivado o caído y no obligatorio)
     */
    record Resultado(String amenaza, boolean verificado) {

        public static Resultado limpio() {
            return new Resultado(null, true);
        }

        public static Resultado sinVerificar() {
            return new Resultado(null, false);
        }

        public static Resultado infectado(String amenaza) {
            return new Resultado(amenaza, true);
        }

        public boolean estaInfectado() {
            return amenaza != null;
        }
    }

    Resultado escanear(Path archivo);
}
