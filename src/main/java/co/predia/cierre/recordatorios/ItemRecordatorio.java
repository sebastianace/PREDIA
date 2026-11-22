package co.predia.cierre.recordatorios;

/**
 * Una línea del recordatorio: lo que entregó el motor de reglas. Se guarda como foto en el historial
 * ("qué se pidió"). {@code venceEl} va en texto ISO (AAAA-MM-DD) para no depender del serializador de fechas.
 */
public record ItemRecordatorio(String codigo, String nombre, String estado, String detalle, String venceEl) {
}
