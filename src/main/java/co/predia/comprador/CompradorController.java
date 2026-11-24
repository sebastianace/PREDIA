package co.predia.comprador;

import co.predia.comun.seguridad.SesionActual;
import co.predia.inventario.foto.FotoInmuebleRepositorio;
import co.predia.inventario.foto.PlanoInmuebleRepositorio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** El portal del comprador: solo el rol COMPRADOR entra aquí (lo exige ConfiguracionSeguridad). */
@RestController
@RequestMapping("/api/comprador")
@Tag(name = "Portal del comprador")
public class CompradorController {

    private final ServicioComprador servicio;
    private final SesionActual sesion;

    public CompradorController(ServicioComprador servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    @GetMapping("/perfil")
    @Operation(summary = "Su nombre, su asesor y su presupuesto (tope de compra y de dónde sale)")
    public ServicioComprador.PerfilComprador perfil() {
        return servicio.perfil(sesion.usuario());
    }

    @GetMapping("/inmuebles")
    @Operation(summary = "Los inmuebles que su asesor le compartió")
    public List<ServicioComprador.InmuebleResumen> inmuebles() {
        return servicio.inmuebles(sesion.usuario());
    }

    @GetMapping("/inmuebles/{id}")
    @Operation(summary = "La ficha de un inmueble compartido, con los controles del simulador")
    public ServicioComprador.FichaComprador ficha(@PathVariable Long id) {
        return servicio.ficha(sesion.usuario(), id);
    }

    @PostMapping("/inmuebles/{id}/simulacion")
    @Operation(summary = "Simula la compra con la cuota inicial y el plazo elegidos; las cifras las calcula el servidor")
    public SimuladorCompraService.SimulacionCompra simular(@PathVariable Long id, @Valid @RequestBody SolicitudSimulacion s) {
        return servicio.simular(sesion.usuario(), id, s.cuotaInicial(), s.plazoAnios());
    }

    @PutMapping("/inmuebles/{id}/interes")
    @Operation(summary = "Marca «me interesa» o «no me interesa» (null lo quita)")
    public void interes(@PathVariable Long id, @RequestBody SolicitudInteres s) {
        servicio.marcarInteres(sesion.usuario(), id, s.interes());
    }

    @GetMapping("/inmuebles/{id}/foto")
    @Operation(summary = "La foto de un inmueble compartido")
    public ResponseEntity<byte[]> foto(@PathVariable Long id, @RequestParam(defaultValue = "grande") String tam) {
        FotoInmuebleRepositorio.Bytes foto = servicio.foto(sesion.usuario(), id, "miniatura".equals(tam));
        return imagen(foto.contenido());
    }

    @GetMapping("/inmuebles/{id}/plano")
    @Operation(summary = "El plano de un inmueble compartido")
    public ResponseEntity<byte[]> plano(@PathVariable Long id) {
        PlanoInmuebleRepositorio.Plano plano = servicio.plano(sesion.usuario(), id);
        return imagen(plano.contenido());
    }

    @PostMapping("/solicitudes")
    @Operation(summary = "«Quiero más información»: un aviso para su asesor (no envía ningún correo)")
    public ServicioComprador.SolicitudDto solicitar(@Valid @RequestBody SolicitudMensaje s) {
        return servicio.solicitar(sesion.usuario(), s.inmuebleId(), s.mensaje());
    }

    @GetMapping("/solicitudes")
    @Operation(summary = "Sus mensajes al asesor y si ya los atendieron")
    public List<ServicioComprador.SolicitudDto> solicitudes() {
        return servicio.solicitudes(sesion.usuario());
    }

    private static ResponseEntity<byte[]> imagen(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(bytes);
    }

    public record SolicitudSimulacion(@NotNull BigDecimal cuotaInicial, Integer plazoAnios) {
    }

    public record SolicitudInteres(InmuebleCompartido.Interes interes) {
    }

    public record SolicitudMensaje(Long inmuebleId, @NotBlank @Size(max = 1000) String mensaje) {
    }
}
