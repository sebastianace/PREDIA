package co.predia.inventario.foto;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La galería de un inmueble: fotos y planos en un solo orden, con la primera foto como portada. */
class GaleriaInmuebleApiTest extends PruebaIntegracion {

    private static byte[] imagen(int ancho, int alto, String formato, int tono) throws IOException {
        BufferedImage i = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(20, 70, 160));
        g.fillRect(0, 0, ancho, alto);
        g.setColor(new Color(tono, 255 - tono, 255));
        g.fillRect(ancho / 3, alto / 3, ancho / 3, alto / 3);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(i, formato, salida);
        return salida.toByteArray();
    }

    private static String url(Inmueble i) {
        return "/api/inmuebles/" + i.getId() + "/imagenes";
    }

    private MockMultipartHttpServletRequestBuilder subida(Inmueble i, Usuario u, byte[] bytes, String nombre) {
        return (MockMultipartHttpServletRequestBuilder) multipart(HttpMethod.POST, url(i))
                .file(new MockMultipartFile("archivo", nombre, "image/jpeg", bytes)).header("Authorization", bearer(u));
    }

    private long agregar(Inmueble i, Usuario u, String tipo, int tono, String etiqueta) throws Exception {
        MvcResult r = mvc.perform(subida(i, u, imagen(1200, 800, "jpg", tono), "x.jpg").param("tipo", tipo)
                .param("etiqueta", etiqueta)).andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void lasImagenesQuedanEnElOrdenEnQueSeSubenYLaPrimeraFotoEsLaPortada() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Orden");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-1");

        // Un plano primero: la portada sigue siendo la primera FOTO, no la primera imagen.
        long plano = agregar(apto, asesor, "PLANO", 10, "Plano del piso");
        long sala = agregar(apto, asesor, "FOTO", 80, "Sala");
        long cocina = agregar(apto, asesor, "FOTO", 160, "Cocina");

        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(plano)).andExpect(jsonPath("$[0].tipo").value("PLANO"))
                .andExpect(jsonPath("$[0].orden").value(0)).andExpect(jsonPath("$[0].etiqueta").value("Plano del piso"))
                .andExpect(jsonPath("$[1].id").value(sala)).andExpect(jsonPath("$[2].id").value(cocina));

        String versionSala = JsonPath.read(mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)))
                .andReturn().getResponse().getContentAsString(), "$[1].version");
        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto.version").value(versionSala));
        // La portada se descarga por /foto y es la de la sala.
        mvc.perform(get("/api/inmuebles/" + apto.getId() + "/foto").header("Authorization", bearer(asesor)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void unaFotoSeReduceA1600YUnPlanoA2000ConSuMiniaturaYNingunoSeRecortaAlSubir() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Tamaños");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-2");

        mvc.perform(subida(apto, asesor, imagen(4000, 3000, "jpg", 50), "f.jpg").param("focoX", "30").param("focoY", "70"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("FOTO"))
                .andExpect(jsonPath("$.ancho").value(1600)).andExpect(jsonPath("$.alto").value(1200))
                .andExpect(jsonPath("$.focoX").value(30)).andExpect(jsonPath("$.focoY").value(70));
        long plano = ((Number) JsonPath.read(mvc.perform(subida(apto, asesor, imagen(3000, 2000, "png", 200), "p.png")
                .param("tipo", "PLANO")).andExpect(status().isCreated()).andExpect(jsonPath("$.ancho").value(2000))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        byte[] grande = mvc.perform(get(url(apto) + "/" + plano).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] mini = mvc.perform(get(url(apto) + "/" + plano).param("tam", "miniatura")
                .header("Authorization", bearer(asesor))).andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(grande)).getWidth()).isEqualTo(2000);
        assertThat(ImageIO.read(new ByteArrayInputStream(mini)).getWidth()).isEqualTo(480);
        assertThat(mini.length).isLessThan(grande.length);
        assertThat(grande[0] & 0xFF).isEqualTo(0xFF);
        assertThat(grande[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void sinFotosElInmuebleNoTienePortadaYUnSoloPlanoNoLaCrea() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Sin Portada");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-3");

        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto").value(nullValue()));
        agregar(apto, asesor, "PLANO", 10, "Plano");
        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto").value(nullValue()));
        mvc.perform(get("/api/inmuebles/" + apto.getId() + "/foto").header("Authorization", bearer(asesor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sePuedeReordenarCambiarElTipoYLaEtiquetaYElEncuadre() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Edición");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-4");
        long a = agregar(apto, asesor, "FOTO", 10, "A");
        long b = agregar(apto, asesor, "FOTO", 90, "B");
        long c = agregar(apto, asesor, "FOTO", 170, "C");

        mvc.perform(put(url(apto) + "/orden").header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [" + c + "," + a + "," + b + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(c)).andExpect(jsonPath("$[1].id").value(a))
                .andExpect(jsonPath("$[2].orden").value(2));
        String versionC = JsonPath.read(mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)))
                .andReturn().getResponse().getContentAsString(), "$[0].version");
        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto.version").value(versionC));

        mvc.perform(patch(url(apto) + "/" + a).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"PLANO\", \"etiqueta\": \"Plano torre\", \"focoX\": 10, \"focoY\": 20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("PLANO"))
                .andExpect(jsonPath("$.etiqueta").value("Plano torre")).andExpect(jsonPath("$.focoX").value(10));
        // Lo que no se manda se queda como estaba.
        mvc.perform(patch(url(apto) + "/" + a).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"focoY\": 90}"))
                .andExpect(jsonPath("$.tipo").value("PLANO")).andExpect(jsonPath("$.focoX").value(10))
                .andExpect(jsonPath("$.focoY").value(90));
        mvc.perform(patch(url(apto) + "/" + a).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"focoX\": 150}")).andExpect(status().isBadRequest());
    }

    @Test
    void elOrdenDebeNombrarCadaImagenUnaSolaVez() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Orden Mal");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-5");
        long a = agregar(apto, asesor, "FOTO", 10, "A");
        long b = agregar(apto, asesor, "FOTO", 90, "B");

        for (String cuerpo : List.of("{\"ids\": [" + a + "]}", "{\"ids\": [" + a + "," + a + "]}",
                "{\"ids\": [" + a + "," + b + ",999999]}", "{\"ids\": [" + a + ",999999]}")) {
            mvc.perform(put(url(apto) + "/orden").header("Authorization", bearer(asesor))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor))).andExpect(jsonPath("$[0].id").value(a));
    }

    @Test
    void quitarUnaImagenNoDejaHuecosYSiEraLaPortadaLaSiguienteFotoLaReemplaza() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Quitar");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-6");
        long a = agregar(apto, asesor, "FOTO", 10, "A");
        long b = agregar(apto, asesor, "FOTO", 90, "B");
        long c = agregar(apto, asesor, "PLANO", 170, "C");

        mvc.perform(delete(url(apto) + "/" + a).header("Authorization", bearer(asesor))).andExpect(status().isNoContent());
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].id").value(b))
                .andExpect(jsonPath("$[0].orden").value(0)).andExpect(jsonPath("$[1].id").value(c))
                .andExpect(jsonPath("$[1].orden").value(1));
        mvc.perform(get(url(apto) + "/" + a).header("Authorization", bearer(asesor))).andExpect(status().isNotFound());
        mvc.perform(delete(url(apto) + "/" + a).header("Authorization", bearer(asesor))).andExpect(status().isNotFound());
        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto.version").isNotEmpty());
    }

    @Test
    void laGaleriaTieneUnTopeDeDoceImagenes() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Tope");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-7");
        byte[] pequena = imagen(100, 80, "jpg", 40);
        for (int i = 0; i < ServicioImagenesInmueble.MAX_IMAGENES; i++) {
            mvc.perform(subida(apto, asesor, pequena, "x.jpg")).andExpect(status().isCreated());
        }
        mvc.perform(subida(apto, asesor, pequena, "x.jpg")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("12 imágenes")));
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor))).andExpect(jsonPath("$", hasSize(12)));
    }

    @Test
    void elNavegadorReutilizaLaImagenConElEtag() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Etag");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-8");
        long id = agregar(apto, asesor, "FOTO", 10, "A");

        String etag = mvc.perform(get(url(apto) + "/" + id).header("Authorization", bearer(asesor)))
                .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();
        mvc.perform(get(url(apto) + "/" + id).header("Authorization", bearer(asesor)).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void rechazaArchivosQueNoSonImagenesOQueSonDemasiadoGrandes() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Inválidas");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-9");

        mvc.perform(subida(apto, asesor, pdfConTexto("contrato"), "foto.jpg"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value(containsString("no es una imagen válida")));
        mvc.perform(subida(apto, asesor, new byte[0], "vacia.jpg")).andExpect(status().isUnprocessableEntity());
        mvc.perform(subida(apto, asesor, new byte[9 * 1024 * 1024], "pesada.jpg"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value(containsString("8 MB")));
        mvc.perform(subida(apto, asesor, imagen(100, 80, "jpg", 10), "x.jpg").param("tipo", "VIDEO"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unInmuebleOUnaImagenDeOtraInmobiliariaRespondenComoSiNoExistieran() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Galería A");
        Inmobiliaria b = crearInmobiliaria("Galería B");
        Usuario asesorA = crearUsuario(a, Rol.ASESOR);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);
        Inmueble deB = crearInmueble(b, "GAL-B");
        long imagenB = agregar(deB, asesorB, "FOTO", 10, "B");
        Inmueble deA = crearInmueble(a, "GAL-A");

        mvc.perform(get(url(deB)).header("Authorization", bearer(asesorA))).andExpect(status().isNotFound());
        mvc.perform(get(url(deB) + "/" + imagenB).header("Authorization", bearer(asesorA))).andExpect(status().isNotFound());
        mvc.perform(subida(deB, asesorA, imagen(100, 80, "jpg", 10), "x.jpg")).andExpect(status().isNotFound());
        mvc.perform(delete(url(deB) + "/" + imagenB).header("Authorization", bearer(asesorA))).andExpect(status().isNotFound());
        mvc.perform(patch(url(deB) + "/" + imagenB).header("Authorization", bearer(asesorA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"focoX\": 1}")).andExpect(status().isNotFound());
        mvc.perform(put(url(deB) + "/orden").header("Authorization", bearer(asesorA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\": [" + imagenB + "]}")).andExpect(status().isNotFound());
        // Un inmueble propio con el id de la imagen ajena tampoco la alcanza.
        mvc.perform(get(url(deA) + "/" + imagenB).header("Authorization", bearer(asesorA))).andExpect(status().isNotFound());
        // La de B sigue intacta.
        mvc.perform(get(url(deB) + "/" + imagenB).header("Authorization", bearer(asesorB))).andExpect(status().isOk());
    }

    @Test
    void sinSesionNoSePuedeVerNiSubirNada() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Sin Sesión");
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-10");

        mvc.perform(get(url(apto))).andExpect(status().isUnauthorized());
        mvc.perform(multipart(HttpMethod.POST, url(apto)).file(new MockMultipartFile("archivo", "a.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaFotoYaSubidaEnLaMigracionNoSePierdeAlCambiarDeTipo() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Galería Tipo");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "GAL-11");
        long id = agregar(apto, asesor, "FOTO", 10, "A");
        mvc.perform(patch(url(apto) + "/" + id).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"PLANO\"}")).andExpect(status().isOk());
        // Ya no es foto: el inmueble queda sin portada.
        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto").value(nullValue()));
    }
}
