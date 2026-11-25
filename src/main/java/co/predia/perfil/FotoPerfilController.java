package co.predia.perfil;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.foto.RespuestaImagen;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Las fotos de perfil del personal, de los clientes y de los vendedores. Es personal de la inmobiliaria (asesor o
 * administrador) quien las gestiona; el tenant sale siempre de la sesión. La foto de un asesor está bajo /api/perfil
 * porque /api/usuarios es solo del administrador y todo el personal necesita ver las fotos de sus compañeros.
 */
@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Fotos de perfil")
public class FotoPerfilController {

    private final ServicioFotosPerfil servicio;
    private final SesionActual sesion;

    public FotoPerfilController(ServicioFotosPerfil servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    // --- Clientes ---------------------------------------------------------------------------------------------------

    @GetMapping("/clientes/{id}/foto")
    @Operation(summary = "La foto de perfil de un cliente (tam=miniatura para las listas)")
    public ResponseEntity<byte[]> fotoCliente(@PathVariable Long id, @RequestParam(defaultValue = "grande") String tam,
                                              @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String sinCambios) {
        return ver(SujetoFoto.CLIENTE, id, tam, sinCambios);
    }

    @PutMapping(path = "/clientes/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza la foto de un cliente (se recorta en cuadrado y se guarda reducida)")
    public Map<String, String> subirCliente(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo,
                                            @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoX,
                                            @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoY) {
        return subir(SujetoFoto.CLIENTE, id, archivo, focoX, focoY);
    }

    @DeleteMapping("/clientes/{id}/foto")
    @Operation(summary = "Quita la foto de un cliente")
    public void quitarCliente(@PathVariable Long id) {
        servicio.quitar(sesion.usuario(), SujetoFoto.CLIENTE, id);
    }

    // --- Vendedores (propietarios) ----------------------------------------------------------------------------------

    @GetMapping("/propietarios/{id}/foto")
    @Operation(summary = "La foto de perfil de un vendedor (tam=miniatura para las listas)")
    public ResponseEntity<byte[]> fotoPropietario(@PathVariable Long id, @RequestParam(defaultValue = "grande") String tam,
                                                  @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String sinCambios) {
        return ver(SujetoFoto.PROPIETARIO, id, tam, sinCambios);
    }

    @PutMapping(path = "/propietarios/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza la foto de un vendedor (se recorta en cuadrado y se guarda reducida)")
    public Map<String, String> subirPropietario(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo,
                                                @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoX,
                                                @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoY) {
        return subir(SujetoFoto.PROPIETARIO, id, archivo, focoX, focoY);
    }

    @DeleteMapping("/propietarios/{id}/foto")
    @Operation(summary = "Quita la foto de un vendedor")
    public void quitarPropietario(@PathVariable Long id) {
        servicio.quitar(sesion.usuario(), SujetoFoto.PROPIETARIO, id);
    }

    // --- Personal (asesores y administradores) ----------------------------------------------------------------------

    @GetMapping("/perfil/usuarios/{id}/foto")
    @Operation(summary = "La foto de perfil de un asesor o administrador de la misma inmobiliaria")
    public ResponseEntity<byte[]> fotoUsuario(@PathVariable Long id, @RequestParam(defaultValue = "grande") String tam,
                                              @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String sinCambios) {
        return ver(SujetoFoto.USUARIO, id, tam, sinCambios);
    }

    @PutMapping(path = "/perfil/usuarios/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube o reemplaza la foto de un asesor: solo él mismo o un administrador")
    public Map<String, String> subirUsuario(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo,
                                            @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoX,
                                            @RequestParam(defaultValue = "50") @Min(0) @Max(100) int focoY) {
        return subir(SujetoFoto.USUARIO, id, archivo, focoX, focoY);
    }

    @DeleteMapping("/perfil/usuarios/{id}/foto")
    @Operation(summary = "Quita la foto de un asesor: solo él mismo o un administrador")
    public void quitarUsuario(@PathVariable Long id) {
        servicio.quitar(sesion.usuario(), SujetoFoto.USUARIO, id);
    }

    // --- Apoyo ------------------------------------------------------------------------------------------------------

    private ResponseEntity<byte[]> ver(SujetoFoto sujeto, Long id, String tam, String sinCambios) {
        boolean miniatura = "miniatura".equals(tam);
        FotoPerfilRepositorio.Bytes foto = servicio.ver(sesion.usuario(), sujeto, id, miniatura);
        // La foto cambia detrás de la misma dirección: se revalida siempre (ver RespuestaImagen).
        return RespuestaImagen.portada(foto.contenido(), foto.hash(), miniatura, sinCambios);
    }

    private Map<String, String> subir(SujetoFoto sujeto, Long id, MultipartFile archivo, int focoX, int focoY) {
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer la imagen. Intenta de nuevo.");
        }
        return Map.of("fotoVersion", servicio.subir(sesion.usuario(), sujeto, id, bytes, focoX, focoY));
    }
}
