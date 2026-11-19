package co.predia.cierre;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.HipotecaVigente;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioService;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.inventario.Operacion;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cierre del negocio: el estado de cada requisito sale de las reglas en Java, con la fecha de hoy. */
class CierreApiTest extends PruebaIntegracion {

    private record Escenario(Inmobiliaria inmobiliaria, Usuario asesor, Negocio negocio) {
    }

    private Escenario escenario(String nombre, LocalDate fechaFirma) {
        Inmobiliaria inmobiliaria = crearInmobiliaria(nombre);
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "C-" + nombre.hashCode());
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Jorge Ramírez", "jorge@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        return new Escenario(inmobiliaria, asesor, crearNegocio(inmobiliaria, asesor, inmueble, propietario, fechaFirma));
    }

    private String estado(Escenario e) throws Exception {
        return mvc.perform(get("/api/negocios/" + e.negocio().getId() + "/estado-documental")
                        .header("Authorization", bearer(e.asesor())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Long idRequisito(String estadoJson, String codigo) {
        List<Number> ids = JsonPath.read(estadoJson, "$.items[?(@.codigo=='" + codigo + "')].id");
        return ids.get(0).longValue();
    }

    private String accion(Escenario e, String codigo, String cuerpo) throws Exception {
        Long id = idRequisito(estado(e), codigo);
        return mvc.perform(put("/api/negocios/" + e.negocio().getId() + "/requisitos/" + id)
                        .header("Authorization", bearer(e.asesor())).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String recibido(LocalDate fecha) {
        return fecha == null ? "{\"accion\": \"RECIBIR\"}"
                : "{\"accion\": \"RECIBIR\", \"fechaExpedicion\": \"%s\"}".formatted(fecha);
    }

    private void sinMargenDeAlerta(Inmobiliaria inmobiliaria) {
        ParametrosInmobiliaria p = parametros.findById(inmobiliaria.getId()).orElseThrow();
        p.actualizar(p.getPctCuotaMaxIngreso(), p.getPctCuotaInicial(), p.getPctCuotaInicialVis(),
                p.getPlazoDefaultAnios(), 0, p.getCiudadesConValorizacion(), p.getUmbralSimilitudRag(),
                p.getEmailRemitente());
        parametros.save(p);
    }

    @Test
    void unNegocioNuevoParteConTodoPendienteYSoloExigeLoQueAplica() throws Exception {
        Escenario e = escenario("Cierre Inicial", hoy().plusDays(10));

        mvc.perform(get("/api/negocios/" + e.negocio().getId() + "/estado-documental")
                        .header("Authorization", bearer(e.asesor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listoParaFirmar").value(false))
                .andExpect(jsonPath("$.semaforo").value("Faltan 6 documentos"))
                .andExpect(jsonPath("$.porcentajeAvance").value(0))
                .andExpect(jsonPath("$.aviso").value("Lista de referencia; confirma los requisitos con la notaría."))
                .andExpect(jsonPath("$.items", hasSize(12)))
                // Aplican (y son obligatorios): cédula, tradición, escritura, predial, administración y promesa.
                .andExpect(jsonPath("$.items[?(@.codigo=='ADMINISTRACION')].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.items[?(@.codigo=='CONYUGE')].estado").value("NO_APLICA"))
                .andExpect(jsonPath("$.items[?(@.codigo=='PODER_ESPECIAL')].estado").value("NO_APLICA"))
                .andExpect(jsonPath("$.items[?(@.codigo=='EXISTENCIA_REPRESENTACION')].estado").value("NO_APLICA"))
                .andExpect(jsonPath("$.items[?(@.codigo=='LEVANTAMIENTO_HIPOTECA')].estado").value("NO_APLICA"))
                .andExpect(jsonPath("$.items[?(@.codigo=='VALORIZACION')].estado").value("NO_APLICA"))
                // El recomendado se muestra pero no bloquea.
                .andExpect(jsonPath("$.items[?(@.codigo=='SERVICIOS_PUBLICOS')].obligatorio").value(false));
    }

    @Test
    void elCertificadoDeTradicionExpedidoHace31DiasEstaVencidoYElDeHace25DiasConFirmaEn10EstaPorVencer()
            throws Exception {
        Escenario e = escenario("Cierre Vigencias", hoy().plusDays(10));

        String vencido = accion(e, "CERTIFICADO_TRADICION", recibido(hoy().minusDays(31)));
        assertThat(JsonPath.<List<String>>read(vencido, "$.items[?(@.codigo=='CERTIFICADO_TRADICION')].estado"))
                .containsExactly("VENCIDO");

        String porVencer = accion(e, "CERTIFICADO_TRADICION", recibido(hoy().minusDays(25)));
        assertThat(JsonPath.<List<String>>read(porVencer, "$.items[?(@.codigo=='CERTIFICADO_TRADICION')].estado"))
                .containsExactly("POR_VENCER");
        assertThat(JsonPath.<List<String>>read(porVencer, "$.items[?(@.codigo=='CERTIFICADO_TRADICION')].venceEl"))
                .containsExactly(hoy().plusDays(5).toString());
    }

    @Test
    void recibirUnDocumentoConVigenciaExigeLaFechaYNoAceptaFechasFuturas() throws Exception {
        Escenario e = escenario("Cierre Fechas", null);
        Long id = idRequisito(estado(e), "CERTIFICADO_TRADICION");
        String url = "/api/negocios/" + e.negocio().getId() + "/requisitos/" + id;

        mvc.perform(put(url).header("Authorization", bearer(e.asesor())).contentType(MediaType.APPLICATION_JSON)
                        .content(recibido(null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("fecha de expedición")));
        mvc.perform(put(url).header("Authorization", bearer(e.asesor())).contentType(MediaType.APPLICATION_JSON)
                        .content(recibido(hoy().plusDays(1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("futura")));

        // Un documento sin vigencia (la cédula) no necesita fecha.
        String json = accion(e, "CEDULA_VENDEDOR", recibido(null));
        assertThat(JsonPath.<List<String>>read(json, "$.items[?(@.codigo=='CEDULA_VENDEDOR')].estado"))
                .containsExactly("RECIBIDO");
    }

    @Test
    void llegaAListoParaFirmarSoloConTodoLoObligatorioRecibidoYVigente() throws Exception {
        Escenario e = escenario("Cierre Listo", null);
        sinMargenDeAlerta(e.inmobiliaria());

        accion(e, "CEDULA_VENDEDOR", recibido(null));
        accion(e, "CERTIFICADO_TRADICION", recibido(hoy().minusDays(2)));
        accion(e, "ESCRITURA_ADQUISICION", recibido(null));
        accion(e, "PREDIAL", recibido(hoy()));
        accion(e, "ADMINISTRACION", recibido(hoy()));
        String antes = estado(e);
        assertThat(JsonPath.<String>read(antes, "$.semaforo")).isEqualTo("Falta 1 documento");
        assertThat(JsonPath.<Integer>read(antes, "$.porcentajeAvance")).isEqualTo(83);

        accion(e, "PROMESA_COMPRAVENTA", recibido(null));

        mvc.perform(get("/api/negocios/" + e.negocio().getId() + "/estado-documental")
                        .header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$.listoParaFirmar").value(true))
                .andExpect(jsonPath("$.semaforo").value("Listo para firmar"))
                .andExpect(jsonPath("$.porcentajeAvance").value(100));
    }

    @Test
    void elAsesorPuedeMarcarUnRequisitoComoNoAplicaYReabrirlo() throws Exception {
        Escenario e = escenario("Cierre NoAplica", null);

        String noAplica = accion(e, "ADMINISTRACION", "{\"accion\": \"NO_APLICA\", \"nota\": \"Es casa independiente\"}");
        assertThat(JsonPath.<List<String>>read(noAplica, "$.items[?(@.codigo=='ADMINISTRACION')].estado"))
                .containsExactly("NO_APLICA");
        assertThat(JsonPath.<String>read(noAplica, "$.semaforo")).isEqualTo("Faltan 5 documentos");

        String reabierto = accion(e, "ADMINISTRACION", "{\"accion\": \"REABRIR\"}");
        assertThat(JsonPath.<List<String>>read(reabierto, "$.items[?(@.codigo=='ADMINISTRACION')].estado"))
                .containsExactly("PENDIENTE");
    }

    @Test
    void pazYSalvoDeAdministracionNoAplicaSiElInmuebleNoEsPropiedadHorizontal() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Cierre Casa");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble casa = crearInmueble(inmobiliaria, "CASA-1");   // propiedad horizontal: falso por defecto
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Marta Pardo", "marta@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = negocioService.crear(new UsuarioActual(asesor.getId(), inmobiliaria.getId(),
                        asesor.getNombre(), asesor.getEmail(), Rol.ASESOR),
                new NegocioService.Datos(casa.getId(), propietario.getId(), null, null, false,
                        HipotecaVigente.NO, null));

        mvc.perform(get("/api/negocios/" + negocio.getId() + "/estado-documental")
                        .header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.items[?(@.codigo=='ADMINISTRACION')].estado").value("NO_APLICA"))
                .andExpect(jsonPath("$.semaforo").value("Faltan 5 documentos"));
    }

    @Test
    void lasReglasDeAplicacionSiguenAlVendedor() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Cierre Juridica");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "JUR-1");
        Propietario empresa = crearPropietario(inmobiliaria, asesor, "Constructora Altos S.A.S.", "empresa@ejemplo.test",
                TipoPersona.JURIDICA, EstadoCivil.CASADO);   // el estado civil se ignora en una persona jurídica
        inmueble.setPropiedadHorizontal(true);
        inmuebles.save(inmueble);
        Negocio negocio = negocioService.crear(new UsuarioActual(asesor.getId(), inmobiliaria.getId(),
                        asesor.getNombre(), asesor.getEmail(), Rol.ASESOR),
                new NegocioService.Datos(inmueble.getId(), empresa.getId(), null, null, true, HipotecaVigente.SI,
                        null));

        mvc.perform(get("/api/negocios/" + negocio.getId() + "/estado-documental")
                        .header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$.items[?(@.codigo=='EXISTENCIA_REPRESENTACION')].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.items[?(@.codigo=='PODER_ESPECIAL')].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.items[?(@.codigo=='LEVANTAMIENTO_HIPOTECA')].estado").value("PENDIENTE"))
                // Una persona jurídica no tiene cónyuge.
                .andExpect(jsonPath("$.items[?(@.codigo=='CONYUGE')].estado").value("NO_APLICA"));
    }

    @Test
    void elAdminAjustaElCatalogoYElAsesorNoPuede() throws Exception {
        Escenario e = escenario("Cierre Catalogo", null);
        Usuario admin = crearUsuario(e.inmobiliaria(), Rol.ADMIN);

        String catalogo = mvc.perform(get("/api/requisitos").header("Authorization", bearer(e.asesor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].aviso").value("Lista de referencia; confirma los requisitos con la notaría."))
                .andReturn().getResponse().getContentAsString();
        Long idPromesa = ((Number) ((List<?>) JsonPath.read(catalogo, "$[?(@.codigo=='PROMESA_COMPRAVENTA')].id")).get(0))
                .longValue();
        String desactivar = """
                {"nombre": "Promesa de compraventa firmada", "descripcion": "", "orden": 7, "activo": false,
                 "reglaAplica": "SIEMPRE", "obligatorio": true, "reglaVigencia": "NINGUNA"}""";

        mvc.perform(put("/api/requisitos/" + idPromesa).header("Authorization", bearer(e.asesor()))
                        .contentType(MediaType.APPLICATION_JSON).content(desactivar))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/requisitos/" + idPromesa).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(desactivar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // La promesa ya no se exige en este negocio: quedan 11 requisitos y 5 por entregar.
        mvc.perform(get("/api/negocios/" + e.negocio().getId() + "/estado-documental")
                        .header("Authorization", bearer(e.asesor())))
                .andExpect(jsonPath("$.items", hasSize(11)))
                .andExpect(jsonPath("$.semaforo").value("Faltan 5 documentos"));
    }

    @Test
    void unUsuarioDeOtraInmobiliariaNoVeNiTocaLosNegociosAjenos() throws Exception {
        Escenario propio = escenario("Cierre Aislado A", null);
        Inmobiliaria otra = crearInmobiliaria("Cierre Aislado B");
        Usuario asesorAjeno = crearUsuario(otra, Rol.ASESOR);
        Long id = propio.negocio().getId();
        Long requisito = idRequisito(estado(propio), "CEDULA_VENDEDOR");

        mvc.perform(get("/api/negocios").header("Authorization", bearer(asesorAjeno)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/negocios/" + id).header("Authorization", bearer(asesorAjeno)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/negocios/" + id + "/estado-documental").header("Authorization", bearer(asesorAjeno)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/negocios/" + id + "/requisitos/" + requisito)
                        .header("Authorization", bearer(asesorAjeno)).contentType(MediaType.APPLICATION_JSON)
                        .content(recibido(null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/negocios/" + id + "/recordatorios").header("Authorization", bearer(asesorAjeno)))
                .andExpect(status().isNotFound());

        // Ni subir un documento a un negocio ajeno, ni armar un negocio con inmueble o propietario ajenos.
        mvc.perform(multipart("/api/documentos")
                        .file(new MockMultipartFile("archivo", "x.pdf", "application/pdf", pdfConTexto("Texto.")))
                        .param("inmuebleId", crearInmueble(otra, "AJE-1").getId().toString())
                        .param("negocioId", id.toString()).header("Authorization", bearer(asesorAjeno)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/negocios").header("Authorization", bearer(asesorAjeno))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inmuebleId": %d, "propietarioId": %d}""".formatted(propio.negocio().getInmuebleId(),
                                propio.negocio().getPropietarioId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void elPropietarioSoloSeGuardaConLaAutorizacionDeDatos() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Cierre Propietarios");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        String base = """
                {"tipoPersona": "NATURAL", "nombre": "Ana Ruiz", "tipoDocumento": "CC", "numeroDocumento": "123",
                 "email": "ana@ejemplo.test", "estadoCivil": "CASADO" %s}""";

        mvc.perform(post("/api/propietarios").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(base.formatted("")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("Ley 1581")));
        mvc.perform(post("/api/propietarios").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base.formatted(", \"autorizacionDatos\": true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorizacionFecha").isNotEmpty());
    }

    @Test
    void elCierreSoloAplicaAInmueblesEnVenta() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Cierre Arriendo");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble arriendo = crearInmueble(inmobiliaria, "ARR-1");
        arriendo.setOperacion(Operacion.ARRIENDO);
        arriendo.setPrecioVenta(null);
        arriendo.setCanonArriendo(new BigDecimal("1500000"));
        inmuebles.save(arriendo);
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Luis Mora", "luis@ejemplo.test",
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);

        mvc.perform(post("/api/negocios").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inmuebleId": %d, "propietarioId": %d}""".formatted(arriendo.getId(),
                                propietario.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("en venta")));
    }
}
