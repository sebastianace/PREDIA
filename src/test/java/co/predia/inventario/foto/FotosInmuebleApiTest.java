package co.predia.inventario.foto;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FotosInmuebleApiTest extends PruebaIntegracion {

    private static byte[] foto(int ancho, int alto, String formato) throws IOException {
        BufferedImage i = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(new Color(20, 70, 160));
        g.fillRect(0, 0, ancho, alto);
        g.setColor(Color.WHITE);
        g.fillRect(ancho / 3, alto / 3, ancho / 3, alto / 3);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(i, formato, salida);
        return salida.toByteArray();
    }

    private static String url(Inmueble i) {
        return "/api/inmuebles/" + i.getId() + "/foto";
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder subida(
            Inmueble i, Usuario u, byte[] bytes, String nombre, String tipo) {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
                multipart(HttpMethod.PUT, url(i)).file(new MockMultipartFile("archivo", nombre, tipo, bytes))
                        .header("Authorization", bearer(u));
    }

    @Test
    void subirUnaFotoLaGuardaReducidaYLaListaDelInventarioLaMenciona() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Inmobiliaria");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-1");

        mvc.perform(subida(apto, asesor, foto(4000, 3000, "jpg"), "casa.jpg", "image/jpeg").param("focoX", "30").param("focoY", "70"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ancho").value(1600))
                .andExpect(jsonPath("$.alto").value(1200))
                .andExpect(jsonPath("$.focoX").value(30))
                .andExpect(jsonPath("$.focoY").value(70))
                .andExpect(jsonPath("$.version").isNotEmpty());

        MvcResult grande = mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
        MvcResult mini = mvc.perform(get(url(apto)).param("tam", "miniatura").header("Authorization", bearer(asesor)))
                .andExpect(status().isOk()).andReturn();
        BufferedImage g = ImageIO.read(new ByteArrayInputStream(grande.getResponse().getContentAsByteArray()));
        BufferedImage m = ImageIO.read(new ByteArrayInputStream(mini.getResponse().getContentAsByteArray()));
        assertThat(g.getWidth()).isEqualTo(1600);
        assertThat(m.getWidth()).isEqualTo(480);
        assertThat(mini.getResponse().getContentAsByteArray().length)
                .isLessThan(grande.getResponse().getContentAsByteArray().length);

        mvc.perform(get("/api/inmuebles").header("Authorization", bearer(asesor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].foto.version").isNotEmpty())
                .andExpect(jsonPath("$[0].foto.focoX").value(30));
        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto.focoY").value(70));
    }

    @Test
    void unInmuebleSinFotoDevuelveFotoNulaYElArchivoRespondeNoEncontrado() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Sin Foto");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-2");

        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto").value(nullValue()));
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor))).andExpect(status().isNotFound());
    }

    @Test
    void aceptaPngVerticalYLaVuelveJpeg() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Png");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-3");

        mvc.perform(subida(apto, asesor, foto(2000, 3000, "png"), "vertical.png", "image/png"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ancho").value(1067)).andExpect(jsonPath("$.alto").value(1600))
                .andExpect(jsonPath("$.focoX").value(50));
        byte[] bytes = mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xFF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void cambiarLaFotoCambiaLaVersionYQuitarlaLaBorra() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Cambio");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-4");

        String v1 = com.jayway.jsonpath.JsonPath.read(mvc.perform(subida(apto, asesor, foto(800, 600, "jpg"), "a.jpg", "image/jpeg"))
                .andReturn().getResponse().getContentAsString(), "$.version");
        String v2 = com.jayway.jsonpath.JsonPath.read(mvc.perform(subida(apto, asesor, foto(900, 600, "jpg"), "b.jpg", "image/jpeg"))
                .andReturn().getResponse().getContentAsString(), "$.version");
        assertThat(v2).isNotEqualTo(v1);

        mvc.perform(delete(url(apto)).header("Authorization", bearer(asesor))).andExpect(status().isNoContent());
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor))).andExpect(status().isNotFound());
        mvc.perform(get("/api/inmuebles/" + apto.getId()).header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.foto").value(nullValue()));
    }

    @Test
    void elEncuadreSeCambiaSinVolverASubirLaImagen() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Foco");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-5");
        mvc.perform(subida(apto, asesor, foto(1000, 1500, "jpg"), "a.jpg", "image/jpeg")).andExpect(status().isOk());

        mvc.perform(patch(url(apto) + "/foco").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"focoX\": 10, \"focoY\": 20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.focoX").value(10)).andExpect(jsonPath("$.focoY").value(20));
        mvc.perform(patch(url(apto) + "/foco").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"focoX\": 150, \"focoY\": 20}"))
                .andExpect(status().isBadRequest());

        // Sin foto no hay encuadre que cambiar.
        Inmueble otro = crearInmueble(inmobiliaria, "FOT-5B");
        mvc.perform(patch(url(otro) + "/foco").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"focoX\": 10, \"focoY\": 20}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elNavegadorReutilizaLaImagenConElEtag() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Etag");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-6");
        mvc.perform(subida(apto, asesor, foto(800, 600, "jpg"), "a.jpg", "image/jpeg")).andExpect(status().isOk());

        String etag = mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)))
                .andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor)).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void rechazaArchivosQueNoSonImagenesOQueSonDemasiadoGrandes() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Invalidas");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-7");

        // Un PDF con extensión .jpg: se decodifica de verdad, no se confía en el nombre ni en el tipo declarado.
        mvc.perform(subida(apto, asesor, pdfConTexto("contrato"), "foto.jpg", "image/jpeg"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no es una imagen válida")));
        mvc.perform(subida(apto, asesor, new byte[0], "vacia.jpg", "image/jpeg")).andExpect(status().isUnprocessableEntity());
        mvc.perform(subida(apto, asesor, new byte[9 * 1024 * 1024], "pesada.jpg", "image/jpeg"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("8 MB")));
        mvc.perform(get(url(apto)).header("Authorization", bearer(asesor))).andExpect(status().isNotFound());
    }

    @Test
    void unInmuebleOUnaFotoDeOtraInmobiliariaRespondenComoSiNoExistieran() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Fotos A");
        Inmobiliaria b = crearInmobiliaria("Fotos B");
        Usuario asesorA = crearUsuario(a, Rol.ASESOR);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);
        Inmueble deB = crearInmueble(b, "FOT-B");
        mvc.perform(subida(deB, asesorB, foto(800, 600, "jpg"), "b.jpg", "image/jpeg")).andExpect(status().isOk());

        mvc.perform(get(url(deB)).header("Authorization", bearer(asesorA))).andExpect(status().isNotFound());
        mvc.perform(subida(deB, asesorA, foto(800, 600, "jpg"), "x.jpg", "image/jpeg")).andExpect(status().isNotFound());
        mvc.perform(delete(url(deB)).header("Authorization", bearer(asesorA))).andExpect(status().isNotFound());
        mvc.perform(patch(url(deB) + "/foco").header("Authorization", bearer(asesorA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"focoX\": 1, \"focoY\": 1}"))
                .andExpect(status().isNotFound());
        // La de B sigue intacta y A no la ve en su lista.
        mvc.perform(get(url(deB)).header("Authorization", bearer(asesorB))).andExpect(status().isOk());
        mvc.perform(get("/api/inmuebles").header("Authorization", bearer(asesorA))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sinSesionNoSePuedeVerNiSubirNada() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Fotos Sin Sesion");
        Inmueble apto = crearInmueble(inmobiliaria, "FOT-8");

        mvc.perform(get(url(apto))).andExpect(status().isUnauthorized());
        mvc.perform(multipart(HttpMethod.PUT, url(apto)).file(new MockMultipartFile("archivo", "a.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }
}
