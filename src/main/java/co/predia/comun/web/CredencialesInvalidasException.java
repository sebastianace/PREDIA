package co.predia.comun.web;

/** Email o contraseña incorrectos (mensaje genérico para no revelar cuál de los dos falló). */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Email o contraseña incorrectos.");
    }
}
