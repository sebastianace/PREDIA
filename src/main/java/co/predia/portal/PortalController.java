package co.predia.portal;

import co.predia.comun.seguridad.SesionActual;
import co.predia.inventario.foto.FotoInmuebleRepositorio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/** El portal del propietario: solo el rol PROPIETARIO entra aquí (lo exige ConfiguracionSeguridad). */
@RestController
@RequestMapping("/api/portal")
@Tag(name = "Portal del propietario")
public class PortalController {

    private final ServicioPortal servicio;
    private final SesionActual sesion;

    public PortalController(ServicioPortal servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping("/negocios")
    @Operation(summary = "Las ventas del propietario: avance, semáforo, fecha de firma y su asesor")
    public List<ServicioPortal.NegocioPortal> negocios() {
        return servicio.negocios(sesion.usuario());
    }

    @GetMapping("/negocios/{id}")
    @Operation(summary = "Los documentos de una venta: qué falta, qué venció y cómo conseguirlo")
    public ServicioPortal.DetallePortal detalle(@PathVariable Long id) {
        return servicio.detalle(sesion.usuario(), id);
    }

    @PostMapping(value = "/negocios/{id}/requisitos/{requisitoId}/documento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envía el PDF de un documento: queda en revisión (SUGERIDO) hasta que el asesor lo confirme")
    public ServicioPortal.DetallePortal enviar(@PathVariable Long id, @PathVariable Long requisitoId,
                                               @RequestParam("archivo") MultipartFile archivo,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaExpedicion) {
        return servicio.enviar(sesion.usuario(), id, requisitoId, archivo, fechaExpedicion);
    }

    @GetMapping("/negocios/{id}/recordatorios")
    @Operation(summary = "Los recordatorios que la inmobiliaria le envió (no los borradores)")
    public List<ServicioPortal.RecordatorioPortal> recordatorios(@PathVariable Long id) {
        return servicio.recordatorios(sesion.usuario(), id);
    }

    @GetMapping("/negocios/{id}/foto")
    @Operation(summary = "La foto del inmueble de la venta, si tiene")
    public ResponseEntity<byte[]> foto(@PathVariable Long id, @RequestParam(defaultValue = "grande") String tam) {
        FotoInmuebleRepositorio.Bytes foto = servicio.foto(sesion.usuario(), id, "miniatura".equals(tam));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(foto.contenido());
    }
}
