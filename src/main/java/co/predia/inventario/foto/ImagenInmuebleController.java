package co.predia.inventario.foto;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.ReglaNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** La galería de fotos y planos de un inmueble, administrada por el personal. El comprador la ve por /api/comprador. */
@RestController
@RequestMapping("/api/inmuebles/{id}/imagenes")
@Tag(name = "Galería de inmuebles")
public class ImagenInmuebleController {

    private final ServicioImagenesInmueble servicio;
    private final SesionActual sesion;

    public ImagenInmuebleController(ServicioImagenesInmueble servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Las imágenes del inmueble, en el orden en que se muestran")
    public List<ImagenInfo> listar(@PathVariable Long id) {
        return servicio.listar(sesion.inmobiliariaId(), id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega una foto o un plano al final de la galería (JPG, PNG, GIF o BMP); se guarda reducido")
    public ImagenInfo agregar(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo,
                              @RequestParam(defaultValue = "FOTO") TipoImagen tipo,
                              @RequestParam(required = false) @Size(max = 60) String etiqueta,
                              @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoX,
                              @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoY) {
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer la imagen. Intenta de nuevo.");
        }
        return servicio.agregar(sesion.inmobiliariaId(), sesion.usuario().id(), id, bytes, tipo, etiqueta, focoX, focoY);
    }

    @GetMapping("/{imagenId}")
    @Operation(summary = "Descarga una imagen (tam=miniatura para las listas, grande por defecto)")
    public ResponseEntity<byte[]> ver(@PathVariable Long id, @PathVariable Long imagenId,
                                      @RequestParam(defaultValue = "grande") String tam,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String sinCambios) {
        boolean miniatura = "miniatura".equals(tam);
        ImagenInmuebleRepositorio.Bytes imagen = servicio.leer(sesion.inmobiliariaId(), id, imagenId, miniatura);
        String etag = "\"" + imagen.hash() + "-" + imagenId + (miniatura ? "-m" : "-g") + "\"";
        // Es privada (solo con sesión) y el hash cambia al subir otra imagen, así que el navegador puede guardarla.
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable();
        if (etag.equals(sinCambios)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).eTag(etag).cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff").body(imagen.contenido());
    }

    @PatchMapping("/{imagenId}")
    @Operation(summary = "Cambia el tipo (foto o plano), la etiqueta o el encuadre de una imagen")
    public ImagenInfo editar(@PathVariable Long id, @PathVariable Long imagenId, @Valid @RequestBody SolicitudEdicion s) {
        return servicio.editar(sesion.inmobiliariaId(), id, imagenId, s.tipo(), s.etiqueta(), s.focoX(), s.focoY());
    }

    @PutMapping("/orden")
    @Operation(summary = "Fija el orden de la galería; la primera foto de la lista es la portada")
    public List<ImagenInfo> ordenar(@PathVariable Long id, @Valid @RequestBody SolicitudOrden s) {
        return servicio.ordenar(sesion.inmobiliariaId(), id, s.ids());
    }

    @DeleteMapping("/{imagenId}")
    @Operation(summary = "Quita una imagen de la galería")
    public ResponseEntity<Void> quitar(@PathVariable Long id, @PathVariable Long imagenId) {
        servicio.quitar(sesion.inmobiliariaId(), id, imagenId);
        return ResponseEntity.noContent().build();
    }

    public record SolicitudEdicion(TipoImagen tipo, @Size(max = 60) String etiqueta,
                                   @Min(0) @Max(100) Integer focoX, @Min(0) @Max(100) Integer focoY) {
    }

    public record SolicitudOrden(@NotNull List<Long> ids) {
    }
}
