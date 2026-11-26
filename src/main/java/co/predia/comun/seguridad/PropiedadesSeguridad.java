package co.predia.comun.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de la puerta de entrada: cuántos intentos fallidos se toleran antes de bloquear por un rato.
 * Los valores por defecto sirven tal cual; solo se cambian por variable de entorno si hace falta.
 */
@ConfigurationProperties("predia.seguridad")
public record PropiedadesSeguridad(int intentosPorCorreo, int intentosPorIp, int intentosEnlacePorIp, int ventanaMinutos) {

    public PropiedadesSeguridad {
        if (intentosPorCorreo <= 0) intentosPorCorreo = 5;
        if (intentosPorIp <= 0) intentosPorIp = 20;
        if (intentosEnlacePorIp <= 0) intentosEnlacePorIp = 10;
        if (ventanaMinutos <= 0) ventanaMinutos = 15;
    }
}
