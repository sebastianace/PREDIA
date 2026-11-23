package co.predia.inventario.foto;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.ReglaNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

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
    @Operation(summary = "Descarga la foto del inmueble (tam=miniatura para las listas, grande por defecto)")
    public ResponseEntity<byte[]> ver(@PathVariable Long id, @RequestParam(defaultValue = "grande") String tam,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String sinCambios) {
        FotoInmuebleRepositorio.Bytes foto = servicio.leer(sesion.inmobiliariaId(), id, "miniatura".equals(tam));
        String etag = "\"" + foto.hash() + "-" + ("miniatura".equals(tam) ? "m" : "g") + "\"";
        // Es privada (solo con sesión) y el hash cambia al subir otra imagen, así que el navegador puede guardarla.
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable();
        if (etag.equals(sinCambios)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).eTag(etag).cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff").body(foto.contenido());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza la foto del inmueble (JPG, PNG, GIF o BMP); se guarda reducida")
    public FotoInfo subir(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo,
                          @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoX,
                          @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoY) {
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer la imagen. Intenta de nuevo.");
        }
        return servicio.subir(sesion.inmobiliariaId(), sesion.usuario().id(), id, bytes, focoX, focoY);
    }

    @PatchMapping("/foco")
    @Operation(summary = "Cambia qué parte de la foto queda visible al recortarla")
    public FotoInfo foco(@PathVariable Long id, @Valid @RequestBody SolicitudFoco s) {
        return servicio.fijarFoco(sesion.inmobiliariaId(), id, s.focoX(), s.focoY());
    }

    @DeleteMapping
    @Operation(summary = "Quita la foto del inmueble")
    public ResponseEntity<Void> quitar(@PathVariable Long id) {
        servicio.quitar(sesion.inmobiliariaId(), id);
        return ResponseEntity.noContent().build();
    }

    public record SolicitudFoco(@Min(0) @Max(100) int focoX, @Min(0) @Max(100) int focoY) {
    }
}
