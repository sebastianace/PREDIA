package co.predia.inventario.foto;

import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * La galería de un inmueble: fotos y planos en un solo orden. El inmueble siempre se busca dentro de la inmobiliaria y
 * cada imagen dentro de su inmueble, así que un id ajeno responde 404 igual que uno inexistente.
 */
@Service
public class ServicioImagenesInmueble {

    /** Lo que sube el navegador ya viene reducido; esto solo frena archivos absurdos. */
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    /** Una ficha rara vez necesita más: así la galería no crece sin control en la base de datos. */
    public static final int MAX_IMAGENES = 12;

    private final InmuebleRepository inmuebles;
    private final ImagenInmuebleRepositorio imagenes;

    public ServicioImagenesInmueble(InmuebleRepository inmuebles, ImagenInmuebleRepositorio imagenes) {
        this.inmuebles = inmuebles;
        this.imagenes = imagenes;
    }

    @Transactional(readOnly = true)
    public List<ImagenInfo> listar(Long inmobiliariaId, Long inmuebleId) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        return imagenes.listar(inmobiliariaId, inmuebleId);
    }

    @Transactional
    public ImagenInfo agregar(Long inmobiliariaId, Long usuarioId, Long inmuebleId, byte[] original, TipoImagen tipo,
                              String etiqueta, int focoX, int focoY) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        if (original == null || original.length == 0) {
            throw new ReglaNegocioException("Elige una imagen para subir.");
        }
        if (original.length > MAX_BYTES) {
            throw new ReglaNegocioException("La imagen pesa más de 8 MB. Elige una más liviana.");
        }
        if (imagenes.contar(inmobiliariaId, inmuebleId) >= MAX_IMAGENES) {
            throw new ReglaNegocioException("La galería ya tiene " + MAX_IMAGENES + " imágenes. Quita alguna para agregar otra.");
        }
        ProcesadorImagen.Imagen imagen = tipo == TipoImagen.PLANO
                ? ProcesadorImagen.procesarPlano(original) : ProcesadorImagen.procesar(original);
        Long id = imagenes.agregar(inmobiliariaId, inmuebleId, usuarioId, tipo, limpiar(etiqueta), imagen,
                limitar(focoX), limitar(focoY));
        return imagenes.obtener(inmobiliariaId, inmuebleId, id).orElseThrow();
    }

    /** Cambia el tipo, la etiqueta o el encuadre; lo que llegue nulo se queda como estaba. */
    @Transactional
    public ImagenInfo editar(Long inmobiliariaId, Long inmuebleId, Long imagenId, TipoImagen tipo, String etiqueta,
                             Integer focoX, Integer focoY) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        ImagenInfo actual = imagenes.obtener(inmobiliariaId, inmuebleId, imagenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen del inmueble", imagenId));
        imagenes.actualizar(inmobiliariaId, inmuebleId, imagenId, tipo == null ? actual.tipo() : tipo,
                etiqueta == null ? actual.etiqueta() : limpiar(etiqueta),
                limitar(focoX == null ? actual.focoX() : focoX), limitar(focoY == null ? actual.focoY() : focoY));
        return imagenes.obtener(inmobiliariaId, inmuebleId, imagenId).orElseThrow();
    }

    /** El nuevo orden debe nombrar exactamente las imágenes que tiene el inmueble, cada una una vez. */
    @Transactional
    public List<ImagenInfo> ordenar(Long inmobiliariaId, Long inmuebleId, List<Long> ids) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        List<ImagenInfo> actuales = imagenes.listar(inmobiliariaId, inmuebleId);
        var existentes = new HashSet<Long>();
        actuales.forEach(i -> existentes.add(i.id()));
        if (ids == null || ids.size() != actuales.size() || !existentes.equals(new HashSet<>(ids))) {
            throw new ReglaNegocioException("El orden debe incluir cada imagen del inmueble, una sola vez.");
        }
        imagenes.ordenar(inmobiliariaId, inmuebleId, ids);
        return imagenes.listar(inmobiliariaId, inmuebleId);
    }

    @Transactional
    public void quitar(Long inmobiliariaId, Long inmuebleId, Long imagenId) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        if (!imagenes.borrar(inmobiliariaId, inmuebleId, imagenId)) {
            throw new RecursoNoEncontradoException("Imagen del inmueble", imagenId);
        }
    }

    @Transactional(readOnly = true)
    public ImagenInmuebleRepositorio.Bytes leer(Long inmobiliariaId, Long inmuebleId, Long imagenId, boolean miniatura) {
        exigirInmueble(inmobiliariaId, inmuebleId);
        return imagenes.leer(inmobiliariaId, inmuebleId, imagenId, miniatura)
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen del inmueble", imagenId));
    }

    private void exigirInmueble(Long inmobiliariaId, Long inmuebleId) {
        inmuebles.findByIdAndInmobiliariaId(inmuebleId, inmobiliariaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", inmuebleId));
    }

    private static String limpiar(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return null;
        }
        String t = etiqueta.strip();
        return t.length() > 60 ? t.substring(0, 60) : t;
    }

    private static int limitar(int foco) {
        return Math.max(0, Math.min(100, foco));
    }
}
