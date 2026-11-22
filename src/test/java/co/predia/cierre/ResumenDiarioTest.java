package co.predia.cierre;

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
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumenDiarioTest extends PruebaIntegracion {

    private Negocio negocio(Inmobiliaria inmobiliaria, Usuario asesor, String codigo, String titulo) {
        Inmueble inmueble = crearInmueble(inmobiliaria, codigo);
        inmueble.setTitulo(titulo);
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Vendedor " + titulo, titulo + "@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        return crearNegocio(inmobiliaria, asesor, inmueble, propietario, null);
    }

    private void recibir(Usuario asesor, Negocio negocio, String codigo, LocalDate fecha) throws Exception {
        String estado = mvc.perform(get("/api/negocios/" + negocio.getId() + "/estado-documental")
                .header("Authorization", bearer(asesor))).andReturn().getResponse().getContentAsString();
        Number id = ((List<Number>) JsonPath.read(estado, "$.items[?(@.codigo=='" + codigo + "')].id")).get(0);
        mvc.perform(put("/api/negocios/" + negocio.getId() + "/requisitos/" + id)
                        .header("Authorization", bearer(asesor)).contentType(MediaType.APPLICATION_JSON)
                        .content(fecha == null ? "{\"accion\": \"RECIBIR\"}"
                                : "{\"accion\": \"RECIBIR\", \"fechaExpedicion\": \"%s\"}".formatted(fecha)))
                .andExpect(status().isOk());
    }

    @Test
    void muestraLosNegociosBloqueadosYLosDocumentosQueVencenEstaSemana() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Resumen Principal");
        Usuario laura = crearUsuario(inmobiliaria, Rol.ASESOR);
        Negocio n = negocio(inmobiliaria, laura, "RES-1", "Apto Resumen Uno");
        // Expedido hace 27 días con 30 de vigencia: vence en 3 días.
        recibir(laura, n, "CERTIFICADO_TRADICION", hoy().minusDays(27));

        mvc.perform(get("/api/resumen-diario").header("Authorization", bearer(laura)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.negociosAbiertos").value(1))
                .andExpect(jsonPath("$.bloqueados", hasSize(1)))
                .andExpect(jsonPath("$.bloqueados[0].inmueble").value("Apto Resumen Uno"))
                .andExpect(jsonPath("$.bloqueados[0].faltan").value(6))
                .andExpect(jsonPath("$.bloqueados[0].porVencer").value(1))
                .andExpect(jsonPath("$.vencenEstaSemana", hasSize(1)))
                .andExpect(jsonPath("$.vencenEstaSemana[0].documento").value("Certificado de tradición y libertad"))
                .andExpect(jsonPath("$.vencenEstaSemana[0].diasRestantes").value(3))
                .andExpect(jsonPath("$.vencenEstaSemana[0].venceEl").value(hoy().plusDays(3).toString()));
    }

    @Test
    void cadaAsesorVeSusNegociosYElAdminVeTodosPeroNuncaLosDeOtraInmobiliaria() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Resumen Alcance");
        Usuario laura = crearUsuario(inmobiliaria, Rol.ASESOR);
        Usuario pedro = crearUsuario(inmobiliaria, Rol.ASESOR);
        Usuario admin = crearUsuario(inmobiliaria, Rol.ADMIN);
        negocio(inmobiliaria, laura, "ALC-1", "Apto de Laura");
        negocio(inmobiliaria, pedro, "ALC-2", "Apto de Pedro");
        Usuario ajeno = crearUsuario(crearInmobiliaria("Resumen Alcance Otra"), Rol.ADMIN);

        mvc.perform(get("/api/resumen-diario").header("Authorization", bearer(laura)))
                .andExpect(jsonPath("$.bloqueados", hasSize(1)))
                .andExpect(jsonPath("$.bloqueados[0].inmueble").value("Apto de Laura"));
        mvc.perform(get("/api/resumen-diario").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.bloqueados", hasSize(2)));
        mvc.perform(get("/api/resumen-diario").header("Authorization", bearer(ajeno)))
                .andExpect(jsonPath("$.negociosAbiertos").value(0))
                .andExpect(jsonPath("$.bloqueados", hasSize(0)));
    }

    @Test
    void unNegocioListoParaFirmarNoApareceComoBloqueado() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Resumen Listo");
        Usuario laura = crearUsuario(inmobiliaria, Rol.ASESOR);
        Negocio n = negocio(inmobiliaria, laura, "LIS-1", "Apto Listo");
        var p = parametros.findById(inmobiliaria.getId()).orElseThrow();
        p.actualizar(p.getPctCuotaMaxIngreso(), p.getPctCuotaInicial(), p.getPctCuotaInicialVis(),
                p.getPlazoDefaultAnios(), 0, p.getCiudadesConValorizacion(), p.getUmbralSimilitudRag(),
                p.getEmailRemitente());
        parametros.save(p);
        recibir(laura, n, "CEDULA_VENDEDOR", null);
        recibir(laura, n, "CERTIFICADO_TRADICION", hoy().minusDays(2));
        recibir(laura, n, "ESCRITURA_ADQUISICION", null);
        recibir(laura, n, "PREDIAL", hoy());
        recibir(laura, n, "ADMINISTRACION", hoy());
        recibir(laura, n, "PROMESA_COMPRAVENTA", null);

        mvc.perform(get("/api/resumen-diario").header("Authorization", bearer(laura)))
                .andExpect(jsonPath("$.negociosAbiertos").value(1))
                .andExpect(jsonPath("$.bloqueados", hasSize(0)));
    }
}
