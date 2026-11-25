package co.predia.perfil;

import co.predia.clientes.ClienteRepository;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.foto.ProcesadorImagen;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Subir, ver y quitar la foto de perfil de un asesor, un cliente o un vendedor. Reglas: la persona debe ser de la misma
 * inmobiliaria; la foto de un cliente o vendedor exige que haya autorizado el tratamiento de sus datos (es un dato
 * personal, Ley 1581 de 2012); y la foto de un asesor solo la cambia él mismo o un administrador.
 */
@Service
public class ServicioFotosPerfil {

    static final int MAXIMO_BYTES = 10 * 1024 * 1024;

    private final FotoPerfilRepositorio fotos;
    private final ClienteRepository clientes;
    private final PropietarioRepository propietarios;
    private final UsuarioRepository usuarios;

    public ServicioFotosPerfil(FotoPerfilRepositorio fotos, ClienteRepository clientes, PropietarioRepository propietarios,
                               UsuarioRepository usuarios) {
        this.fotos = fotos;
        this.clientes = clientes;
        this.propietarios = propietarios;
        this.usuarios = usuarios;
    }

    public String subir(UsuarioActual actor, SujetoFoto sujeto, Long id, byte[] bytes, int focoX, int focoY) {
        verificarQuePuedeCambiar(actor, sujeto, id);
        if (bytes == null || bytes.length == 0) {
            throw new ReglaNegocioException("Elige una imagen para la foto.");
        }
        if (bytes.length > MAXIMO_BYTES) {
            throw new ReglaNegocioException("La foto pesa más de 10 MB. Elige una más liviana.");
        }
        ProcesadorImagen.Imagen imagen = ProcesadorImagen.procesarPerfil(bytes, focoX, focoY);
        fotos.guardar(actor.inmobiliariaId(), sujeto, id, imagen, actor.id());
        return imagen.hash();
    }

    public FotoPerfilRepositorio.Bytes ver(UsuarioActual actor, SujetoFoto sujeto, Long id, boolean miniatura) {
        verificarQueExiste(actor.inmobiliariaId(), sujeto, id);
        return fotos.leer(actor.inmobiliariaId(), sujeto, id, miniatura)
                .orElseThrow(() -> new RecursoNoEncontradoException("Foto de perfil", id));
    }

    public void quitar(UsuarioActual actor, SujetoFoto sujeto, Long id) {
        verificarQuePuedeCambiar(actor, sujeto, id);
        fotos.borrar(actor.inmobiliariaId(), sujeto, id);
    }

    private void verificarQuePuedeCambiar(UsuarioActual actor, SujetoFoto sujeto, Long id) {
        verificarQueExiste(actor.inmobiliariaId(), sujeto, id);
        switch (sujeto) {
            case USUARIO -> {
                if (!actor.esAdmin() && !actor.id().equals(id)) {
                    throw new AccessDeniedException("Solo puedes cambiar tu propia foto.");
                }
            }
            case CLIENTE -> {
                if (!clientes.findByIdAndInmobiliariaId(id, actor.inmobiliariaId()).orElseThrow().isAutorizacionDatos()) {
                    throw sinAutorizacion("El cliente");
                }
            }
            case PROPIETARIO -> {
                if (!propietarios.findByIdAndInmobiliariaId(id, actor.inmobiliariaId()).orElseThrow().isAutorizacionDatos()) {
                    throw sinAutorizacion("El vendedor");
                }
            }
        }
    }

    private static ReglaNegocioException sinAutorizacion(String quien) {
        return new ReglaNegocioException(quien + " no ha autorizado el tratamiento de sus datos personales: "
                + "sin esa autorización Predia no guarda su foto.");
    }

    /** Una persona de otra inmobiliaria responde igual que una que no existe. */
    private void verificarQueExiste(Long inmobiliariaId, SujetoFoto sujeto, Long id) {
        boolean existe = switch (sujeto) {
            case USUARIO -> usuarios.findByIdAndInmobiliariaId(id, inmobiliariaId).isPresent();
            case CLIENTE -> clientes.findByIdAndInmobiliariaId(id, inmobiliariaId).isPresent();
            case PROPIETARIO -> propietarios.findByIdAndInmobiliariaId(id, inmobiliariaId).isPresent();
        };
        if (!existe) {
            throw new RecursoNoEncontradoException(switch (sujeto) {
                case USUARIO -> "Usuario";
                case CLIENTE -> "Cliente";
                case PROPIETARIO -> "Propietario";
            }, id);
        }
    }
}
