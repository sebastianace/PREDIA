package co.predia.comun.web;

/** Demasiados intentos fallidos seguidos: se bloquea por un rato (responde 429 con «Retry-After»). */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosEspera;

    public DemasiadosIntentosException(long segundosEspera) {
        super("Demasiados intentos fallidos. Espera " + minutos(segundosEspera) + " e inténtalo de nuevo.");
        this.segundosEspera = segundosEspera;
    }

    public long getSegundosEspera() {
        return segundosEspera;
    }

    private static String minutos(long segundos) {
        long m = (segundos + 59) / 60;
        return m <= 1 ? "un minuto" : m + " minutos";
    }
}
