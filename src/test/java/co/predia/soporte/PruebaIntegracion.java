package co.predia.soporte;

import co.predia.comun.seguridad.ServicioJwt;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

/**
 * Base de las pruebas de integración: PostgreSQL 16 + pgvector real en un contenedor desechable
 * (compartido por todas las clases de prueba). Requiere Docker en ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaIntegracion {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    public static final String PASSWORD_PRUEBA = "ClaveDePrueba123";

    @Autowired protected MockMvc mvc;
    @Autowired protected InmobiliariaRepository inmobiliarias;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected ParametrosInmobiliariaRepository parametros;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ServicioJwt servicioJwt;

    /** Crea una inmobiliaria nueva (NIT único) con sus parámetros por defecto. */
    protected Inmobiliaria crearInmobiliaria(String nombre) {
        Inmobiliaria i = inmobiliarias.save(new Inmobiliaria(nombre, "T-" + UUID.randomUUID().toString().substring(0, 15), "Bucaramanga"));
        parametros.save(new ParametrosInmobiliaria(i.getId()));
        return i;
    }

    protected Usuario crearUsuario(Inmobiliaria inmobiliaria, Rol rol) {
        String email = rol.name().toLowerCase() + "-" + UUID.randomUUID() + "@prueba.test";
        return usuarios.save(new Usuario(inmobiliaria.getId(), "Usuario " + rol, email,
                passwordEncoder.encode(PASSWORD_PRUEBA), rol));
    }

    /** Cabecera Authorization con un JWT válido para el usuario. */
    protected String bearer(Usuario usuario) {
        return "Bearer " + servicioJwt.emitir(usuario).token();
    }
}
