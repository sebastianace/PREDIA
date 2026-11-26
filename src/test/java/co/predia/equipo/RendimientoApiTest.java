package co.predia.equipo;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cuánto vendió cada asesor: sale de los negocios cerrados, por periodo, y solo el administrador ve a todos. */
class RendimientoApiTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;

    /** Un negocio del asesor, ya cerrado hace {@code haceDias} por {@code valor}, que tardó {@code diasDeCierre} en cerrarse. */
    private Negocio venta(Inmobiliaria inm, Usuario asesor, String codigo, String valor, int haceDias, int diasDeCierre) {
        Inmueble inmueble = crearInmueble(inm, codigo);
        Propietario vendedor = crearPropietario(inm, asesor, "V " + codigo, codigo + "@ejemplo.test", TipoPersona.NATURAL,
                EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inm, asesor, inmueble, vendedor, null);
        Instant firmado = Instant.now().minus(haceDias, ChronoUnit.DAYS);
        // Se registra como lo hace un cierre real (restricción FIRMADO = con su registro completo).
        jdbc.update("UPDATE negocio SET estado = 'FIRMADO', firmado_en = ?, firmado_por = ?, valor_cierre = ?, creado_en = ? WHERE id = ?",
                Timestamp.from(firmado), asesor.getId(), new BigDecimal(valor), Timestamp.from(firmado.minus(diasDeCierre, ChronoUnit.DAYS)),
                negocio.getId());
        return negocio;
    }

    private void cancelado(Inmobiliaria inm, Usuario asesor, String codigo, int haceDias) {
        Inmueble inmueble = crearInmueble(inm, codigo);
        Propietario vendedor = crearPropietario(inm, asesor, "C " + codigo, codigo + "@ejemplo.test", TipoPersona.NATURAL,
                EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inm, asesor, inmueble, vendedor, null);
        jdbc.update("UPDATE negocio SET estado = 'CANCELADO', cancelado_en = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(haceDias, ChronoUnit.DAYS)), negocio.getId());
    }

    private String equipo(Usuario quien, String periodo) throws Exception {
        return mvc.perform(get("/api/equipo/rendimiento").param("periodo", periodo).header("Authorization", bearer(quien)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void elAdministradorVeLasVentasDeCadaAsesorOrdenadasYConLosTotales() throws Exception {
        Inmobiliaria inm = crearInmobiliaria("Equipo Todo");
        Usuario admin = crearUsuario(inm, Rol.ADMIN);
        Usuario laura = crearUsuario(inm, Rol.ASESOR);
        Usuario andres = crearUsuario(inm, Rol.ASESOR);
        venta(inm, laura, "L1", "300000000", 5, 30);
        venta(inm, laura, "L2", "500000000", 10, 40);
        venta(inm, andres, "A1", "250000000", 3, 20);
        cancelado(inm, andres, "A2", 4);
        // Un negocio abierto de Laura (no es venta todavía).
        Inmueble abierto = crearInmueble(inm, "L3");
        crearNegocio(inm, laura, abierto, crearPropietario(inm, laura, "V L3", "l3@ejemplo.test", TipoPersona.NATURAL, EstadoCivil.SOLTERO), null);

        String json = equipo(admin, "TODO");

        List<String> nombres = JsonPath.read(json, "$.asesores[*].nombre");
        assertThat(nombres).containsExactly(laura.getNombre(), andres.getNombre());       // ordenados por ventas
        assertThat(JsonPath.<Integer>read(json, "$.asesores[0].vendidos")).isEqualTo(2);
        assertThat(JsonPath.<Number>read(json, "$.asesores[0].valorVendido").longValue()).isEqualTo(800_000_000L);
        assertThat(JsonPath.<Integer>read(json, "$.asesores[0].abiertos")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(json, "$.asesores[0].diasPromedioCierre")).isEqualTo(35);    // (30 + 40) / 2
        assertThat(JsonPath.<Integer>read(json, "$.asesores[0].tasaDeCierre")).isEqualTo(100);
        assertThat(JsonPath.<Integer>read(json, "$.asesores[1].vendidos")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(json, "$.asesores[1].cancelados")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(json, "$.asesores[1].tasaDeCierre")).isEqualTo(50);
        assertThat(JsonPath.<Integer>read(json, "$.totales.vendidos")).isEqualTo(3);
        assertThat(JsonPath.<Number>read(json, "$.totales.valorVendido").longValue()).isEqualTo(1_050_000_000L);
        assertThat(JsonPath.<Integer>read(json, "$.totales.asesores")).isEqualTo(2);
    }

    @Test
    void elPeriodoDecideQueVentasCuentan() throws Exception {
        Inmobiliaria inm = crearInmobiliaria("Equipo Periodo");
        Usuario admin = crearUsuario(inm, Rol.ADMIN);
        Usuario laura = crearUsuario(inm, Rol.ASESOR);
        venta(inm, laura, "P1", "300000000", 0, 10);          // hoy
        venta(inm, laura, "P2", "200000000", 400, 10);        // hace más de un año

        assertThat(JsonPath.<Integer>read(equipo(admin, "MES"), "$.asesores[0].vendidos")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(equipo(admin, "ANIO"), "$.asesores[0].vendidos")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(equipo(admin, "TODO"), "$.asesores[0].vendidos")).isEqualTo(2);
        assertThat(JsonPath.<Number>read(equipo(admin, "ANIO"), "$.asesores[0].valorVendido").longValue()).isEqualTo(300_000_000L);
    }

    @Test
    void unAsesorSinVentasApareceEnCeroYSinTasaNiDias() throws Exception {
        Inmobiliaria inm = crearInmobiliaria("Equipo Cero");
        Usuario admin = crearUsuario(inm, Rol.ADMIN);
        Usuario nuevo = crearUsuario(inm, Rol.ASESOR);

        mvc.perform(get("/api/equipo/rendimiento").param("periodo", "ANIO").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asesores[0].usuarioId").value(nuevo.getId()))
                .andExpect(jsonPath("$.asesores[0].vendidos").value(0))
                .andExpect(jsonPath("$.asesores[0].diasPromedioCierre").value((Object) null))
                .andExpect(jsonPath("$.asesores[0].tasaDeCierre").value((Object) null));
    }

    @Test
    void soloElAdministradorVeAlEquipoYCadaAsesorSoloSusPropiasCifras() throws Exception {
        Inmobiliaria inm = crearInmobiliaria("Equipo Permisos");
        Usuario laura = crearUsuario(inm, Rol.ASESOR);
        Usuario andres = crearUsuario(inm, Rol.ASESOR);
        venta(inm, laura, "Q1", "300000000", 2, 10);
        venta(inm, andres, "Q2", "999000000", 2, 10);

        mvc.perform(get("/api/equipo/rendimiento").header("Authorization", bearer(laura))).andExpect(status().isForbidden());

        mvc.perform(get("/api/perfil/rendimiento").param("periodo", "TODO").header("Authorization", bearer(laura)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendimiento.usuarioId").value(laura.getId()))
                .andExpect(jsonPath("$.rendimiento.vendidos").value(1))
                .andExpect(jsonPath("$.rendimiento.valorVendido").value(300000000))
                .andExpect(jsonPath("$.ultimosCierres.length()").value(1))
                .andExpect(jsonPath("$.ultimosCierres[0].valor").value(300000000));
    }

    @Test
    void lasCifrasNuncaMezclanInmobiliarias() throws Exception {
        Inmobiliaria a = crearInmobiliaria("Equipo A");
        Inmobiliaria b = crearInmobiliaria("Equipo B");
        Usuario adminA = crearUsuario(a, Rol.ADMIN);
        Usuario asesorA = crearUsuario(a, Rol.ASESOR);
        Usuario asesorB = crearUsuario(b, Rol.ASESOR);
        venta(a, asesorA, "XA", "100000000", 1, 5);
        venta(b, asesorB, "XB", "900000000", 1, 5);

        String json = equipo(adminA, "TODO");

        assertThat(JsonPath.<Integer>read(json, "$.totales.vendidos")).isEqualTo(1);
        assertThat(JsonPath.<Number>read(json, "$.totales.valorVendido").longValue()).isEqualTo(100_000_000L);
        assertThat(JsonPath.<List<Integer>>read(json, "$.asesores[*].usuarioId")).doesNotContain(asesorB.getId().intValue());
    }

    @Test
    void losUltimosCierresVanDelMasRecienteAlMasAntiguoYUnPeriodoInvalidoSeRechaza() throws Exception {
        Inmobiliaria inm = crearInmobiliaria("Equipo Ultimos");
        Usuario laura = crearUsuario(inm, Rol.ASESOR);
        venta(inm, laura, "U1", "100000000", 30, 5);
        venta(inm, laura, "U2", "200000000", 3, 5);
        venta(inm, laura, "U3", "300000000", 15, 5);

        String json = mvc.perform(get("/api/perfil/rendimiento").param("periodo", "TODO").header("Authorization", bearer(laura)))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<Number>>read(json, "$.ultimosCierres[*].valor").stream().map(Number::longValue).toList())
                .containsExactly(200_000_000L, 300_000_000L, 100_000_000L);
        mvc.perform(get("/api/perfil/rendimiento").param("periodo", "SIEMPRE").header("Authorization", bearer(laura)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laBaseNoAceptaUnNegocioFirmadoSinSuRegistroDeCierre() {
        Inmobiliaria inm = crearInmobiliaria("Equipo Restriccion");
        Usuario laura = crearUsuario(inm, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inm, "R1");
        Negocio negocio = crearNegocio(inm, laura, inmueble,
                crearPropietario(inm, laura, "V R1", "r1@ejemplo.test", TipoPersona.NATURAL, EstadoCivil.SOLTERO), null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("UPDATE negocio SET estado = 'FIRMADO' WHERE id = ?", negocio.getId()))
                .hasMessageContaining("ck_negocio_cierre");
    }
}
