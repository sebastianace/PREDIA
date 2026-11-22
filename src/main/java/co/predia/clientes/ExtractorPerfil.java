package co.predia.clientes;

import co.predia.comun.ia.EstadoIa;
import co.predia.comun.web.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Extrae un perfil estructurado a partir de lo que el asesor pegó o dictó. La salida del modelo se valida
 * en Java (nada negativo, intención válida) y nunca se guarda sola: el asesor la confirma en el formulario.
 * El texto pegado se envía al modelo para extraerlo, pero no se almacena.
 */
@Component
public class ExtractorPerfil {

    private static final Logger log = LoggerFactory.getLogger(ExtractorPerfil.class);

    private static final String INSTRUCCION = """
            Extraes el perfil de un cliente de una inmobiliaria colombiana a partir de lo que dijo, pegado o \
            dictado por su asesor. Reglas: usa SOLO lo que está escrito; no calcules, no estimes ni completes \
            datos. Los montos van en pesos colombianos como números enteros, tal como los dijo el cliente \
            (por ejemplo, "7,5 millones" es 7500000). Si un dato no aparece, déjalo vacío. La intención es \
            COMPRAR o ARRENDAR. Las zonas son los barrios o municipios que mencionó. En preferencias resume \
            en una frase breve lo que busca (mascotas, parqueadero, cercanía a colegios, etc.).""";

    private final ChatClient chatClient;
    private final EstadoIa estadoIa;

    public ExtractorPerfil(ChatClient.Builder builder, EstadoIa estadoIa) {
        this.chatClient = builder.build();
        this.estadoIa = estadoIa;
    }

    public PerfilSugerido extraer(String texto) {
        if (!estadoIa.disponible()) {
            throw new ReglaNegocioException(
                    "La IA no está disponible. Completa el perfil del cliente con el formulario.");
        }
        PerfilSugerido crudo;
        try {
            crudo = chatClient.prompt().system(INSTRUCCION)
                    .user("Esto fue lo que dijo el cliente:\n\n" + texto)
                    .call().entity(PerfilSugerido.class);
        } catch (Exception e) {
            log.warn("No se pudo extraer el perfil con IA: {}", e.getMessage());
            throw new ReglaNegocioException(
                    "No se pudo leer el perfil con la IA. Completa el perfil del cliente con el formulario.");
        }
        return validar(crudo);
    }

    /** Descarta lo que no tenga sentido (negativos, textos vacíos) en vez de confiar ciegamente en el modelo. */
    static PerfilSugerido validar(PerfilSugerido p) {
        if (p == null) {
            return new PerfilSugerido(null, null, null, null, null, null, List.of(), null, null, null);
        }
        List<String> zonas = new ArrayList<>();
        if (p.zonas() != null) {
            p.zonas().stream().filter(z -> z != null && !z.isBlank()).map(String::strip).forEach(zonas::add);
        }
        Integer habitaciones = p.habitacionesMin() != null && p.habitacionesMin() >= 0 && p.habitacionesMin() <= 10
                ? p.habitacionesMin() : null;
        return new PerfilSugerido(limpio(p.nombre()), noNegativo(p.ingresoFamiliar()), noNegativo(p.ahorro()),
                noNegativo(p.deudasMensuales()), p.intencion(), habitaciones, zonas, limpio(p.preferencias()),
                limpio(p.email()), limpio(p.telefono()));
    }

    private static Long noNegativo(Long valor) {
        return valor != null && valor >= 0 ? valor : null;
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
