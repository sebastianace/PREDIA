package co.predia.inventario.foto;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;

/** Sube, consulta y quita la foto de portada de un inmueble. El inmueble siempre se busca dentro de la inmobiliaria. */
@Service
public class ServicioFotosInmueble {

    /** Lo que sube el navegador ya viene reducido (máx. 1600 px); esto solo frena archivos absurdos. */
    public static final int MAX_BYTES = 8 * 1024 * 1024;

    private final InmuebleRepository inmuebles;
    private final FotoInmuebleRepositorio fotos;

    public ServicioFotosInmueble(InmuebleRepository inmuebles, FotoInmuebleRepositorio fotos) {
        this.inmuebles = inmuebles;
        this.fotos = fotos;
    }

    public FotoInfo subir(Long inmobiliariaId, Long usuarioId, Long inmuebleId, byte[] original, int focoX, int focoY) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        if (original == null || original.length == 0) {
            throw new ReglaNegocioException("Elige una imagen para subir.");
        }
        if (original.length > MAX_BYTES) {
            throw new ReglaNegocioException("La imagen pesa más de 8 MB. Elige una más liviana.");
        }
        ProcesadorImagen.Imagen imagen = ProcesadorImagen.procesar(original);
        fotos.guardar(inmobiliariaId, inmuebleId, usuarioId, imagen, limitar(focoX), limitar(focoY));
        return fotos.info(inmobiliariaId, inmuebleId).orElseThrow();
    }

    public FotoInfo fijarFoco(Long inmobiliariaId, Long inmuebleId, int focoX, int focoY) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        if (!fotos.fijarFoco(inmobiliariaId, inmuebleId, limitar(focoX), limitar(focoY))) {
            throw new RecursoNoEncontradoException("Foto del inmueble", inmuebleId);
        }
        return fotos.info(inmobiliariaId, inmuebleId).orElseThrow();
    }

    public FotoInmuebleRepositorio.Bytes leer(Long inmobiliariaId, Long inmuebleId, boolean miniatura) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        return fotos.leer(inmobiliariaId, inmuebleId, miniatura)
                .orElseThrow(() -> new RecursoNoEncontradoException("Foto del inmueble", inmuebleId));
    }

    public void quitar(Long inmobiliariaId, Long inmuebleId) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        fotos.borrar(inmobiliariaId, inmuebleId);
    }

    private void exigirInmueble(Long inmobiliariaId, Long inmuebleId) {
        inmuebles.findByIdAndInmobiliariaId(inmuebleId, inmobiliariaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", inmuebleId));
    }

    private static int limitar(int foco) {
        return Math.max(0, Math.min(100, foco));
    }
}
