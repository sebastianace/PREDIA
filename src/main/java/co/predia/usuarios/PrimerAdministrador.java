package co.predia.usuarios;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * En una instalación nueva (sin ningún ADMIN), crea la inmobiliaria y su primer administrador a partir de las variables
 * PREDIA_ADMIN_* y PREDIA_INMOBILIARIA_*. El administrador queda sin contraseña y el enlace para crearla se escribe una
 * sola vez en el log: así no hay ninguna contraseña en variables de entorno. Si ya hay un ADMIN, no hace nada.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PrimerAdministrador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrimerAdministrador.class);

    private final UsuarioRepository usuarios;
    private final InmobiliariaRepository inmobiliarias;
    private final ParametrosInmobiliariaRepository parametros;
    private final ServicioInvitaciones invitaciones;
    private final PropiedadesUsuarios propiedades;

    public PrimerAdministrador(UsuarioRepository usuarios, InmobiliariaRepository inmobiliarias,
                               ParametrosInmobiliariaRepository parametros, ServicioInvitaciones invitaciones,
                               PropiedadesUsuarios propiedades) {
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.parametros = parametros;
        this.invitaciones = invitaciones;
        this.propiedades = propiedades;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        crearSiHaceFalta();
    }

    /** Devuelve el enlace de activación si creó al administrador, o null si no hizo nada. */
    @Transactional
    public String crearSiHaceFalta() {
        PropiedadesUsuarios.PrimerAdmin datos = propiedades.primerAdmin();
        if (usuarios.countByRol(Rol.ADMIN) > 0) {
            return null;
        }
        if (datos == null || !datos.definido()) {
            log.warn("No hay ningún administrador. Define PREDIA_ADMIN_EMAIL, PREDIA_ADMIN_NOMBRE y "
                    + "PREDIA_INMOBILIARIA_NOMBRE (o activa la demo) para poder entrar a Predia.");
            return null;
        }
        String email = ServicioUsuarios.normalizar(datos.email());
        if (usuarios.existsByEmailIgnoreCase(email)) {
            log.warn("No se creó el primer administrador: el correo ya pertenece a otro usuario.");
            return null;
        }
        String nit = datos.nit() == null || datos.nit().isBlank()
                ? "SIN-NIT-" + UUID.randomUUID().toString().substring(0, 8) : datos.nit().strip();
        Inmobiliaria inmobiliaria = inmobiliarias.save(new Inmobiliaria(datos.inmobiliaria().strip(), nit,
                datos.ciudad() == null || datos.ciudad().isBlank() ? "Bucaramanga" : datos.ciudad().strip()));
        parametros.save(new ParametrosInmobiliaria(inmobiliaria.getId()));
        Usuario admin = usuarios.save(Usuario.invitado(inmobiliaria.getId(), datos.nombre().strip(), email, Rol.ADMIN,
                null, null));
        ServicioInvitaciones.Emitida emitida = invitaciones.emitir(admin, null);
        log.warn("Se creó el primer administrador de {}. Para crear su contraseña, abre este enlace antes de 72 horas "
                + "(no se volverá a mostrar): {}", inmobiliaria.getNombre(), emitida.enlace());
        return emitida.enlace();
    }
}
