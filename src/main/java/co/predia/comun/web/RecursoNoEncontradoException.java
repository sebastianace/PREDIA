package co.predia.comun.web;

/**
 * Recurso inexistente o perteneciente a otra inmobiliaria. Ambos casos responden 404 a propósito:
 * así no se revela que un id existe en otro tenant.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(recurso + " " + id + " no existe.");
    }
}
