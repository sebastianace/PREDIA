package co.predia.inventario;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alta, edición y búsqueda de inmuebles SIN IA (perfil "test"): SQL filtra lo no negociable, orden por precio. */
class InventarioApiTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;

    private static String inmuebleJson(String codigo, String operacion, String precio, String canon, String estado) {
        return """
                {"codigo": "%s", "titulo": "Apto %s", "tipo": "APARTAMENTO", "direccion": "Calle 1 # 2-3",
                 "barrio": "Cabecera", "ciudad": "Bucaramanga", "estrato": 4, "areaM2": 70, "habitaciones": 3,
                 "banos": 2, "parqueaderos": 1, "aceptaMascotas": true, "valorAdministracion": 200000,
                 "operacion": "%s", "precioVenta": %s, "canonArriendo": %s, "esVis": false,
                 "propiedadHorizontal": true, "descripcion": "Apartamento luminoso", "estado": "%s"}"""
                .formatted(codigo, codigo, operacion, precio, canon, estado);
    }

    private Inmueble crear(Inmobiliaria inmobiliaria, String codigo, int habitaciones, String precio,
                           EstadoInmueble estado) {
        Inmueble i = crearInmueble(inmobiliaria, codigo);
        i.setHabitaciones((short) habitaciones);
        i.setPrecioVenta(new BigDecimal(precio));
        i.setEstado(estado);
        return inmuebles.save(i);
    }

    @Test
    void creaUnInmuebleYNoPermiteRepetirElCodigoNiOlvidarElPrecio() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Inventario");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/inmuebles").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inmuebleJson("NUEVO-1", "VENTA", "250000000", "null", "DISPONIBLE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("NUEVO-1"))
                .andExpect(jsonPath("$.precioVenta").value(250000000));

        mvc.perform(post("/api/inmuebles").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inmuebleJson("NUEVO-1", "VENTA", "250000000", "null", "DISPONIBLE")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("código")));

        mvc.perform(post("/api/inmuebles").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inmuebleJson("NUEVO-2", "VENTA", "null", "null", "DISPONIBLE")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("precio de venta")));

        mvc.perform(post("/api/inmuebles").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inmuebleJson("NUEVO-3", "ARRIENDO", "null", "null", "DISPONIBLE")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("canon")));
    }

    @Test
    void elMismoCodigoPuedeExistirEnOtraInmobiliaria() throws Exception {
        Usuario asesorA = crearUsuario(crearInmobiliaria("Inventario Codigo A"), Rol.ASESOR);
        Usuario asesorB = crearUsuario(crearInmobiliaria("Inventario Codigo B"), Rol.ASESOR);
        String cuerpo = inmuebleJson("APT-COMUN", "VENTA", "250000000", "null", "DISPONIBLE");

        mvc.perform(post("/api/inmuebles").header("Authorization", bearer(asesorA))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
        mvc.perform(post("/api/inmuebles").header("Authorization", bearer(asesorB))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
    }

    @Test
    void editaUnInmuebleYCambiaSuEstadoPeroNoElDeOtraInmobiliaria() throws Exception {
        Inmobiliaria propia = crearInmobiliaria("Inventario Edicion A");
        Inmobiliaria ajena = crearInmobiliaria("Inventario Edicion B");
        Usuario asesor = crearUsuario(propia, Rol.ASESOR);
        Inmueble inmueble = crear(propia, "EDIT-1", 3, "250000000", EstadoInmueble.DISPONIBLE);
        Inmueble ajeno = crear(ajena, "EDIT-2", 3, "250000000", EstadoInmueble.DISPONIBLE);

        mvc.perform(put("/api/inmuebles/" + inmueble.getId()).header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inmuebleJson("EDIT-1", "VENTA", "240000000", "null", "SEPARADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SEPARADO"))
                .andExpect(jsonPath("$.precioVenta").value(240000000));

        mvc.perform(put("/api/inmuebles/" + ajeno.getId()).header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inmuebleJson("EDIT-2", "VENTA", "1", "null", "VENDIDO")))
                .andExpect(status().isNotFound());
    }

    @Test
    void laBusquedaNuncaOfreceUnInmuebleQueNoEsteDisponibleNiDeOtraInmobiliaria() throws Exception {
        Inmobiliaria propia = crearInmobiliaria("Inventario Busqueda A");
        Inmobiliaria ajena = crearInmobiliaria("Inventario Busqueda B");
        Usuario asesor = crearUsuario(propia, Rol.ASESOR);
        crear(propia, "DISP-1", 3, "250000000", EstadoInmueble.DISPONIBLE);
        crear(propia, "VEND-1", 3, "100000000", EstadoInmueble.VENDIDO);
        crear(propia, "SEPA-1", 3, "110000000", EstadoInmueble.SEPARADO);
        crear(propia, "ARRE-1", 3, "120000000", EstadoInmueble.ARRENDADO);
        crear(ajena, "AJEN-1", 3, "90000000", EstadoInmueble.DISPONIBLE);

        mvc.perform(post("/api/inmuebles/busqueda").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operacion": "VENTA", "precioMax": 400000000}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados", hasSize(1)))
                .andExpect(jsonPath("$.resultados[0].inmueble.codigo").value("DISP-1"));
    }

    @Test
    void filtraPorPresupuestoYHabitacionesYOrdenaPorPrecioCuandoNoHayIa() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inventario Filtros");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        crear(inmobiliaria, "F-CARO", 3, "300000000", EstadoInmueble.DISPONIBLE);
        crear(inmobiliaria, "F-MEDIO", 3, "250000000", EstadoInmueble.DISPONIBLE);
        crear(inmobiliaria, "F-BARATO", 2, "150000000", EstadoInmueble.DISPONIBLE);

        // Presupuesto de 300 M y mínimo 3 habitaciones: se descarta el de 2 habitaciones.
        mvc.perform(post("/api/inmuebles/busqueda").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operacion": "VENTA", "precioMax": 300000000, "habitacionesMin": 3,
                                 "descripcion": "cerca de colegios"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados", hasSize(2)))
                // Sin IA: del más económico al más costoso, sin porcentaje de coincidencia.
                .andExpect(jsonPath("$.resultados[0].inmueble.codigo").value("F-MEDIO"))
                .andExpect(jsonPath("$.resultados[1].inmueble.codigo").value("F-CARO"))
                .andExpect(jsonPath("$.resultados[0].coincidenciaPct").value(nullValue()))
                .andExpect(jsonPath("$.ordenadoPorSimilitud").value(false))
                .andExpect(jsonPath("$.aviso", containsString("IA")));

        // Un presupuesto menor excluye lo que lo supera.
        mvc.perform(post("/api/inmuebles/busqueda").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operacion": "VENTA", "precioMax": 200000000}"""))
                .andExpect(jsonPath("$.resultados", hasSize(1)))
                .andExpect(jsonPath("$.resultados[0].inmueble.codigo").value("F-BARATO"));
    }

    @Test
    void sinIaUnaEdicionBorraElVectorViejoParaNoDejarUnoDesactualizado() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inventario Vector");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crear(inmobiliaria, "VEC-1", 3, "250000000", EstadoInmueble.DISPONIBLE);
        float[] v = new float[1536];
        v[0] = 1f;
        jdbc.update("UPDATE inmueble SET embedding = ?::vector WHERE id = ?", InmuebleEmbeddings.aTextoVector(v),
                inmueble.getId());

        mvc.perform(put("/api/inmuebles/" + inmueble.getId()).header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inmuebleJson("VEC-1", "VENTA", "250000000", "null", "DISPONIBLE")))
                .andExpect(status().isOk());

        Integer conVector = jdbc.queryForObject("SELECT count(*) FROM inmueble WHERE id = ? AND embedding IS NOT NULL",
                Integer.class, inmueble.getId());
        assertThat(conVector).isZero();
    }

    @Test
    void sinPermisoDeAutenticacionNoSeBusca() throws Exception {
        MvcResult r = mvc.perform(post("/api/inmuebles/busqueda").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }
}
