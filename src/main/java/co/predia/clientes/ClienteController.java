package co.predia.clientes;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.perfil.FotoPerfilRepositorio;
import co.predia.perfil.SujetoFoto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Clientes compradores. No se guarda ninguno sin la autorización de tratamiento de datos. Los datos
 * personales (correo, teléfono) no se escriben en los logs.
 */
@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteRepository clientes;
    private final PresupuestoService presupuestos;
    private final ExtractorPerfil extractor;
    private final SesionActual sesion;
    private final FotoPerfilRepositorio fotos;

    public ClienteController(ClienteRepository clientes, PresupuestoService presupuestos, ExtractorPerfil extractor,
                             SesionActual sesion, FotoPerfilRepositorio fotos) {
        this.fotos = fotos;
        this.clientes = clientes;
        this.presupuestos = presupuestos;
        this.extractor = extractor;
        this.sesion = sesion;
    }

    @GetMapping
    @Operation(summary = "Lista los clientes de la inmobiliaria")
    public List<ClienteDto> listar() {
        Map<Long, String> fotoDe = fotos.versiones(sesion.inmobiliariaId(), SujetoFoto.CLIENTE);
        return clientes.findByInmobiliariaIdOrderByNombreAsc(sesion.inmobiliariaId()).stream()
                .map(c -> ClienteDto.desde(c, topeDe(c), fotoDe.get(c.getId()))).toList();
    }

    private ClienteDto conFoto(Cliente c) {
        return ClienteDto.desde(c, null, fotos.version(sesion.inmobiliariaId(), SujetoFoto.CLIENTE, c.getId()).orElse(null));
    }

    /** El presupuesto de un comprador con el plazo y la tasa por defecto; null si no aplica o no hay tasa configurada. */
    private CalculadoraPresupuesto.Presupuesto topeDe(Cliente cliente) {
        if (cliente.getIntencion() != Intencion.COMPRAR) {
            return null;
        }
        try {
            return presupuestos.calcular(sesion.inmobiliariaId(), cliente.getId(), null, null, false).presupuesto();
        } catch (RuntimeException e) {
            // Sin una tasa vigente la lista sigue funcionando: la tarjeta simplemente no muestra el tope.
            return null;
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un cliente")
    public ClienteDto detalle(@PathVariable Long id) {
        return conFoto(buscar(id));
    }

    @PostMapping("/extraer-perfil")
    @Operation(summary = "La IA propone un perfil a partir de lo que dijo el cliente. Es solo una sugerencia: no guarda nada")
    public PerfilExtraido extraerPerfil(@Valid @RequestBody SolicitudExtraccion s) {
        return new PerfilExtraido(extractor.extraer(s.texto().strip()), true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Guarda un cliente. Exige la autorización de tratamiento de datos (Ley 1581 de 2012)")
    public ClienteDto crear(@Valid @RequestBody SolicitudCliente s) {
        if (!Boolean.TRUE.equals(s.autorizacionDatos())) {
            throw new ReglaNegocioException("Para guardar al cliente debes registrar su autorización de "
                    + "tratamiento de datos personales (Ley 1581 de 2012).");
        }
        UsuarioActual usuario = sesion.usuario();
        Cliente cliente = new Cliente(usuario.inmobiliariaId(), usuario.id());
        aplicar(cliente, s);
        return conFoto(clientes.save(cliente));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza el perfil de un cliente")
    public ClienteDto actualizar(@PathVariable Long id, @Valid @RequestBody SolicitudCliente s) {
        Cliente cliente = buscar(id);
        aplicar(cliente, s);
        return conFoto(clientes.save(cliente));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina a un cliente y sus datos (derecho de supresión, Ley 1581 de 2012)")
    public void eliminar(@PathVariable Long id) {
        Cliente cliente = buscar(id);
        // La supresión incluye su foto: es un dato personal y no puede quedar huérfana.
        fotos.borrar(sesion.inmobiliariaId(), SujetoFoto.CLIENTE, cliente.getId());
        clientes.delete(cliente);
    }

    @GetMapping("/{id}/presupuesto")
    @Operation(summary = "Presupuesto de compra del cliente: cuota máxima y tope de compra")
    public PresupuestoCliente presupuesto(@PathVariable Long id, @RequestParam(required = false) Integer plazoAnios,
                                          @RequestParam(required = false) Long tasaId,
                                          @RequestParam(defaultValue = "false") boolean vis) {
        return presupuestos.calcular(sesion.inmobiliariaId(), id, plazoAnios, tasaId, vis);
    }

    private Cliente buscar(Long id) {
        return clientes.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    private static void aplicar(Cliente cliente, SolicitudCliente s) {
        cliente.actualizarPerfil(s.nombre().strip(), vacioANulo(s.email()), vacioANulo(s.telefono()),
                s.ingresoFamiliar(), s.ahorro() == null ? BigDecimal.ZERO : s.ahorro(),
                s.deudasMensuales() == null ? BigDecimal.ZERO : s.deudasMensuales(), s.intencion(),
                (short) (s.habitacionesMin() == null ? 0 : s.habitacionesMin()),
                s.zonas() == null ? List.of() : s.zonas().stream().filter(z -> z != null && !z.isBlank())
                        .map(String::strip).toList(),
                s.preferencias());
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    public record SolicitudExtraccion(@NotBlank @Size(max = 4000) String texto) {
    }

    /** {@code sugerido} siempre es true: lo extraído por la IA queda SUGERIDO hasta que el asesor lo confirme. */
    public record PerfilExtraido(PerfilSugerido perfil, boolean sugerido) {
    }

    public record SolicitudCliente(
            @NotBlank @Size(max = 150) String nombre,
            @Email @Size(max = 150) String email,
            @Size(max = 30) String telefono,
            @NotNull @DecimalMin("0") BigDecimal ingresoFamiliar,
            @DecimalMin("0") BigDecimal ahorro,
            @DecimalMin("0") BigDecimal deudasMensuales,
            @NotNull Intencion intencion,
            @Min(0) @Max(10) Integer habitacionesMin,
            List<@Size(max = 100) String> zonas,
            @Size(max = 2000) String preferencias,
            Boolean autorizacionDatos) {
    }
}
