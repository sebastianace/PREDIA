package co.predia.comun.web;

/** No se pudo verificar el archivo con el antivirus (y es obligatorio): responde 503 y el archivo no se acepta. */
public class EscaneoNoDisponibleException extends RuntimeException {

    public EscaneoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
