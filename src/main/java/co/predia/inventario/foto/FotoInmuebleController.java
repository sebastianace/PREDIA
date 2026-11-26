package co.predia.inventario.foto;

import co.predia.comun.seguridad.SesionActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/inmuebles/{id}/foto")
@Tag(name = "Fotos de inmuebles")
public class FotoInmuebleController {

    private final ServicioFotosInmueble servicio;
    private final SesionActual sesion;

    public FotoInmuebleController(ServicioFotosInmueble servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Descarga la foto de portada del inmueble (tam=miniatura para las listas, grande por defecto)")
    public ResponseEntity<byte[]> ver(@PathVariable Long id, @RequestParam(defaultValue = "grande") String tam,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String sinCambios) {
        boolean miniatura = "miniatura".equals(tam);
        FotoInmuebleRepositorio.Bytes foto = servicio.leer(sesion.inmobiliariaId(), id, miniatura);
        // La portada cambia detrás de la misma dirección: se revalida siempre (ver RespuestaImagen).
        return RespuestaImagen.portada(foto.contenido(), foto.hash(), miniatura, sinCambios);
    }
}
