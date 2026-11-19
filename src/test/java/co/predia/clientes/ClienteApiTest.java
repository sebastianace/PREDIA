package co.predia.clientes;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.credito.TipoTasa;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClienteApiTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;

    private static String clienteJson(String autorizacion) {
        return """
                {"nombre": "Paola y Juan", "email": "paola@ejemplo.test", "ingresoFamiliar": 7500000,
                 "ahorro": 90000000, "deudasMensuales": 0, "intencion": "COMPRAR", "habitacionesMin": 3,
                 "zonas": ["Floridablanca"], "preferencias": "Con parqueadero" %s}""".formatted(autorizacion);
    }

    @Test
    void noSeGuardaUnClienteSinLaAutorizacionDeDatos() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Clientes");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/clientes").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(clienteJson("")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("Ley 1581")));
        mvc.perform(post("/api/clientes").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(clienteJson(", \"autorizacionDatos\": false")))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/clientes").header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void conLaAutorizacionSeGuardaYQuedaRegistradaConSuFechaYSuAsesor() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Clientes 2");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        MvcResult creado = mvc.perform(post("/api/clientes").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson(", \"autorizacionDatos\": true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizacionDatos").value(true))
                .andExpect(jsonPath("$.autorizacionFecha").isNotEmpty())
                .andReturn();

        Long registradaPor = jdbc.queryForObject(
                "SELECT autorizacion_registrada_por FROM cliente WHERE id = ?", Long.class, idDe(creado));
        assertThat(registradaPor).isEqualTo(asesor.getId());
    }

    @Test
    void unaInmobiliariaNoVeNiBorraLosClientesDeOtra() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Inmobiliaria Clientes A");
        Inmobiliaria b = crearInmobiliaria("Inmobiliaria Clientes B");
        Usuario asesorA = crearUsuario(a, Rol.ASESOR);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);
        Cliente deA = crearCliente(a, asesorA, "Cliente de A", "5000000", "20000000");

        mvc.perform(get("/api/clientes").header("Authorization", bearer(asesorB)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/clientes/" + deA.getId()).header("Authorization", bearer(asesorB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/" + deA.getId() + "/presupuesto").header("Authorization", bearer(asesorB)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/clientes/" + deA.getId()).header("Authorization", bearer(asesorB)))
                .andExpect(status().isNotFound());

        // El dueño sí puede ejercer el derecho de supresión.
        mvc.perform(delete("/api/clientes/" + deA.getId()).header("Authorization", bearer(asesorA)))
                .andExpect(status().isNoContent());
        assertThat(clientes.findById(deA.getId())).isEmpty();
    }

    @Test
    void elPresupuestoDePaolaYJuanDaCuotaMaxima2250000YTopeDe300Millones() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Presupuesto");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crearTasa(inmobiliaria, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Paola y Juan", "7500000", "90000000");

        String respuesta = mvc.perform(get("/api/clientes/" + cliente.getId() + "/presupuesto")
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plazoAnios").value(20))   // el de los parámetros de la inmobiliaria
                .andExpect(jsonPath("$.presupuesto.cuotaMaxima").value(2250000))
                .andExpect(jsonPath("$.presupuesto.limitante").value("AHORRO"))
                .andExpect(jsonPath("$.tasa.banco").value("Banco Ejemplo"))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("ejemplo"))))
                .andReturn().getResponse().getContentAsString();

        BigDecimal tope = new BigDecimal(com.jayway.jsonpath.JsonPath.read(respuesta, "$.presupuesto.topeDeCompra")
                .toString());
        assertThat(tope).isBetween(new BigDecimal("299000000"), new BigDecimal("301000000"));
    }

    @Test
    void elPlazoYLaTasaPedidosCambianElPresupuesto() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Presupuesto 2");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crearTasa(inmobiliaria, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");
        // Mucho ahorro: manda la capacidad de pago, que sí depende del plazo.
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Cliente con ahorro", "7500000", "300000000");

        String a10 = mvc.perform(get("/api/clientes/" + cliente.getId() + "/presupuesto?plazoAnios=10")
                        .header("Authorization", bearer(asesor))).andReturn().getResponse().getContentAsString();
        String a20 = mvc.perform(get("/api/clientes/" + cliente.getId() + "/presupuesto?plazoAnios=20")
                        .header("Authorization", bearer(asesor))).andReturn().getResponse().getContentAsString();

        BigDecimal tope10 = new BigDecimal(com.jayway.jsonpath.JsonPath.read(a10, "$.presupuesto.topeDeCompra").toString());
        BigDecimal tope20 = new BigDecimal(com.jayway.jsonpath.JsonPath.read(a20, "$.presupuesto.topeDeCompra").toString());
        assertThat(tope20).isGreaterThan(tope10);

        mvc.perform(get("/api/clientes/" + cliente.getId() + "/presupuesto?plazoAnios=50")
                        .header("Authorization", bearer(asesor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conDeudasElPresupuestoLasMuestraComoAlertaSinRestarlas() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Deudas");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crearTasa(inmobiliaria, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Cliente con deudas", "7500000", "90000000");
        cliente.actualizarPerfil("Cliente con deudas", null, null, new BigDecimal("7500000"),
                new BigDecimal("90000000"), new BigDecimal("1500000"), Intencion.COMPRAR, (short) 3,
                java.util.List.of(), "");
        clientes.save(cliente);

        mvc.perform(get("/api/clientes/" + cliente.getId() + "/presupuesto").header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presupuesto.cuotaMaxima").value(2250000))
                .andExpect(jsonPath("$.presupuesto.alertaEndeudamiento").value(true))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("deudas"))));
    }
}
