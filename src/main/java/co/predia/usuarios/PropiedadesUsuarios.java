package co.predia.usuarios;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración de usuarios e invitaciones (ver application.yml, sección predia.usuarios). */
@ConfigurationProperties("predia.usuarios")
public record PropiedadesUsuarios(String urlPublica, String remitente, PrimerAdmin primerAdmin) {

    public record PrimerAdmin(String email, String nombre, String inmobiliaria, String nit, String ciudad) {

        public boolean definido() {
            return email != null && !email.isBlank() && nombre != null && !nombre.isBlank()
                    && inmobiliaria != null && !inmobiliaria.isBlank();
        }
    }
}
