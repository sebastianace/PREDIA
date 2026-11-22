package co.predia.comun.web;

/** El proveedor de IA falló o no respondió a tiempo: responde 502, sin exponer el detalle del proveedor. */
public class FalloDeIaException extends RuntimeException {

    public FalloDeIaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
