package co.predia.inmobiliaria;

import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import co.predia.inventario.TipoInmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un usuario de una inmobiliaria nunca ve ni modifica datos de otra. */
class AislamientoInmobiliariasTest extends PruebaIntegracion {

    @Autowired
    InmuebleRepository inmuebles;

    @Test
    void cadaInmobiliariaSoloVeSusInmuebles() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Inmobiliaria A");
        Inmobiliaria b = crearInmobiliaria("Inmobiliaria B");
        Inmueble deA = inmuebles.save(inmueble(a, "A-1", "Apto de A"));
        inmuebles.save(inmueble(b, "B-1", "Apto de B"));
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);

        mvc.perform(get("/api/inmuebles").header("Authorization", bearer(asesorB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Apto de B"));

        // Pedir por id un inmueble ajeno responde 404, igual que si no existiera.
        mvc.perform(get("/api/inmuebles/" + deA.getId()).header("Authorization", bearer(asesorB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAsesorNoPuedeCambiarParametros() throws Exception {
        Usuario asesor = crearUsuario(crearInmobiliaria("Inmobiliaria Parametros"), Rol.ASESOR);

        mvc.perform(put("/api/parametros").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(parametrosJson("0.35")))
                .andExpect(status().isForbidden());
        // Aunque el cuerpo sea inválido, primero se niega el permiso.
        mvc.perform(put("/api/parametros").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdminCambiaSoloLosParametrosDeSuInmobiliaria() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Inmobiliaria Admin A");
        Inmobiliaria b = crearInmobiliaria("Inmobiliaria Admin B");
        Usuario adminA = crearUsuario(a, Rol.ADMIN);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);

        mvc.perform(put("/api/parametros").header("Authorization", bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON).content(parametrosJson("0.35")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pctCuotaMaxIngreso").value(0.35));

        mvc.perform(get("/api/parametros").header("Authorization", bearer(asesorB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pctCuotaMaxIngreso").value(0.30));
    }

    private static Inmueble inmueble(Inmobiliaria inmobiliaria, String codigo, String titulo) {
        Inmueble i = new Inmueble(inmobiliaria.getId(), codigo, titulo, TipoInmueble.APARTAMENTO, Operacion.VENTA);
        i.setDireccion("Calle 1 # 2-3");
        i.setBarrio("Centro");
        i.setCiudad("Bucaramanga");
        i.setAreaM2(new BigDecimal("60"));
        i.setHabitaciones((short) 2);
        i.setPrecioVenta(new BigDecimal("200000000"));
        return i;
    }

    private static String parametrosJson(String pctCuota) {
        return """
                {"pctCuotaMaxIngreso": %s, "pctCuotaInicial": 0.30, "pctCuotaInicialVis": 0.20,
                 "plazoDefaultAnios": 20, "diasAlertaVencimiento": 7, "ciudadesConValorizacion": ["Bucaramanga"],
                 "umbralSimilitudRag": 0.3, "emailRemitente": "avisos@prueba.test"}""".formatted(pctCuota);
    }
}
