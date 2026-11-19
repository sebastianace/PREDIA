package co.predia.comun.web;

/** Una regla de negocio impide la operación (responde 422). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
