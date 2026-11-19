package co.predia.clientes;

import co.predia.comun.ia.EstadoIa;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El perfil que propone la IA es solo una sugerencia, se valida en Java y no se guarda. ChatModel va mockeado. */
class ExtraccionPerfilTest extends PruebaIntegracion {

    @MockitoBean EstadoIa estadoIa;
    @MockitoBean ChatModel chatModel;

    private static final String TEXTO = "{\"texto\": \"Paola y Juan ganan 7,5 millones entre los dos y tienen 90 millones "
            + "ahorrados. Quieren comprar en Floridablanca un apartamento de 3 habitaciones con parqueadero.\"}";

    @Test
    void devuelveElPerfilSugeridoYDescartaLoQueNoTieneSentido() throws Exception {
        when(estadoIa.disponible()).thenReturn(true);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("""
                        {"nombre": "Paola y Juan", "ingresoFamiliar": 7500000, "ahorro": -5,
                         "deudasMensuales": null, "intencion": "COMPRAR", "habitacionesMin": 3,
                         "zonas": ["Floridablanca", "  "], "preferencias": "Parqueadero y mascotas",
                         "email": null, "telefono": null}""")))));

        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Extraccion");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/clientes/extraer-perfil").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(TEXTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerido").value(true))
                .andExpect(jsonPath("$.perfil.ingresoFamiliar").value(7500000))
                .andExpect(jsonPath("$.perfil.intencion").value("COMPRAR"))
                .andExpect(jsonPath("$.perfil.zonas", hasSize(1)))
                // Un ahorro negativo no tiene sentido: se descarta, no se confía ciegamente en el modelo.
                .andExpect(jsonPath("$.perfil.ahorro").doesNotExist());

        // Solo es una sugerencia: no se guardó ningún cliente.
        mvc.perform(get("/api/clientes").header("Authorization", bearer(asesor)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sinIaPideCompletarElFormularioSinLlamarAlModelo() throws Exception {
        when(estadoIa.disponible()).thenReturn(false);
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Extraccion Manual");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/clientes/extraer-perfil").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(TEXTO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("formulario")));

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void siElModeloFallaPideCompletarElFormulario() throws Exception {
        when(estadoIa.disponible()).thenReturn(true);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Simulado: sin respuesta"));
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria Extraccion Falla");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/clientes/extraer-perfil").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content(TEXTO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("formulario")));
    }

    @Test
    void unJsonMalFormadoEsUnErrorDelClienteNoDelServidor() throws Exception {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria JSON");
        Usuario asesor = crearUsuario(inmobiliaria, Rol.ASESOR);

        mvc.perform(post("/api/clientes/extraer-perfil").header("Authorization", bearer(asesor))
                        .contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("JSON")));
    }
}
