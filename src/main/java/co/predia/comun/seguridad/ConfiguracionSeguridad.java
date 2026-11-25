package co.predia.comun.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad sin estado con JWT: cada petición trae su token y de él sale la inmobiliaria del usuario.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, RespuestasSeguridad respuestas, UsuarioRepository usuarios)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/activar").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/invitaciones/*").permitAll()
                        .requestMatchers("/api/auth/yo").authenticated()
                        // El portal es solo del propietario, y el propietario solo tiene el portal.
                        .requestMatchers("/api/portal/**").hasRole("PROPIETARIO")
                        .requestMatchers("/api/comprador/**").hasRole("COMPRADOR")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Escrituras de configuración: solo ADMIN (se revisa antes de validar el cuerpo).
                        .requestMatchers(HttpMethod.PUT, "/api/parametros").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/tasas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/tasas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/tasas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/requisitos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/documentos/regenerar-vectores").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/negocios/*/reabrir").hasRole("ADMIN")
                        .requestMatchers("/api/equipo/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "ASESOR")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidorRoles(usuarios)))
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .exceptionHandling(e -> e.authenticationEntryPoint(respuestas).accessDeniedHandler(respuestas))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(PropiedadesJwt propiedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(propiedades)));
    }

    @Bean
    JwtDecoder jwtDecoder(PropiedadesJwt propiedades) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(clave(propiedades))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(ServicioJwt.EMISOR));
        return decodificador;
    }

    /**
     * Convierte el claim "rol" del token en la autoridad ROLE_ADMIN, ROLE_ASESOR o ROLE_PROPIETARIO, y en cada petición
     * comprueba que el usuario siga activo y con el mismo rol: desactivar a alguien (o cambiarle el rol) corta su
     * sesión al instante, sin esperar a que el token venza.
     */
    private Converter<Jwt, AbstractAuthenticationToken> convertidorRoles(UsuarioRepository usuarios) {
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(ServicioJwt.CLAIM_ROL))));
        return jwt -> {
            Long id = Long.valueOf(jwt.getSubject());
            boolean vigente = usuarios.findById(id)
                    .filter(Usuario::isActivo)
                    .filter(u -> u.getRol().name().equals(jwt.getClaimAsString(ServicioJwt.CLAIM_ROL)))
                    .isPresent();
            if (!vigente) {
                throw new DisabledException("Tu sesión ya no es válida. Vuelve a iniciar sesión.");
            }
            return convertidor.convert(jwt);
        };
    }

    private static SecretKey clave(PropiedadesJwt propiedades) {
        return new SecretKeySpec(propiedades.secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
