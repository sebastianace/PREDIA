package co.predia.inventario.foto;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;

/** Sube, consulta y quita el plano de un inmueble. El inmueble siempre se busca dentro de la inmobiliaria. */
@Service
public class ServicioPlanosInmueble {

    private final InmuebleRepository inmuebles;
    private final PlanoInmuebleRepositorio planos;

    public ServicioPlanosInmueble(InmuebleRepository inmuebles, PlanoInmuebleRepositorio planos) {
        this.inmuebles = inmuebles;
        this.planos = planos;
    }

    public String subir(Long inmobiliariaId, Long usuarioId, Long inmuebleId, byte[] original) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        if (original == null || original.length == 0) {
            throw new ReglaNegocioException("Elige una imagen del plano para subir.");
        }
        if (original.length > ServicioFotosInmueble.MAX_BYTES) {
            throw new ReglaNegocioException("La imagen pesa más de 8 MB. Elige una más liviana.");
        }
        ProcesadorImagen.Plano plano = ProcesadorImagen.procesarPlano(original);
        planos.guardar(inmobiliariaId, inmuebleId, usuarioId, plano);
        return plano.hash();
    }

    public PlanoInmuebleRepositorio.Plano leer(Long inmobiliariaId, Long inmuebleId) {
        return planos.leer(inmobiliariaId, inmuebleId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Plano del inmueble", inmuebleId));
    }

    public void quitar(Long inmobiliariaId, Long inmuebleId) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        planos.borrar(inmobiliariaId, inmuebleId);
    }

    private void exigirInmueble(Long inmobiliariaId, Long inmuebleId) {
        inmuebles.findByIdAndInmobiliariaId(inmuebleId, inmobiliariaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", inmuebleId));
    }
}
