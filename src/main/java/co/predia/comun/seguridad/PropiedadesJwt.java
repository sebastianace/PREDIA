package co.predia.comun.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración del JWT. El secreto llega solo por variable de entorno (JWT_SECRET). */
@ConfigurationProperties("predia.jwt")
public record PropiedadesJwt(String secreto, int horasValidez) {

    public PropiedadesJwt {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException(
                    "Falta JWT_SECRET o tiene menos de 32 caracteres. Configúralo en el archivo .env (ver .env.example).");
        }
        if (horasValidez <= 0) {
            horasValidez = 8;
        }
    }
}
