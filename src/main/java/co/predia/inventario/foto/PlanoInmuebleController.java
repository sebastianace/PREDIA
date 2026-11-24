package co.predia.inventario.foto;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.ReglaNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** El plano de un inmueble, administrado por el personal. El comprador lo ve por /api/comprador. */
@RestController
@RequestMapping("/api/inmuebles/{id}/plano")
@Tag(name = "Planos de inmuebles")
public class PlanoInmuebleController {

    private final ServicioPlanosInmueble servicio;
    private final SesionActual sesion;

    public PlanoInmuebleController(ServicioPlanosInmueble servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Descarga el plano del inmueble")
    public ResponseEntity<byte[]> ver(@PathVariable Long id) {
        PlanoInmuebleRepositorio.Plano plano = servicio.leer(sesion.inmobiliariaId(), id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(plano.contenido());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza el plano (JPG, PNG, GIF o BMP); se guarda reducido a 2.000 px")
    public Map<String, String> subir(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        try {
            return Map.of("version", servicio.subir(sesion.inmobiliariaId(), sesion.usuario().id(), id,
                    archivo.getBytes()));
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer la imagen. Intenta de nuevo.");
        }
    }

    @DeleteMapping
    @Operation(summary = "Quita el plano del inmueble")
    public ResponseEntity<Void> quitar(@PathVariable Long id) {
        servicio.quitar(sesion.inmobiliariaId(), id);
        return ResponseEntity.noContent().build();
    }
}
