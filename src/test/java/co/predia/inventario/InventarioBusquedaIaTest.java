package co.predia.inventario;

import co.predia.comun.ia.EstadoIa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con IA "disponible" y EmbeddingModel MOCKEADO (nunca llama a OpenAI): cualquier texto que hable de "perros"
 * se vectoriza en una dirección y todo lo demás en otra, así la similitud es predecible.
 */
class InventarioBusquedaIaTest extends PruebaIntegracion {

    @Autowired JdbcTemplate jdbc;
    @MockitoBean EstadoIa estadoIa;
    @MockitoBean EmbeddingModel embeddingModel;

    private static float[] vector(int dimension) {
        float[] v = new float[1536];
        v[dimension] = 1f;
        return v;
    }

    private void simularIa() {
        when(estadoIa.disponible()).thenReturn(true);
        when(estadoIa.modeloEmbeddings()).thenReturn("embeddings-de-pruebas");
        when(embeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<String> textos = inv.getArgument(0);
            return textos.stream().map(t -> t.toLowerCase().contains("perros") ? vector(0) : vector(1)).toList();
        });
    }

    private Inmueble crear(Inmobiliaria inmobiliaria, String codigo, String precio, String descripcion) {
        Inmueble i = crearInmueble(inmobiliaria, codigo);
        i.setPrecioVenta(new BigDecimal(precio));
        i.setDescripcion(descripcion);
        return inmuebles.save(i);
    }

    @Test
    void ordenaPorSimilitudConLaDescripcionYMuestraElPorcentajeDeCoincidencia() throws Exception {
        simularIa();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inventario IA");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        // El más barato NO es el que mejor encaja: el orden tiene que venir de la similitud, no del precio.
        crear(inmobiliaria, "IA-MINIMALISTA", "180000000", "Apartamento minimalista y moderno.");
        crear(inmobiliaria, "IA-PERROS", "260000000", "Casa con patio amplio, ideal para perros grandes.");

        mvc.perform(post("/api/inmuebles/busqueda").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operacion": "VENTA", "precioMax": 300000000,
                                 "descripcion": "Quiero un lugar con espacio para mis perros"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenadoPorSimilitud").value(true))
                .andExpect(jsonPath("$.resultados", hasSize(2)))
                .andExpect(jsonPath("$.resultados[0].inmueble.codigo").value("IA-PERROS"))
                .andExpect(jsonPath("$.resultados[0].coincidenciaPct").value(100))
                .andExpect(jsonPath("$.resultados[1].inmueble.codigo").value("IA-MINIMALISTA"))
                .andExpect(jsonPath("$.resultados[1].coincidenciaPct").value(0));
    }

    @Test
    void conIaElSqlSigueFiltrandoLoNoNegociableAunqueEncajeMejor() throws Exception {
        simularIa();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inventario IA Filtro");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble vendido = crear(inmobiliaria, "IA-VENDIDO", "200000000", "La casa perfecta para perros.");
        vendido.setEstado(EstadoInmueble.VENDIDO);
        inmuebles.save(vendido);
        crear(inmobiliaria, "IA-CARO", "900000000", "Casa para perros con piscina.");
        crear(inmobiliaria, "IA-OK", "250000000", "Apartamento sencillo.");

        mvc.perform(post("/api/inmuebles/busqueda").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operacion": "VENTA", "precioMax": 300000000, "descripcion": "espacio para perros"}"""))
                .andExpect(jsonPath("$.resultados", hasSize(1)))
                .andExpect(jsonPath("$.resultados[0].inmueble.codigo").value("IA-OK"));
    }

    @Test
    void alCrearUnInmuebleConIaQuedaVectorizadoYLaBusquedaCompletaLosQueFaltan() throws Exception {
        simularIa();
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inventario IA Vectores");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/inmuebles").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "VEC-API", "titulo": "Casa para perros", "tipo": "CASA",
                                 "direccion": "Calle 1", "barrio": "Lagos", "ciudad": "Floridablanca",
                                 "areaM2": 100, "habitaciones": 3, "banos": 2, "parqueaderos": 1,
                                 "aceptaMascotas": true, "operacion": "VENTA", "precioVenta": 280000000,
                                 "descripcion": "Patio para perros.", "estado": "DISPONIBLE"}"""))
                .andExpect(status().isCreated());
        Integer conVector = jdbc.queryForObject(
                "SELECT count(*) FROM inmueble WHERE inmobiliaria_id = ? AND embedding IS NOT NULL", Integer.class,
                inmobiliaria.getId());
        assertThat(conVector).isEqualTo(1);

        // Un inmueble sembrado sin vector se completa al buscar.
        crear(inmobiliaria, "SIN-VECTOR", "200000000", "Apartamento sencillo.");
        mvc.perform(post("/api/inmuebles/busqueda").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operacion": "VENTA", "descripcion": "perros"}"""))
                .andExpect(jsonPath("$.resultados", hasSize(2)));
        Integer sinVector = jdbc.queryForObject(
                "SELECT count(*) FROM inmueble WHERE inmobiliaria_id = ? AND embedding IS NULL", Integer.class,
                inmobiliaria.getId());
        assertThat(sinVector).isZero();
    }
}
