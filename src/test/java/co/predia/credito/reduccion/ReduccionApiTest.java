package co.predia.credito.reduccion;

import co.predia.clientes.Cliente;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReduccionApiTest extends PruebaIntegracion {

    private static final String URL = "/api/reducciones/simular";

    private static final String CUERPO = """
            {"banco": "Banco Ejemplo", "saldo": 150000000, "tasaEa": 0.11, "cuotasPendientes": 240,
             "seguroMensual": 60000, "frecuencia": "MENSUAL", "abonos": [500000, 700000, 1000000]%s}""";

    @Test
    void comparaTresAbonosContraElCreditoActualConCifrasDeLaCalculadora() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Reducción");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post(URL).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.cuotaInicial").value(1495708))
                // Cómo se reparte la primera cuota (la hoja impresa la muestra): intereses del primer mes y el resto a capital.
                .andExpect(jsonPath("$.primeraCuotaIntereses").value(1310189))
                .andExpect(jsonPath("$.primeraCuotaCapital").value(185519))
                .andExpect(jsonPath("$.actual.cuotas").value(240))
                .andExpect(jsonPath("$.actual.totalSeguro").value(14400000))
                .andExpect(jsonPath("$.escenarios", hasSize(3)))
                // 500.000 mensuales: 123 cuotas (el simulador original da lo mismo), 117 menos.
                .andExpect(jsonPath("$.escenarios[0].cuotas").value(123))
                .andExpect(jsonPath("$.escenarios[0].cuotasReducidas").value(117))
                .andExpect(jsonPath("$.escenarios[0].ahorroSeguro").value(7020000))
                // Cuota + seguro + abono, porque el abono es mensual.
                .andExpect(jsonPath("$.escenarios[0].desembolsoMensual").value(2055708))
                .andExpect(jsonPath("$.escenarios[1].cuotas").value(org.hamcrest.Matchers.lessThan(123)))
                .andExpect(jsonPath("$.escenarios[2].cuotas").value(org.hamcrest.Matchers.lessThan(
                        100)))
                .andExpect(jsonPath("$.escenarioDeLaTabla").value(1))
                .andExpect(jsonPath("$.tabla", hasSize(123)))
                .andExpect(jsonPath("$.aviso").exists());
    }

    @Test
    void sinAbonosDevuelveSoloElCreditoActual() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Solo Actual");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post(URL).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saldo": 80000000, "tasaEa": 0.095, "cuotasPendientes": 120}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.cuotaInicial").value(1018168))
                .andExpect(jsonPath("$.escenarios", hasSize(0)))
                .andExpect(jsonPath("$.escenarioDeLaTabla").value(0))
                .andExpect(jsonPath("$.tabla", hasSize(120)))
                .andExpect(jsonPath("$.actual.ahorroIntereses").value(nullValue()))
                .andExpect(jsonPath("$.modo").value("REDUCIR_PLAZO"));
    }

    @Test
    void conUnClienteIndicaElIngresoQueHaceFaltaYSiLoCumple() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Ingreso");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Cliente holgado = crearCliente(inmobiliaria, asesor, "Cliente holgado", "9000000", "10000000");
        Cliente justo = crearCliente(inmobiliaria, asesor, "Cliente justo", "3000000", "10000000");

        // Desembolso de 2.055.708 con el 30 % de la inmobiliaria: hacen falta 6.852.360.
        mvc.perform(post(URL).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted(", \"clienteId\": " + holgado.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteNombre").value("Cliente holgado"))
                .andExpect(jsonPath("$.escenarios[0].ingresoRequerido").value(6852360))
                .andExpect(jsonPath("$.escenarios[0].cumpleIngreso").value(true));
        mvc.perform(post(URL).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted(", \"clienteId\": " + justo.getId())))
                .andExpect(jsonPath("$.escenarios[0].cumpleIngreso").value(false));
    }

    @Test
    void reducirLaCuotaDejaElPlazoIgualYBajaLaCuota() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Modo Cuota");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post(URL).header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saldo": 150000000, "tasaEa": 0.11, "cuotasPendientes": 240,
                                 "modo": "REDUCIR_CUOTA", "frecuencia": "UNICO", "abonos": [20000000]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escenarios[0].cuotas").value(240))
                .andExpect(jsonPath("$.escenarios[0].cuotasReducidas").value(0))
                .andExpect(jsonPath("$.escenarios[0].cuotaFinal").value(
                        org.hamcrest.Matchers.lessThan(1495708)))
                .andExpect(jsonPath("$.escenarios[0].ahorroIntereses").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void unClienteDeOtraInmobiliariaRespondeComoSiNoExistiera() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Inmobiliaria Reducción A");
        Inmobiliaria b = crearInmobiliaria("Inmobiliaria Reducción B");
        Usuario asesorA = crearUsuario(a, Rol.ASESOR);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);
        Cliente deB = crearCliente(b, asesorB, "Cliente de B", "5000000", "10000000");

        mvc.perform(post(URL).header("Authorization", bearer(asesorA)).contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO.formatted(", \"clienteId\": " + deB.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaDatosInvalidosYExigeSesion() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Validación");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        String auth = bearer(asesor);

        // Tasa como porcentaje (11 en vez de 0,11): se rechaza en vez de simular un 1100 %.
        mvc.perform(post(URL).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saldo": 150000000, "tasaEa": 11, "cuotasPendientes": 240}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saldo": 0, "tasaEa": 0.11, "cuotasPendientes": 240}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saldo": 150000000, "tasaEa": 0.11, "cuotasPendientes": 361}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saldo": 150000000, "tasaEa": 0.11, "cuotasPendientes": 240, "abonos": [1, 2, 3, 4]}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"saldo": 150000000, "tasaEa": 0.11, "cuotasPendientes": 240,
                                 "abonos": [500000], "escenarioDeLaTabla": 2}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(CUERPO.formatted("")))
                .andExpect(status().isUnauthorized());
    }
}
