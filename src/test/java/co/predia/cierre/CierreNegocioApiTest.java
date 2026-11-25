package co.predia.cierre;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.EstadoInmueble;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cerrar un negocio (registrar una venta firmada) y reabrirlo: las reglas y quién puede hacerlo. */
class CierreNegocioApiTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;
    @Autowired ParametrosInmobiliariaRepository parametros;

    private record Escenario(Inmobiliaria inmobiliaria, Usuario asesor, Usuario admin, Inmueble inmueble, Negocio negocio) {
    }

    private Escenario escenario(String nombre) {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Cierre " + nombre);
        // Sin margen de alerta: un documento del mes no cuenta como «por vencer» antes de la firma.
        ParametrosInmobiliaria p = parametros.findById(inmobiliaria.getId())
                .orElseGet(() -> new ParametrosInmobiliaria(inmobiliaria.getId()));
        p.actualizar(p.getPctCuotaMaxIngreso(), p.getPctCuotaInicial(), p.getPctCuotaInicialVis(), p.getPlazoDefaultAnios(), 0,
                p.getCiudadesConValorizacion(), p.getUmbralSimilitudRag(), p.getEmailRemitente());
        parametros.save(p);
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);
        Inmueble inmueble = crearInmueble(inmobiliaria, "CN-" + nombre);
        inmueble.setPrecioVenta(new BigDecimal("300000000"));
        inmuebles.save(inmueble);
        Propietario vendedor = crearPropietario(inmobiliaria, asesor, "Vendedor " + nombre, nombre + "@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        return new Escenario(inmobiliaria, asesor, admin, inmueble, crearNegocio(inmobiliaria, asesor, inmueble, vendedor, null));
    }

    private void recibir(Escenario e, String codigo, LocalDate fecha) throws Exception {
        String estado = mvc.perform(get("/api/negocios/" + e.negocio().getId() + "/estado-documental")
                .header("Authorization", bearer(e.asesor()))).andReturn().getResponse().getContentAsString();
        Number id = ((List<Number>) JsonPath.read(estado, "$.items[?(@.codigo=='" + codigo + "')].id")).get(0);
        mvc.perform(put("/api/negocios/" + e.negocio().getId() + "/requisitos/" + id)
                        .header("Authorization", bearer(e.asesor())).contentType(MediaType.APPLICATION_JSON)
                        .content(fecha == null ? "{\"accion\": \"RECIBIR\"}"
                                : "{\"accion\": \"RECIBIR\", \"fechaExpedicion\": \"%s\"}".formatted(fecha)))
                .andExpect(status().isOk());
    }

    private void completarDocumentos(Escenario e) throws Exception {
        recibir(e, "CEDULA_VENDEDOR", null);
        recibir(e, "CERTIFICADO_TRADICION", hoy().minusDays(2));
        recibir(e, "ESCRITURA_ADQUISICION", null);
        recibir(e, "PREDIAL", hoy());
        recibir(e, "ADMINISTRACION", hoy());
        recibir(e, "PROMESA_COMPRAVENTA", null);
    }

    private org.springframework.test.web.servlet.ResultActions cerrar(Escenario e, Usuario quien, String cuerpo) throws Exception {
        return mvc.perform(post("/api/negocios/" + e.negocio().getId() + "/cerrar").header("Authorization", bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private EstadoInmueble estadoDelInmueble(Escenario e) {
        return inmuebles.findById(e.inmueble().getId()).orElseThrow().getEstado();
    }

    @Test
    void elAsesorCierraUnNegocioListoYElInmuebleQuedaVendidoConElPrecioPublicado() throws Exception {
        Escenario e = escenario("Feliz");
        completarDocumentos(e);

        cerrar(e, e.asesor(), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FIRMADO"))
                .andExpect(jsonPath("$.firmadoEn").exists())
                .andExpect(jsonPath("$.valorCierre").value(300000000))
                .andExpect(jsonPath("$.puedeCerrar").value(false));

        assertThat(estadoDelInmueble(e)).isEqualTo(EstadoInmueble.VENDIDO);
        // Queda registrado quién lo confirmó.
        assertThat(jdbc.queryForObject("SELECT firmado_por FROM negocio WHERE id = ?", Long.class, e.negocio().getId()))
                .isEqualTo(e.asesor().getId());
    }

    @Test
    void elAsesorPuedeConfirmarOtroValorDeCierre() throws Exception {
        Escenario e = escenario("Valor");
        completarDocumentos(e);

        cerrar(e, e.asesor(), "{\"valorCierre\": 285000000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.valorCierre").value(285000000));
        Escenario otro = escenario("ValorCero");
        completarDocumentos(otro);
        cerrar(otro, otro.asesor(), "{\"valorCierre\": 0}").andExpect(status().isBadRequest());
        cerrar(otro, otro.asesor(), "{\"valorCierre\": -5}").andExpect(status().isBadRequest());
        assertThat(estadoDelInmueble(otro)).isEqualTo(EstadoInmueble.DISPONIBLE);
    }

    @Test
    void sinLaListaDeDocumentosCompletaUnAsesorNoPuedeCerrar() throws Exception {
        Escenario e = escenario("Incompleto");
        recibir(e, "CEDULA_VENDEDOR", null);

        cerrar(e, e.asesor(), "{}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Todavía no se puede cerrar")));

        assertThat(estadoDelInmueble(e)).isEqualTo(EstadoInmueble.DISPONIBLE);
        mvc.perform(get("/api/negocios/" + e.negocio().getId()).header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$.estado").value("ABIERTO")).andExpect(jsonPath("$.puedeCerrar").value(true));
    }

    @Test
    void unAdministradorPuedeCerrarConDocumentosPendientesSoloConUnaNota() throws Exception {
        Escenario e = escenario("Forzado");

        cerrar(e, e.admin(), "{}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("nota")));
        cerrar(e, e.admin(), "{\"nota\": \"corta\"}").andExpect(status().isUnprocessableEntity());
        cerrar(e, e.admin(), "{\"nota\": \"El notario recibe los documentos el mismo día de la firma.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FIRMADO"))
                .andExpect(jsonPath("$.notaCierre").value(containsString("notario")));
        assertThat(estadoDelInmueble(e)).isEqualTo(EstadoInmueble.VENDIDO);
    }

    @Test
    void otroAsesorNoPuedeCerrarUnNegocioAjenoYUnaPersonaDeOtraInmobiliariaNiLoVe() throws Exception {
        Escenario e = escenario("Ajeno");
        completarDocumentos(e);
        Usuario otroAsesor = crearUsuario(e.inmobiliaria(), Rol.ASESOR);
        Escenario deOtra = escenario("OtraInmobiliaria");

        cerrar(e, otroAsesor, "{}").andExpect(status().isForbidden());
        cerrar(e, deOtra.admin(), "{}").andExpect(status().isNotFound());
        assertThat(estadoDelInmueble(e)).isEqualTo(EstadoInmueble.DISPONIBLE);
    }

    @Test
    void unNegocioNoSeCierraDosVecesNiUnInmuebleYaVendidoSeVuelveAVender() throws Exception {
        Escenario e = escenario("Doble");
        completarDocumentos(e);
        cerrar(e, e.asesor(), "{}").andExpect(status().isOk());

        cerrar(e, e.asesor(), "{}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("ya está cerrado")));

        // Otro negocio del mismo inmueble: ya figura como vendido.
        Propietario otro = crearPropietario(e.inmobiliaria(), e.asesor(), "Otro vendedor", "ov@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        // Se recarga el inmueble: el ayudante lo vuelve a guardar y una copia vieja borraría el estado «vendido».
        Negocio segundo = crearNegocio(e.inmobiliaria(), e.asesor(), inmuebles.findById(e.inmueble().getId()).orElseThrow(), otro, null);
        mvc.perform(post("/api/negocios/" + segundo.getId() + "/cerrar").header("Authorization", bearer(e.admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\": \"Intento de cerrar de nuevo el mismo inmueble.\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("ya figura como vendido")));
    }

    @Test
    void elEstadoFirmadoNoSePoneNiSeQuitaEditandoElNegocio() throws Exception {
        Escenario e = escenario("Edicion");
        String cuerpo = """
                {"inmuebleId": %d, "propietarioId": %d, "estado": "%s"}""";
        Long propietarioId = negocios.findById(e.negocio().getId()).orElseThrow().getPropietarioId();

        mvc.perform(put("/api/negocios/" + e.negocio().getId()).header("Authorization", bearer(e.asesor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted(e.inmueble().getId(), propietarioId, "FIRMADO")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Cerrar negocio")));

        completarDocumentos(e);
        cerrar(e, e.asesor(), "{}").andExpect(status().isOk());
        mvc.perform(put("/api/negocios/" + e.negocio().getId()).header("Authorization", bearer(e.asesor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted(e.inmueble().getId(), propietarioId, "ABIERTO")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("reabre un administrador")));
    }

    @Test
    void cancelarUnNegocioAnotaCuandoYSePuedeAbrirDeNuevo() throws Exception {
        Escenario e = escenario("Cancelar");
        Long propietarioId = negocios.findById(e.negocio().getId()).orElseThrow().getPropietarioId();
        String cuerpo = "{\"inmuebleId\": %d, \"propietarioId\": %d, \"estado\": \"%s\"}";

        mvc.perform(put("/api/negocios/" + e.negocio().getId()).header("Authorization", bearer(e.asesor()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted(e.inmueble().getId(), propietarioId, "CANCELADO")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT cancelado_en IS NOT NULL FROM negocio WHERE id = ?", Boolean.class,
                e.negocio().getId())).isTrue();

        mvc.perform(put("/api/negocios/" + e.negocio().getId()).header("Authorization", bearer(e.asesor()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted(e.inmueble().getId(), propietarioId, "ABIERTO")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT cancelado_en IS NULL FROM negocio WHERE id = ?", Boolean.class,
                e.negocio().getId())).isTrue();
    }

    @Test
    void soloUnAdministradorReabreUnCierreYConUnaNota() throws Exception {
        Escenario e = escenario("Reabrir");
        completarDocumentos(e);
        cerrar(e, e.asesor(), "{}").andExpect(status().isOk());
        String ruta = "/api/negocios/" + e.negocio().getId() + "/reabrir";

        // El asesor no puede deshacer su propio cierre: así nadie ajusta su ranking.
        mvc.perform(post(ruta).header("Authorization", bearer(e.asesor())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\": \"Me equivoqué al cerrarlo.\"}")).andExpect(status().isForbidden());
        mvc.perform(post(ruta).header("Authorization", bearer(e.admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(ruta).header("Authorization", bearer(e.admin())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\": \"corta\"}")).andExpect(status().isUnprocessableEntity());

        mvc.perform(post(ruta).header("Authorization", bearer(e.admin())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\": \"La firma se canceló en la notaría.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.firmadoEn").value(nullValue())).andExpect(jsonPath("$.valorCierre").value(nullValue()));
        assertThat(estadoDelInmueble(e)).isEqualTo(EstadoInmueble.DISPONIBLE);
        // Ya no cuenta como venta: se puede volver a cerrar cuando de verdad se firme.
        cerrar(e, e.asesor(), "{}").andExpect(status().isOk());
    }

    @Test
    void unNegocioAbiertoNoSeReabreNiUnNegocioInexistenteSeCierra() throws Exception {
        Escenario e = escenario("Abierto");

        mvc.perform(post("/api/negocios/" + e.negocio().getId() + "/reabrir").header("Authorization", bearer(e.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nota\": \"No estaba cerrado, es un intento.\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/negocios/999999/cerrar").header("Authorization", bearer(e.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }
}
