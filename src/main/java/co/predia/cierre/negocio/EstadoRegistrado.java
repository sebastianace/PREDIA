package co.predia.cierre.negocio;

/**
 * Lo único que se guarda de cada requisito: lo que hizo una persona (RECIBIDO, NO_APLICA_MANUAL) o lo que sugirió
 * la IA (SUGERIDO). El estado que se muestra lo calcula siempre el motor de reglas.
 */
public enum EstadoRegistrado { PENDIENTE, SUGERIDO, RECIBIDO, NO_APLICA_MANUAL }
