package co.predia.comun.seguridad;

import co.predia.inmobiliaria.Rol;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Lee el usuario autenticado a partir del JWT validado por Spring Security. */
@Component
public class SesionActual {

    public UsuarioActual usuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado.");
        }
        return new UsuarioActual(
                Long.valueOf(jwt.getSubject()),
                ((Number) jwt.getClaim(ServicioJwt.CLAIM_INMOBILIARIA)).longValue(),
                jwt.getClaimAsString(ServicioJwt.CLAIM_NOMBRE),
                jwt.getClaimAsString(ServicioJwt.CLAIM_EMAIL),
                Rol.valueOf(jwt.getClaimAsString(ServicioJwt.CLAIM_ROL)));
    }

    /** Atajo para filtrar consultas: la inmobiliaria del usuario autenticado. */
    public Long inmobiliariaId() {
        return usuario().inmobiliariaId();
    }
}
