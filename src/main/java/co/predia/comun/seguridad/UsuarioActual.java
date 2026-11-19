package co.predia.comun.seguridad;

import co.predia.inmobiliaria.Rol;

/**
 * Usuario autenticado en la petición en curso. De aquí sale SIEMPRE la inmobiliaria (tenant)
 * con la que se filtran las consultas; nunca de un parámetro que envíe el cliente o el modelo.
 */
public record UsuarioActual(Long id, Long inmobiliariaId, String nombre, String email, Rol rol) {

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}
