package co.predia.perfil;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.clientes.Cliente;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Las fotos de perfil de clientes, vendedores y asesores, con PostgreSQL real. */
class FotoPerfilApiTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;

    private static byte[] foto(int ancho, int alto, Color color) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, ancho, alto);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", salida);
        return salida.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder subida(String ruta, Usuario quien, byte[] bytes) {
        return (MockMultipartHttpServletRequestBuilder) multipart(PUT, ruta)
                .file(new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", bytes))
                .header("Authorization", bearer(quien));
    }

    @Test
    void laFotoDeUnClienteSeSubeSeVeCuadradaSeReemplazaYSeQuita() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Cliente");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Con foto", "5000000", "50000000");
        String ruta = "/api/clientes/" + cliente.getId() + "/foto";

        String primera = com.jayway.jsonpath.JsonPath.read(mvc.perform(subida(ruta, asesor, foto(1200, 800, Color.ORANGE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.fotoVersion");
        assertThat(primera).hasSize(64);

        MvcResult grande = mvc.perform(get(ruta).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                // La foto cambia detrás de la misma dirección: siempre se revalida.
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("Cache-Control", not(containsString("immutable")))).andReturn();
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(grande.getResponse().getContentAsByteArray()));
        assertThat(imagen.getWidth()).isEqualTo(imagen.getHeight()).isEqualTo(512);
        BufferedImage mini = ImageIO.read(new ByteArrayInputStream(mvc.perform(get(ruta).param("tam", "miniatura")
                .header("Authorization", bearer(asesor))).andReturn().getResponse().getContentAsByteArray()));
        assertThat(mini.getWidth()).isEqualTo(128);

        String etag = grande.getResponse().getHeader("ETag");
        mvc.perform(get(ruta).header("If-None-Match", etag).header("Authorization", bearer(asesor)))
                .andExpect(status().isNotModified());

        // La lista de clientes anuncia la versión: con ella la interfaz sabe cuándo volver a pedir la foto.
        mvc.perform(get("/api/clientes").header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$[0].fotoVersion").value(primera));

        String segunda = com.jayway.jsonpath.JsonPath.read(mvc.perform(subida(ruta, asesor, foto(300, 300, Color.GREEN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.fotoVersion");
        assertThat(segunda).isNotEqualTo(primera);
        mvc.perform(get(ruta).header("If-None-Match", etag).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk());

        mvc.perform(delete(ruta).header("Authorization", bearer(asesor))).andExpect(status().isOk());
        mvc.perform(get(ruta).header("Authorization", bearer(asesor))).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/" + cliente.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.fotoVersion").value(nullValue()));
    }

    @Test
    void laFotoDeUnVendedorSeSubeYAparece() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Vendedor");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Propietario vendedor = crearPropietario(inmobiliaria, asesor, "Vendedor", "v@ejemplo.test", TipoPersona.NATURAL,
                EstadoCivil.SOLTERO);

        String version = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(subida("/api/propietarios/" + vendedor.getId() + "/foto", asesor, foto(400, 600, Color.CYAN)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.fotoVersion");

        mvc.perform(get("/api/propietarios").header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$[0].fotoVersion").value(version));
        mvc.perform(get("/api/propietarios/" + vendedor.getId() + "/foto").param("tam", "miniatura")
                .header("Authorization", bearer(asesor))).andExpect(status().isOk());
    }

    @Test
    void unaPersonaDeOtraInmobiliariaNoSeAlcanzaNiConElIdCorrecto() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Fotos A");
        Inmobiliaria b = crearInmobiliaria("Fotos B");
        Usuario asesorA = crearUsuario(a, Rol.ASESOR);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);
        Cliente deB = crearCliente(b, asesorB, "Cliente de B", "5000000", "50000000");
        mvc.perform(subida("/api/clientes/" + deB.getId() + "/foto", asesorB, foto(200, 200, Color.RED)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/clientes/" + deB.getId() + "/foto").header("Authorization", bearer(asesorA)))
                .andExpect(status().isNotFound());
        mvc.perform(subida("/api/clientes/" + deB.getId() + "/foto", asesorA, foto(200, 200, Color.BLUE)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/clientes/" + deB.getId() + "/foto").header("Authorization", bearer(asesorA)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/perfil/usuarios/" + asesorB.getId() + "/foto").header("Authorization", bearer(asesorA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unAsesorCambiaSoloSuFotoYUnAdministradorLaDeCualquiera() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Asesores");
        Usuario laura = crearUsuario(inmobiliaria, Rol.ASESOR);
        Usuario andres = crearUsuario(inmobiliaria, Rol.ASESOR);
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);

        mvc.perform(subida("/api/perfil/usuarios/" + laura.getId() + "/foto", laura, foto(300, 300, Color.PINK)))
                .andExpect(status().isOk());
        // Otro asesor no puede cambiarla ni quitarla…
        mvc.perform(subida("/api/perfil/usuarios/" + laura.getId() + "/foto", andres, foto(300, 300, Color.BLACK)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/perfil/usuarios/" + laura.getId() + "/foto").header("Authorization", bearer(andres)))
                .andExpect(status().isForbidden());
        // …pero sí la ve, igual que el administrador, que además puede cambiarla.
        mvc.perform(get("/api/perfil/usuarios/" + laura.getId() + "/foto").header("Authorization", bearer(andres)))
                .andExpect(status().isOk());
        mvc.perform(subida("/api/perfil/usuarios/" + laura.getId() + "/foto", admin, foto(300, 300, Color.YELLOW)))
                .andExpect(status().isOk());

        // Y la sesión anuncia la versión de la foto de quien entró.
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(laura)))
                .andExpect(jsonPath("$.fotoVersion").exists());
        mvc.perform(get("/api/auth/yo").header("Authorization", bearer(andres)))
                .andExpect(jsonPath("$.fotoVersion").value(nullValue()));
    }

    @Test
    void loQueNoEsUnaImagenOEstaVacioSeRechazaSinDejarNada() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Invalidas");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Cliente", "5000000", "50000000");
        String ruta = "/api/clientes/" + cliente.getId() + "/foto";

        mvc.perform(subida(ruta, asesor, "esto no es una imagen".getBytes())).andExpect(status().isUnprocessableEntity());
        mvc.perform(subida(ruta, asesor, new byte[0])).andExpect(status().isUnprocessableEntity());

        mvc.perform(get(ruta).header("Authorization", bearer(asesor))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM foto_perfil WHERE inmobiliaria_id = ?", Integer.class,
                inmobiliaria.getId())).isZero();
    }

    @Test
    void alEliminarUnClienteSuFotoSeBorraConEl() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Supresion");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Cliente cliente = crearCliente(inmobiliaria, asesor, "A suprimir", "5000000", "50000000");
        mvc.perform(subida("/api/clientes/" + cliente.getId() + "/foto", asesor, foto(300, 300, Color.GRAY)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/clientes/" + cliente.getId()).header("Authorization", bearer(asesor)))
                .andExpect(status().isNoContent());

        // Derecho de supresión (Ley 1581): una foto es un dato personal y no puede quedar huérfana.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM foto_perfil WHERE sujeto = 'CLIENTE' AND sujeto_id = ?",
                Integer.class, cliente.getId())).isZero();
    }

    @Test
    void elNegocioTraeLasVersionesDeLasFotosDeSusPersonas() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Negocio");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Propietario vendedor = crearPropietario(inmobiliaria, asesor, "Vendedor", "vn@ejemplo.test", TipoPersona.NATURAL,
                EstadoCivil.SOLTERO);
        Inmueble inmueble = crearInmueble(inmobiliaria, "FN-1");
        Negocio negocio = crearNegocio(inmobiliaria, asesor, inmueble, vendedor, hoy().plusDays(20));
        mvc.perform(get("/api/negocios/" + negocio.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.propietarioFotoVersion").value(nullValue()))
                .andExpect(jsonPath("$.asesorFotoVersion").value(nullValue()));

        mvc.perform(subida("/api/propietarios/" + vendedor.getId() + "/foto", asesor, foto(300, 300, Color.MAGENTA)))
                .andExpect(status().isOk());
        mvc.perform(subida("/api/perfil/usuarios/" + asesor.getId() + "/foto", asesor, foto(300, 300, Color.ORANGE)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/negocios").header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$[0].propietarioFotoVersion").exists())
                .andExpect(jsonPath("$[0].asesorFotoVersion").exists())
                .andExpect(jsonPath("$[0].clienteCompradorFotoVersion").value(nullValue()));
    }
}
