package co.predia.expedientes;

/** Estado de indexación de un documento, visible para el asesor en todo momento. */
public enum EstadoIndexacion {
    EN_COLA,
    PROCESANDO,
    INDEXADO,
    ERROR
}
