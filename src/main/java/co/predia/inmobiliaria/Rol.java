package co.predia.inmobiliaria;

/**
 * Roles de Predia. El ADMIN configura (tasas, catálogo, parámetros, usuarios); el ASESOR opera; el PROPIETARIO (el
 * vendedor de un inmueble) solo entra a su portal.
 */
public enum Rol {
    ADMIN,
    ASESOR,
    PROPIETARIO;

    /** Personal de la inmobiliaria: los únicos que usan la aplicación de trabajo. */
    public boolean esPersonal() {
        return this == ADMIN || this == ASESOR;
    }
}
