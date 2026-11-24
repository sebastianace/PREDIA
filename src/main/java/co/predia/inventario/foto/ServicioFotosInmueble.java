package co.predia.inventario.foto;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;

/** La foto de portada de un inmueble (la primera foto de su galería). El inmueble siempre se busca dentro de la inmobiliaria. */
@Service
public class ServicioFotosInmueble {

    private final InmuebleRepository inmuebles;
    private final FotoInmuebleRepositorio fotos;

    public ServicioFotosInmueble(InmuebleRepository inmuebles, FotoInmuebleRepositorio fotos) {
        this.inmuebles = inmuebles;
        this.fotos = fotos;
    }

    public FotoInmuebleRepositorio.Bytes leer(Long inmobiliariaId, Long inmuebleId, boolean miniatura) {
        inmuebles.findByIdAndInmobiliariaId(inmuebleId, inmobiliariaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", inmuebleId));
        return fotos.leer(inmobiliariaId, inmuebleId, miniatura)
                .orElseThrow(() -> new RecursoNoEncontradoException("Foto del inmueble", inmuebleId));
    }
}
