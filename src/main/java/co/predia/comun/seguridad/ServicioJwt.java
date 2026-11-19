package co.predia.comun.seguridad;

import co.predia.inmobiliaria.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Emite los JWT de Predia (HS256) con la inmobiliaria y el rol del usuario. */
@Service
public class ServicioJwt {

    static final String EMISOR = "predia";
    static final String CLAIM_INMOBILIARIA = "inmobiliaria_id";
    static final String CLAIM_ROL = "rol";
    static final String CLAIM_NOMBRE = "nombre";
    static final String CLAIM_EMAIL = "email";

    private final JwtEncoder codificador;
    private final PropiedadesJwt propiedades;

    public ServicioJwt(JwtEncoder codificador, PropiedadesJwt propiedades) {
        this.codificador = codificador;
        this.propiedades = propiedades;
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(Duration.ofHours(propiedades.horasValidez()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim(CLAIM_INMOBILIARIA, usuario.getInmobiliariaId())
                .claim(CLAIM_ROL, usuario.getRol().name())
                .claim(CLAIM_NOMBRE, usuario.getNombre())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(token, expira);
    }

    public record TokenEmitido(String token, Instant expiraEn) {
    }
}
