package co.predia.comun.web;

/** La función necesita IA y esta no está disponible (sin API key): responde 503; el resto de la app sigue. */
public class IaNoDisponibleException extends RuntimeException {

    public IaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
