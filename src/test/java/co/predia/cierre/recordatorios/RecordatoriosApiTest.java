package co.predia.cierre.recordatorios;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin IA (perfil "test"): la redacción sale de la plantilla. JavaMailSender va mockeado para comprobar lo más
 * importante: no se envía NADA sin la aprobación del asesor.
 */
class RecordatoriosApiTest extends PruebaIntegracion {

    @MockitoBean JavaMailSender correo;
    @Autowired GeneradorDiario generadorDiario;

    private record Caso(Inmobiliaria inmobiliaria, Usuario asesor, Negocio negocio) {
    }

    /** Falta: tradición (vencida), administración y promesa. Ya están: cédula, escritura y predial. */
    private Caso caso(String nombre, String emailPropietario) throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria(nombre);
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);
        Inmueble inmueble = crearInmueble(inmobiliaria, "R-" + nombre.hashCode());
        Propietario propietario = crearPropietario(inmobiliaria, asesor, "Jorge Ramírez", emailPropietario,
                TipoPersona.NATURAL, EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(inmobiliaria, asesor, inmueble, propietario, null);
        // Sin fecha de firma ni margen de alerta, "por vencer" no depende del día en que corra la prueba.
        ParametrosInmobiliaria p = parametros.findById(inmobiliaria.getId()).orElseThrow();
        p.actualizar(p.getPctCuotaMaxIngreso(), p.getPctCuotaInicial(), p.getPctCuotaInicialVis(),
                p.getPlazoDefaultAnios(), 0, p.getCiudadesConValorizacion(), p.getUmbralSimilitudRag(),
                p.getEmailRemitente());
        parametros.save(p);
        recibir(asesor, negocio, "CEDULA_VENDEDOR", null);
        recibir(asesor, negocio, "ESCRITURA_ADQUISICION", null);
        recibir(asesor, negocio, "PREDIAL", hoy());
        recibir(asesor, negocio, "CERTIFICADO_TRADICION", hoy().minusDays(31));
        return new Caso(inmobiliaria, asesor, negocio);
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

    private Long generar(Caso c) throws Exception {
        return idDe(mvc.perform(post("/api/negocios/" + c.negocio().getId() + "/recordatorios")
                        .header("Authorization", bearer(c.asesor())))
                .andExpect(status().isCreated()).andReturn());
    }

    private static final String EDICION = "{\"asunto\": \"Asunto editado\", \"cuerpo\": \"%s\"}";

    @Test
    void elRecordatorioSoloMencionaLoQueDeVerdadFaltaYNoEnviaNadaSinAprobacion() throws Exception {
        Caso c = caso("Recordatorio Contenido", "jorge@ejemplo.test");

        String json = mvc.perform(post("/api/negocios/" + c.negocio().getId() + "/recordatorios")
                        .header("Authorization", bearer(c.asesor())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("SUGERIDO"))
                .andExpect(jsonPath("$.origenTexto").value("PLANTILLA"))
                .andExpect(jsonPath("$.destinatarioEmail").value("jorge@ejemplo.test"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.advertencias", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        List<String> codigos = JsonPath.read(json, "$.items[*].codigo");
        assertThat(codigos).containsExactlyInAnyOrder("CERTIFICADO_TRADICION", "ADMINISTRACION", "PROMESA_COMPRAVENTA");
        String cuerpo = JsonPath.read(json, "$.cuerpo");
        assertThat(cuerpo).contains("Certificado de tradición y libertad", "Paz y salvo de administración",
                "Promesa de compraventa firmada");
        // Nada de lo que ya está entregado, no aplica o es solo recomendado.
        assertThat(cuerpo.toLowerCase()).doesNotContain("predial", "cédula", "hipoteca", "cónyuge",
                "valorización", "servicios públicos", "apoderado", "representación legal");

        // Generarlo no envía nada.
        verifyNoInteractions(correo);
    }

    @Test
    void conElPropietarioAlDiaNoSeGeneraNada() throws Exception {
        Caso c = caso("Recordatorio Al Dia", "jorge@ejemplo.test");
        recibir(c.asesor(), c.negocio(), "CERTIFICADO_TRADICION", hoy().minusDays(1));
        recibir(c.asesor(), c.negocio(), "ADMINISTRACION", hoy());
        recibir(c.asesor(), c.negocio(), "PROMESA_COMPRAVENTA", null);

        mvc.perform(post("/api/negocios/" + c.negocio().getId() + "/recordatorios")
                        .header("Authorization", bearer(c.asesor())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("No hay documentos pendientes")));
    }

    @Test
    void elTrabajoDiarioDejaBorradoresPeroNuncaEnvia() throws Exception {
        Caso c = caso("Recordatorio Diario", "jorge@ejemplo.test");

        int creados = generadorDiario.generarParaTodos(hoy());
        assertThat(creados).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/recordatorios").header("Authorization", bearer(c.asesor())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("SUGERIDO"));

        // Una segunda corrida no duplica el borrador pendiente.
        generadorDiario.generarParaTodos(hoy());
        mvc.perform(get("/api/recordatorios").header("Authorization", bearer(c.asesor())))
                .andExpect(jsonPath("$", hasSize(1)));

        verifyNoInteractions(correo);
    }

    @Test
    void alAprobarSeEnvíaPorCorreoYQuedaEnElHistorialConQuienLoAprobo() throws Exception {
        Caso c = caso("Recordatorio Aprobar", "jorge@ejemplo.test");
        Long id = generar(c);
        verify(correo, never()).send(any(SimpleMailMessage.class));

        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO"))
                .andExpect(jsonPath("$.aprobadoPor").value(c.asesor().getNombre()))
                .andExpect(jsonPath("$.enviadoEn").isNotEmpty());

        ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo, times(1)).send(enviado.capture());
        SimpleMailMessage mensaje = enviado.getValue();
        assertThat(mensaje.getTo()).containsExactly("jorge@ejemplo.test");
        assertThat(mensaje.getReplyTo()).isEqualTo(c.asesor().getEmail());
        assertThat(mensaje.getSubject()).contains("Documentos pendientes");
        assertThat(mensaje.getText()).contains("Certificado de tradición y libertad");

        mvc.perform(get("/api/negocios/" + c.negocio().getId() + "/recordatorios")
                        .header("Authorization", bearer(c.asesor())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].items", hasSize(3)))
                .andExpect(jsonPath("$[0].aprobadoEn").isNotEmpty());

        // Ya enviado: no se puede aprobar otra vez ni mandar duplicado.
        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isUnprocessableEntity());
        verify(correo, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void elAsesorEditaElTextoYSeEnvíaLoEditado() throws Exception {
        Caso c = caso("Recordatorio Editar", "jorge@ejemplo.test");
        Long id = generar(c);
        String texto = "Hola Jorge, aún necesitamos el certificado de tradición y libertad, el paz y salvo de "
                + "administración y la promesa de compraventa. Gracias.";

        mvc.perform(put("/api/recordatorios/" + id).header("Authorization", bearer(c.asesor()))
                        .contentType(MediaType.APPLICATION_JSON).content(EDICION.formatted(texto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asunto").value("Asunto editado"))
                .andExpect(jsonPath("$.advertencias", hasSize(0)));

        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isOk());
        ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo).send(enviado.capture());
        assertThat(enviado.getValue().getText()).isEqualTo(texto);
        assertThat(enviado.getValue().getSubject()).isEqualTo("Asunto editado");

        // Una vez aprobado ya no se edita.
        mvc.perform(put("/api/recordatorios/" + id).header("Authorization", bearer(c.asesor()))
                        .contentType(MediaType.APPLICATION_JSON).content(EDICION.formatted("Otro texto")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void siElAsesorMencionaAlgoDeMasSeLeAvisaPeroNoSeLeBloquea() throws Exception {
        Caso c = caso("Recordatorio Advertencia", "jorge@ejemplo.test");
        Long id = generar(c);
        String texto = "Necesitamos el certificado de tradición y libertad, el paz y salvo de administración, la "
                + "promesa de compraventa y también la carta de la hipoteca antes del 25 de diciembre.";

        mvc.perform(put("/api/recordatorios/" + id).header("Authorization", bearer(c.asesor()))
                        .contentType(MediaType.APPLICATION_JSON).content(EDICION.formatted(texto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advertencias", hasSize(2)))
                .andExpect(jsonPath("$.advertencias[0]", containsString("hipoteca")));

        // La decisión es del asesor: sigue pudiendo aprobarlo.
        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO"));
    }

    @Test
    void unRecordatorioDescartadoNuncaSeEnvia() throws Exception {
        Caso c = caso("Recordatorio Descartar", "jorge@ejemplo.test");
        Long id = generar(c);

        mvc.perform(post("/api/recordatorios/" + id + "/descartar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DESCARTADO"));
        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(correo);
    }

    @Test
    void sinCorreoDelPropietarioNoSePuedeAprobarNiSeEnviaNada() throws Exception {
        Caso c = caso("Recordatorio Sin Correo", null);
        Long id = generar(c);

        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("correo")));

        verifyNoInteractions(correo);
    }

    @Test
    void siElCorreoFallaQuedaConErrorYSePuedeReintentar() throws Exception {
        Caso c = caso("Recordatorio Falla", "jorge@ejemplo.test");
        Long id = generar(c);
        doThrow(new MailSendException("Simulado: servidor de correo caído")).when(correo)
                .send(any(SimpleMailMessage.class));

        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ERROR_ENVIO"))
                .andExpect(jsonPath("$.error", containsString("No se pudo enviar")));

        doNothing().when(correo).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(c.asesor())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ENVIADO"));
    }

    @Test
    void unAsesorDeOtraInmobiliariaNoPuedeVerAprobarNiEnviarLosRecordatoriosAjenos() throws Exception {
        Caso c = caso("Recordatorio Aislado", "jorge@ejemplo.test");
        Long id = generar(c);
        Usuario ajeno = crearUsuario(crearInmobiliaria("Recordatorio Aislado Otra"), Rol.ASESOR);

        mvc.perform(get("/api/recordatorios").header("Authorization", bearer(ajeno)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/recordatorios/" + id + "/aprobar").header("Authorization", bearer(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/recordatorios/" + id + "/descartar").header("Authorization", bearer(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/recordatorios/" + id).header("Authorization", bearer(ajeno))
                        .contentType(MediaType.APPLICATION_JSON).content(EDICION.formatted("Hola")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(correo);
    }
}
