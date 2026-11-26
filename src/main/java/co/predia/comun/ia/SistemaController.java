package co.predia.comun.ia;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sistema")
@Tag(name = "Sistema")
public class SistemaController {

    private final EstadoIa estadoIa;

    public SistemaController(EstadoIa estadoIa) {
        this.estadoIa = estadoIa;
    }

    @GetMapping("/estado")
    @Operation(summary = "Indica si la IA está disponible, para mostrar el aviso de modo manual")
    public EstadoSistema estado() {
        String aviso = estadoIa.disponible() ? null
                : "La IA no está disponible: Predia funciona en modo manual (formularios y búsqueda por texto).";
        return new EstadoSistema(estadoIa.disponible(), estadoIa.proveedor(), estadoIa.modeloChat(),
                estadoIa.modeloEmbeddings(), aviso);
    }

    public record EstadoSistema(boolean iaDisponible, String proveedor, String modeloChat, String modeloEmbeddings,
                         String aviso) {
    }
}
