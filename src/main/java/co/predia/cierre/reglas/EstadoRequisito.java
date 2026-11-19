package co.predia.cierre.reglas;

/** El estado que ve el asesor de cada requisito. Lo calcula MotorReglasCierre; nunca lo decide el modelo de lenguaje. */
public enum EstadoRequisito { PENDIENTE, SUGERIDO, RECIBIDO, POR_VENCER, VENCIDO, NO_APLICA }
