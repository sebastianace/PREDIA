package co.predia.comprador;

import co.predia.clientes.Cliente;
import co.predia.credito.TipoTasa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.EstadoInmueble;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El portal del comprador: ve solo lo que su asesor le compartió, sin código, dirección exacta ni datos del propietario;
 * las cifras las calcula el servidor; y la matriz de acceso comprueba que cada rol queda en su lado.
 */
class PortalCompradorApiTest extends PruebaIntegracion {

    @MockitoBean JavaMailSender correo;
    @Autowired ServicioCompartidos servicioCompartidos;

    private record Escenario(Inmobiliaria inmobiliaria, Usuario asesor, Cliente cliente, Usuario comprador,
                             Inmueble inmueble) {
    }

    private Escenario escenario(String nombre) {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Comprador " + nombre);
        crearTasa(inmobiliaria, "Banco " + nombre, TipoTasa.NO_VIS, "0.12");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Compradora " + nombre, "9000000", "90000000");
        cliente.actualizarPerfil(cliente.getNombre(), "comprador-" + UUID.randomUUID() + "@prueba.test", null,
                cliente.getIngresoFamiliar(), cliente.getAhorro(), BigDecimal.ZERO, cliente.getIntencion(),
                (short) 2, java.util.List.of("Floridablanca"), null);
        cliente = clientes.save(cliente);
        Usuario comprador = Usuario.invitadoComprador(inmobiliaria.getId(), cliente.getNombre(), cliente.getEmail(),
                cliente.getId(), asesor.getId());
        comprador.fijarContrasena(passwordEncoder.encode(PASSWORD_PRUEBA));
        comprador = usuarios.save(comprador);
        Inmueble inmueble = crearInmueble(inmobiliaria, "SECRETO-" + nombre);
        inmueble.setDireccion("Carrera 99 # 88-77 Torre Privada");
        inmuebles.save(inmueble);
        return new Escenario(inmobiliaria, asesor, cliente, comprador, inmueble);
    }

    private void compartir(Escenario e, String nota) throws Exception {
        mvc.perform(post("/api/clientes/" + e.cliente().getId() + "/inmuebles-compartidos")
                        .header("Authorization", bearer(e.asesor())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inmuebleId\":" + e.inmueble().getId() + ",\"nota\":\"" + nota + "\"}"))
                .andExpect(status().isOk());
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void elCompradorSoloVeLoQueSuAsesorLeComparteYSinDatosInternos() throws Exception {
        Escenario e = escenario("Ve");
        mvc.perform(get("/api/comprador/inmuebles").header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        compartir(e, "Le puede gustar");

        String lista = mvc.perform(get("/api/comprador/inmuebles").header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(e.inmueble().getId()))
                .andExpect(jsonPath("$[0].nota").value("Le puede gustar"))
                .andReturn().getResponse().getContentAsString();
        String ficha = mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId())
                        .header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulador.cuotaInicialMinima").isNumber())
                .andReturn().getResponse().getContentAsString();

        assertThat(lista + ficha).doesNotContain("\"codigo\"").doesNotContain("Torre Privada")
                .doesNotContain("Carrera 99").doesNotContain("propietario");
    }

    @Test
    void unInmuebleNoCompartidoOCompartidoAOtroEs404() throws Exception {
        Escenario a = escenario("A");
        Escenario b = escenario("B");
        compartir(b, "Solo para B");
        // Otro comprador de la MISMA inmobiliaria que B: tampoco lo ve.
        Cliente otro = crearCliente(b.inmobiliaria(), b.asesor(), "Otra", "5000000", "1000000");
        Usuario otroUsuario = Usuario.invitadoComprador(b.inmobiliaria().getId(), "Otra",
                "otra-" + UUID.randomUUID() + "@prueba.test", otro.getId(), b.asesor().getId());
        otroUsuario.fijarContrasena(passwordEncoder.encode(PASSWORD_PRUEBA));
        otroUsuario = usuarios.save(otroUsuario);

        mvc.perform(get("/api/comprador/inmuebles/" + a.inmueble().getId()).header("Authorization", bearer(a.comprador())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/comprador/inmuebles/" + b.inmueble().getId()).header("Authorization", bearer(a.comprador())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/comprador/inmuebles/" + b.inmueble().getId()).header("Authorization", bearer(otroUsuario)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/comprador/inmuebles/" + b.inmueble().getId() + "/foto")
                        .header("Authorization", bearer(otroUsuario))).andExpect(status().isNotFound());
        mvc.perform(post("/api/comprador/inmuebles/" + b.inmueble().getId() + "/simulacion")
                        .header("Authorization", bearer(otroUsuario)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuotaInicial\":90000000}")).andExpect(status().isNotFound());
    }

    @Test
    void laSimulacionLaCalculaElServidorYRespetaElRango() throws Exception {
        Escenario e = escenario("Sim");
        compartir(e, "x");
        String ruta = "/api/comprador/inmuebles/" + e.inmueble().getId();
        String ficha = mvc.perform(get(ruta).header("Authorization", bearer(e.comprador())))
                .andReturn().getResponse().getContentAsString();
        Number minima = JsonPath.read(ficha, "$.simulador.cuotaInicialMinima");
        Number maxima = JsonPath.read(ficha, "$.simulador.cuotaInicialMaxima");

        mvc.perform(post(ruta + "/simulacion").header("Authorization", bearer(e.comprador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuotaInicial\":" + maxima + ",\"plazoAnios\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoFinanciado").value(200000000 - maxima.longValue()))
                .andExpect(jsonPath("$.numeroCuotas").value(180))
                .andExpect(jsonPath("$.cuotaMensual").isNumber())
                .andExpect(jsonPath("$.veredicto").isNotEmpty());
        mvc.perform(post(ruta + "/simulacion").header("Authorization", bearer(e.comprador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuotaInicial\":" + (minima.longValue() - 1) + "}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(ruta + "/simulacion").header("Authorization", bearer(e.comprador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuotaInicial\":" + (maxima.longValue() + 1) + "}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(ruta + "/simulacion").header("Authorization", bearer(e.comprador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuotaInicial\":" + maxima + ",\"plazoAnios\":99}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void siElInmuebleYaNoEstaDisponibleSeVeperoNoSeSimula() throws Exception {
        Escenario e = escenario("Vendido");
        compartir(e, "x");
        e.inmueble().setEstado(EstadoInmueble.VENDIDO);
        inmuebles.save(e.inmueble());

        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId()).header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(false));
        mvc.perform(post("/api/comprador/inmuebles/" + e.inmueble().getId() + "/simulacion")
                        .header("Authorization", bearer(e.comprador())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuotaInicial\":90000000}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elInteresYLosMensajesLlegarAlAsesorSinEnviarCorreos() throws Exception {
        Escenario e = escenario("Msg");
        compartir(e, "x");
        String ruta = "/api/comprador/inmuebles/" + e.inmueble().getId();

        mvc.perform(put(ruta + "/interes").header("Authorization", bearer(e.comprador()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"interes\":\"ME_INTERESA\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/clientes/" + e.cliente().getId() + "/inmuebles-compartidos")
                        .header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$[0].interes").value("ME_INTERESA"));

        mvc.perform(post("/api/comprador/solicitudes").header("Authorization", bearer(e.comprador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inmuebleId\":" + e.inmueble().getId() + ",\"mensaje\":\"Quiero verlo el sábado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.atendida").value(false));
        String pendientes = mvc.perform(get("/api/solicitudes-comprador").header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clienteNombre").value(e.cliente().getNombre()))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(pendientes, "$[0].id");
        mvc.perform(get("/api/resumen-diario").header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$.solicitudesPorAtender").value(1));

        mvc.perform(post("/api/solicitudes-comprador/" + id + "/atender").header("Authorization", bearer(e.asesor())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/solicitudes-comprador").header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/comprador/solicitudes").header("Authorization", bearer(e.comprador())))
                .andExpect(jsonPath("$[0].atendida").value(true));
        verifyNoInteractions(correo);
    }

    @Test
    void noSePuedenAcumularMasDeCincoMensajesSinAtender() throws Exception {
        Escenario e = escenario("Tope");
        for (int i = 0; i < ServicioComprador.MAX_SOLICITUDES_SIN_ATENDER; i++) {
            mvc.perform(post("/api/comprador/solicitudes").header("Authorization", bearer(e.comprador()))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Hola " + i + "\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/comprador/solicitudes").header("Authorization", bearer(e.comprador()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"Otro\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/comprador/solicitudes").header("Authorization", bearer(e.comprador()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dejarDeCompartirCierraLaPuertaAlInstante() throws Exception {
        Escenario e = escenario("Cierra");
        compartir(e, "x");
        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId()).header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/clientes/" + e.cliente().getId() + "/inmuebles-compartidos/" + e.inmueble().getId())
                .header("Authorization", bearer(e.asesor()))).andExpect(status().isOk());
        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId()).header("Authorization", bearer(e.comprador())))
                .andExpect(status().isNotFound());
    }

    @Test
    void laGaleriaSoloSeVeSiElInmuebleSeComparteYIncluyeFotosYPlanos() throws Exception {
        Escenario e = escenario("Galería");
        String ruta = "/api/inmuebles/" + e.inmueble().getId() + "/imagenes";
        long plano = subirImagen(e, ruta, "PLANO", "Plano del piso");
        long foto = subirImagen(e, ruta, "FOTO", "Sala");

        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId() + "/imagenes/" + foto)
                .header("Authorization", bearer(e.comprador()))).andExpect(status().isNotFound());
        compartir(e, "x");
        for (long id : new long[]{plano, foto}) {
            for (String tam : new String[]{"grande", "miniatura"}) {
                mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId() + "/imagenes/" + id).param("tam", tam)
                                .header("Authorization", bearer(e.comprador())))
                        .andExpect(status().isOk()).andExpect(r -> assertThat(r.getResponse().getContentType())
                                .isEqualTo("image/jpeg"));
            }
        }
        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId()).header("Authorization", bearer(e.comprador())))
                .andExpect(jsonPath("$.imagenes", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.imagenes[0].id").value(plano)).andExpect(jsonPath("$.imagenes[0].tipo").value("PLANO"))
                .andExpect(jsonPath("$.imagenes[1].tipo").value("FOTO")).andExpect(jsonPath("$.imagenes[1].etiqueta").value("Sala"));
        // Una imagen que no es de ese inmueble no se alcanza con un id ajeno.
        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId() + "/imagenes/999999")
                .header("Authorization", bearer(e.comprador()))).andExpect(status().isNotFound());
    }

    @Test
    void laPortadaQueVeElCompradorSeRevalidaYSeActualizaCuandoElAsesorCambiaLaFoto() throws Exception {
        Escenario e = escenario("Portada caché");
        String ruta = "/api/inmuebles/" + e.inmueble().getId() + "/imagenes";
        long primera = subirImagen(e, ruta, "FOTO", "Primera");
        compartir(e, "x");
        String portada = "/api/comprador/inmuebles/" + e.inmueble().getId() + "/foto";

        org.springframework.test.web.servlet.MvcResult antes = mvc.perform(get(portada).param("tam", "miniatura")
                        .header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("max-age")))).andReturn();
        String etagAntes = antes.getResponse().getHeader("ETag");
        assertThat(etagAntes).isNotBlank();
        mvc.perform(get(portada).param("tam", "miniatura").header("If-None-Match", etagAntes)
                .header("Authorization", bearer(e.comprador()))).andExpect(status().isNotModified());

        // El asesor pone otra foto de primera: el comprador recibe la nueva, no la que tenía guardada.
        long segunda = subirImagenColor(e, ruta, "Segunda");
        mvc.perform(put(ruta + "/orden").header("Authorization", bearer(e.asesor())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[" + segunda + "," + primera + "]}")).andExpect(status().is2xxSuccessful());
        mvc.perform(get(portada).param("tam", "miniatura").header("If-None-Match", etagAntes)
                        .header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.not(etagAntes)));
        // Y la ficha anuncia la versión nueva, que es la que el navegador pone en la dirección de la imagen.
        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId()).header("Authorization", bearer(e.comprador())))
                .andExpect(jsonPath("$.foto.version").isNotEmpty());
    }

    private long subirImagenColor(Escenario e, String ruta, String etiqueta) throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.ORANGE);
        g.fillRect(0, 0, 40, 30);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        MockMultipartFile imagen = new MockMultipartFile("archivo", "y.png", "image/png", out.toByteArray());
        String cuerpo = mvc.perform(multipart(org.springframework.http.HttpMethod.POST, ruta).file(imagen)
                        .param("tipo", "FOTO").param("etiqueta", etiqueta).header("Authorization", bearer(e.asesor())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private long subirImagen(Escenario e, String ruta, String tipo, String etiqueta) throws Exception {
        MockMultipartFile imagen = new MockMultipartFile("archivo", "x.png", "image/png", png());
        String cuerpo = mvc.perform(multipart(org.springframework.http.HttpMethod.POST, ruta).file(imagen)
                        .param("tipo", tipo).param("etiqueta", etiqueta).header("Authorization", bearer(e.asesor())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(cuerpo, "$.id")).longValue();
    }

    @Test
    void invitarAlClienteExigeCorreoYNoUsaUnoYaOcupadoPorOtroRol() throws Exception {
        Escenario e = escenario("Invita");
        Cliente sinCorreo = crearCliente(e.inmobiliaria(), e.asesor(), "Sin correo", "5000000", "1000000");
        mvc.perform(post("/api/clientes/" + sinCorreo.getId() + "/invitacion").header("Authorization", bearer(e.asesor())))
                .andExpect(status().isUnprocessableEntity());

        Cliente conCorreoDelAsesor = crearCliente(e.inmobiliaria(), e.asesor(), "Colisión", "5000000", "1000000");
        conCorreoDelAsesor.actualizarPerfil("Colisión", e.asesor().getEmail(), null, new BigDecimal("5000000"),
                new BigDecimal("1000000"), BigDecimal.ZERO, conCorreoDelAsesor.getIntencion(), (short) 1,
                java.util.List.of(), null);
        clientes.save(conCorreoDelAsesor);
        mvc.perform(post("/api/clientes/" + conCorreoDelAsesor.getId() + "/invitacion")
                .header("Authorization", bearer(e.asesor()))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void desactivarElAccesoCortaLaSesionDelComprador() throws Exception {
        Escenario e = escenario("Corta");
        mvc.perform(get("/api/comprador/perfil").header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk());
        mvc.perform(put("/api/clientes/" + e.cliente().getId() + "/acceso").header("Authorization", bearer(e.asesor()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/comprador/perfil").header("Authorization", bearer(e.comprador())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unInmuebleQueSuperaElTopeLlevaElExcedenteCalculadoPorElServidor() throws Exception {
        Escenario e = escenario("Tope");
        compartir(e, "Para mirar");
        String perfil = mvc.perform(get("/api/comprador/perfil").header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal tope = new BigDecimal(com.jayway.jsonpath.JsonPath.read(perfil, "$.presupuesto.topeDeCompra").toString());

        // Dentro del tope no hay aviso, ni en la lista ni en la ficha.
        e.inmueble().setPrecioVenta(tope.subtract(BigDecimal.valueOf(1_000_000)));
        inmuebles.save(e.inmueble());
        mvc.perform(get("/api/comprador/inmuebles").header("Authorization", bearer(e.comprador())))
                .andExpect(jsonPath("$[0].excedenteSobreTope").doesNotExist());
        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId()).header("Authorization", bearer(e.comprador())))
                .andExpect(jsonPath("$.excedenteSobreTope").doesNotExist());

        // Pasado el tope, el servidor dice por cuánto.
        e.inmueble().setPrecioVenta(tope.add(BigDecimal.valueOf(30_000_000)));
        inmuebles.save(e.inmueble());
        mvc.perform(get("/api/comprador/inmuebles").header("Authorization", bearer(e.comprador())))
                .andExpect(jsonPath("$[0].excedenteSobreTope").value(30_000_000));
        mvc.perform(get("/api/comprador/inmuebles/" + e.inmueble().getId()).header("Authorization", bearer(e.comprador())))
                .andExpect(jsonPath("$.excedenteSobreTope").value(30_000_000));
    }

    @Test
    void elPerfilExplicaElTopeDeCompraYNombraAlAsesor() throws Exception {
        Escenario e = escenario("Perfil");
        mvc.perform(get("/api/comprador/perfil").header("Authorization", bearer(e.comprador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(e.cliente().getNombre()))
                .andExpect(jsonPath("$.asesor.nombre").value(e.asesor().getNombre()))
                .andExpect(jsonPath("$.presupuesto.topeDeCompra").isNumber())
                .andExpect(jsonPath("$.presupuesto.limitante").isNotEmpty());
    }

    @Test
    void matrizDeAccesoCadaRolQuedaEnSuLado() throws Exception {
        Escenario e = escenario("Matriz");
        Usuario admin = crearUsuario(e.inmobiliaria(), Rol.ADMIN);

        // El comprador no entra a nada del personal ni del portal del propietario.
        for (String ruta : new String[]{"/api/clientes", "/api/inmuebles", "/api/portal/negocios", "/api/usuarios",
                "/api/solicitudes-comprador", "/api/resumen-diario", "/api/perfil/rendimiento", "/api/equipo/rendimiento",
                "/api/perfil/usuarios/1/foto"}) {
            mvc.perform(get(ruta).header("Authorization", bearer(e.comprador()))).andExpect(status().isForbidden());
        }
        // El personal no entra al portal del comprador.
        for (Usuario u : new Usuario[]{e.asesor(), admin}) {
            mvc.perform(get("/api/comprador/perfil").header("Authorization", bearer(u))).andExpect(status().isForbidden());
        }
        // Sin sesión, nada.
        mvc.perform(get("/api/comprador/perfil")).andExpect(status().isUnauthorized());
    }
}
