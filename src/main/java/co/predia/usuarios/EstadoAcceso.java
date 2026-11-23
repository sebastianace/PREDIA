package co.predia.usuarios;

/** El estado del acceso de una persona: sin cuenta, invitada (sin contraseña todavía), activa o desactivada. */
public enum EstadoAcceso {
    SIN_INVITAR,
    INVITADO,
    ACTIVO,
    DESACTIVADO
}
