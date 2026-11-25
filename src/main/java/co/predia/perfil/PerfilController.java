package co.predia.perfil;

import co.predia.auth.AuthController;
import co.predia.comun.seguridad.ServicioJwt;
import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.equipo.Periodo;
import co.predia.equipo.ServicioRendimiento;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** «Mi perfil»: el asesor o administrador edita su nombre y ve su propio rendimiento (nunca el de los demás). */
@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Mi perfil")
public class PerfilController {

    public record SolicitudPerfil(@NotBlank @Size(max = 150) String nombre) {
    }

    public record RendimientoPersonal(Periodo periodo, ServicioRendimiento.Rendimiento rendimiento,
                                      List<ServicioRendimiento.CierreReciente> ultimosCierres) {
    }

    private final UsuarioRepository usuarios;
    private final InmobiliariaRepository inmobiliarias;
    private final ServicioJwt servicioJwt;
    private final ServicioRendimiento rendimiento;
    private final FotoPerfilRepositorio fotos;
    private final SesionActual sesion;

    public PerfilController(UsuarioRepository usuarios, InmobiliariaRepository inmobiliarias, ServicioJwt servicioJwt,
                            ServicioRendimiento rendimiento, FotoPerfilRepositorio fotos, SesionActual sesion) {
        this.usuarios = usuarios;
        this.inmobiliarias = inmobiliarias;
        this.servicioJwt = servicioJwt;
        this.rendimiento = rendimiento;
        this.fotos = fotos;
        this.sesion = sesion;
    }

    @PutMapping
    @Transactional
    @Operation(summary = "Cambia tu nombre. Devuelve una sesión nueva: el nombre viaja dentro del token")
    public AuthController.RespuestaLogin cambiarNombre(@Valid @RequestBody SolicitudPerfil s) {
        UsuarioActual actor = sesion.usuario();
        Usuario usuario = usuarios.findByIdAndInmobiliariaId(actor.id(), actor.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", actor.id()));
        usuario.actualizarDatos(s.nombre().strip().replaceAll("\\s+", " "), usuario.getEmail());
        usuarios.save(usuario);
        ServicioJwt.TokenEmitido token = servicioJwt.emitir(usuario);
        String nombreInmobiliaria = inmobiliarias.findById(usuario.getInmobiliariaId()).map(i -> i.getNombre()).orElse("");
        return new AuthController.RespuestaLogin(token.token(), token.expiraEn(), new AuthController.PerfilUsuario(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol(), usuario.getInmobiliariaId(),
                nombreInmobiliaria, fotos.version(usuario.getInmobiliariaId(), SujetoFoto.USUARIO, usuario.getId()).orElse(null)));
    }

    @GetMapping("/rendimiento")
    @Operation(summary = "Tus propias cifras de ventas en el periodo y tus últimos cierres")
    public RendimientoPersonal miRendimiento(@RequestParam(defaultValue = "ANIO") Periodo periodo) {
        UsuarioActual actor = sesion.usuario();
        return new RendimientoPersonal(periodo,
                rendimiento.de(actor.inmobiliariaId(), actor.id(), periodo, actor.nombre(), actor.rol().name()),
                rendimiento.ultimosCierres(actor.inmobiliariaId(), actor.id(), periodo, 5));
    }
}
