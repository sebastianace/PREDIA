package co.predia.asistente;

/**
 * Una herramienta no pudo identificar de quién o de qué habla el modelo (no existe o es ambiguo). El mensaje
 * se le devuelve al modelo para que pregunte al asesor, en lugar de adivinar.
 */
public class ReferenciaException extends RuntimeException {

    public ReferenciaException(String mensaje) {
        super(mensaje);
    }
}
