package co.predia.credito;

import co.predia.clientes.Cliente;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditoApiTest extends PruebaIntegracion {

    private static final String TASA_JSON = """
            {"banco": "Banco Prueba", "tipo": "NO_VIS", "tasaEa": 0.12, "vigenteDesde": "2026-01-01",
             "fuente": "Prueba", "esEjemplo": true}""";

    @Test
    void soloElAdminCargaTasasYElAsesorNo() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Tasas");
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/tasas").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(TASA_JSON))
                .andExpect(status().isForbidden());
        // Aunque el cuerpo sea inválido, primero se niega el permiso.
        mvc.perform(post("/api/tasas").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/tasas").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(TASA_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.banco").value("Banco Prueba"))
                .andExpect(jsonPath("$.esEjemplo").value(true));

        // El asesor sí puede consultarlas.
        mvc.perform(get("/api/tasas").header("Authorization", bearer(asesor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void laTasaTienePorcentajeValidoYLasDeOtraInmobiliariaNoSeVen() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Inmobiliaria Tasas A");
        Inmobiliaria b = crearInmobiliaria("Inmobiliaria Tasas B");
        Usuario adminA = crearUsuario(a, Rol.ADMIN);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);
        TasaCredito deA = crearTasa(a, "Banco A", TipoTasa.NO_VIS, "0.12");

        mvc.perform(post("/api/tasas").header("Authorization", bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASA_JSON.replace("0.12", "12")))  // 1200 %: inválida
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/tasas").header("Authorization", bearer(asesorB)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/tasas/" + deA.getId()).header("Authorization", bearer(crearUsuario(b, Rol.ADMIN))))
                .andExpect(status().isNotFound());
    }

    @Test
    void simulaUnCreditoDe210MillonesYDevuelveLaTasaUsadaConSuBancoYFecha() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Simulador");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        TasaCredito tasa = crearTasa(inmobiliaria, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");

        String respuesta = mvc.perform(post("/api/creditos/simular").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto": 210000000, "plazoAnios": 20, "tasaId": %d}""".formatted(tasa.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuotas").value(240))
                .andExpect(jsonPath("$.tasa.banco").value("Banco Ejemplo"))
                .andExpect(jsonPath("$.tasa.vigenteDesde").value("2026-01-01"))
                .andExpect(jsonPath("$.tasa.esEjemplo").value(true))
                .andExpect(jsonPath("$.aviso").value("Es una simulación; la aprobación es del banco."))
                .andExpect(jsonPath("$.cumpleCuotaMaxima").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        BigDecimal cuota = new BigDecimal(com.jayway.jsonpath.JsonPath.read(respuesta, "$.cuotaMensual").toString());
        assertThat(cuota).isBetween(new BigDecimal("2222000"), new BigDecimal("2224000"));
    }

    @Test
    void conInmuebleYClienteFinanciaPrecioMenosAhorroYVerificaElTope() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Simulador 2");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crearTasa(inmobiliaria, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");
        Inmueble inmueble = crearInmueble(inmobiliaria, "SIM-1");   // precio 200 M
        inmueble.setPrecioVenta(new BigDecimal("300000000"));
        inmuebles.save(inmueble);
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Paola y Juan", "7500000", "90000000");

        mvc.perform(post("/api/creditos/simular").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inmuebleId": %d, "clienteId": %d, "plazoAnios": 20}"""
                                .formatted(inmueble.getId(), cliente.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoFinanciado").value(210000000))
                .andExpect(jsonPath("$.cuotaMaxima").value(2250000))
                .andExpect(jsonPath("$.cumpleCuotaMaxima").value(true))
                .andExpect(jsonPath("$.cumpleCuotaInicial").value(true))
                .andExpect(jsonPath("$.cuotaInicialMinima").value(90000000))
                // 2.223.000 ÷ 7.500.000 ≈ 29,6 % del ingreso
                .andExpect(jsonPath("$.porcentajeSobreIngreso").value(0.2964));
    }

    @Test
    void siElAhorroNoAlcanzaLaCuotaInicialMinimaLoAdvierte() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Simulador 3");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crearTasa(inmobiliaria, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");
        Inmueble inmueble = crearInmueble(inmobiliaria, "SIM-2");
        inmueble.setPrecioVenta(new BigDecimal("300000000"));
        inmuebles.save(inmueble);
        Cliente cliente = crearCliente(inmobiliaria, asesor, "Cliente con poco ahorro", "7500000", "40000000");

        mvc.perform(post("/api/creditos/simular").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inmuebleId": %d, "clienteId": %d}""".formatted(inmueble.getId(), cliente.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumpleCuotaInicial").value(false));
    }

    @Test
    void sinTasaCargadaNoInventaUnaYAvisa() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Sin Tasa");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/creditos/simular").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"monto": 100000000, "plazoAnios": 15}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("No hay una tasa")));
    }

    @Test
    void noSePuedeSimularConUnClienteOUnInmuebleDeOtraInmobiliaria() throws Exception {
        Inmobiliaria propia = crearInmobiliaria("Inmobiliaria Simulador Propia");
        Inmobiliaria ajena = crearInmobiliaria("Inmobiliaria Simulador Ajena");
        Usuario asesorPropio = crearUsuario(propia, Rol.ASESOR);
        Usuario asesorAjeno = crearUsuario(ajena, Rol.ASESOR);
        crearTasa(propia, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");
        Cliente clienteAjeno = crearCliente(ajena, asesorAjeno, "Cliente ajeno", "5000000", "10000000");

        mvc.perform(post("/api/creditos/simular").header("Authorization", bearer(asesorPropio))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"monto": 100000000, "clienteId": %d}""".formatted(clienteAjeno.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void laCorreccionDeUnaTasaCambiaLaSimulacion() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Correccion");
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);
        TasaCredito tasa = crearTasa(inmobiliaria, "Banco Ejemplo", TipoTasa.NO_VIS, "0.12");

        mvc.perform(put("/api/tasas/" + tasa.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASA_JSON.replace("0.12", "0.10").replace("Banco Prueba", "Banco Ejemplo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasaEa").value(0.10));
    }
}
