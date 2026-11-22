package co.predia.asistente;

import co.predia.cierre.negocio.EstadoNegocio;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Convierte lo que dice una persona o el modelo ("Paola y Juan", "el apto 1102 de Cañaveral", "7") en la
 * entidad real. Siempre busca dentro de la inmobiliaria del usuario: un id o un nombre de otra inmobiliaria
 * responde "no encontré", igual que uno que no existe. Si hay varias coincidencias no adivina: lo dice.
 */
@Component
public class ResolvedorEntidades {

    private final ClienteRepository clientes;
    private final InmuebleRepository inmuebles;
    private final NegocioRepository negocios;
    private final PropietarioRepository propietarios;

    public ResolvedorEntidades(ClienteRepository clientes, InmuebleRepository inmuebles, NegocioRepository negocios,
                               PropietarioRepository propietarios) {
        this.clientes = clientes;
        this.inmuebles = inmuebles;
        this.negocios = negocios;
        this.propietarios = propietarios;
    }

    @Transactional(readOnly = true)
    public Cliente cliente(Long tenant, String referencia) {
        if (Texto.esNumero(referencia)) {
            var porId = clientes.findByIdAndInmobiliariaId(Long.valueOf(referencia.strip()), tenant);
            if (porId.isPresent()) {
                return porId.get();
            }
        }
        return elegir("cliente", referencia, clientes.findByInmobiliariaIdOrderByNombreAsc(tenant),
                Cliente::getNombre, c -> "%s (id %d)".formatted(c.getNombre(), c.getId()));
    }

    @Transactional(readOnly = true)
    public Inmueble inmueble(Long tenant, String referencia) {
        if (Texto.esNumero(referencia)) {
            var porId = inmuebles.findByIdAndInmobiliariaId(Long.valueOf(referencia.strip()), tenant);
            if (porId.isPresent()) {
                return porId.get();
            }
        }
        return elegir("inmueble", referencia, inmuebles.findByInmobiliariaIdOrderByTituloAsc(tenant),
                i -> String.join(" ", orVacio(i.getCodigo()), orVacio(i.getTitulo()), orVacio(i.getDireccion()),
                        orVacio(i.getBarrio()), orVacio(i.getCiudad())),
                i -> "%s (id %d)".formatted(i.getTitulo(), i.getId()));
    }

    /** Un negocio por su id, o por el nombre del inmueble (o del propietario); entre varios, prefiere el abierto. */
    @Transactional(readOnly = true)
    public Negocio negocio(Long tenant, String referencia) {
        if (Texto.esNumero(referencia)) {
            var porId = negocios.findByIdAndInmobiliariaId(Long.valueOf(referencia.strip()), tenant);
            if (porId.isPresent()) {
                return porId.get();
            }
        }
        Map<Long, Inmueble> deInmuebles = inmuebles.findByInmobiliariaIdOrderByTituloAsc(tenant).stream()
                .collect(Collectors.toMap(Inmueble::getId, Function.identity()));
        Map<Long, Propietario> dePropietarios = propietarios.findByInmobiliariaIdOrderByNombreAsc(tenant).stream()
                .collect(Collectors.toMap(Propietario::getId, Function.identity()));
        List<Negocio> todos = negocios.findByInmobiliariaIdOrderByCreadoEnDesc(tenant);

        Function<Negocio, String> haystack = n -> {
            Inmueble i = deInmuebles.get(n.getInmuebleId());
            Propietario p = dePropietarios.get(n.getPropietarioId());
            return (i == null ? "" : String.join(" ", orVacio(i.getCodigo()), orVacio(i.getTitulo()),
                    orVacio(i.getDireccion()), orVacio(i.getBarrio()), orVacio(i.getCiudad())))
                    + " " + (p == null ? "" : orVacio(p.getNombre()));
        };
        Function<Negocio, String> etiqueta = n -> {
            Inmueble i = deInmuebles.get(n.getInmuebleId());
            return "negocio %d (%s, %s)".formatted(n.getId(), i == null ? "sin inmueble" : i.getTitulo(),
                    n.getEstado().name().toLowerCase());
        };
        try {
            return elegir("negocio", referencia, todos, haystack, etiqueta);
        } catch (ReferenciaException ambiguo) {
            // Si de varios solo uno sigue abierto, ese es el que se está cerrando.
            List<Negocio> abiertos = coincidencias(referencia, todos, haystack).stream()
                    .filter(n -> n.getEstado() == EstadoNegocio.ABIERTO).toList();
            if (abiertos.size() == 1) {
                return abiertos.get(0);
            }
            throw ambiguo;
        }
    }

    private static <T> T elegir(String tipo, String referencia, List<T> candidatos, Function<T, String> texto,
                                Function<T, String> etiqueta) {
        if (Texto.palabrasClave(referencia).isEmpty()) {
            throw new ReferenciaException("Indica de qué %s hablas (su nombre o su id).".formatted(tipo));
        }
        List<T> coinciden = coincidencias(referencia, candidatos, texto);
        if (coinciden.size() == 1) {
            return coinciden.get(0);
        }
        if (coinciden.isEmpty()) {
            throw new ReferenciaException("No encontré ningún %s que coincida con «%s» en esta inmobiliaria."
                    .formatted(tipo, referencia.strip()));
        }
        String exacto = Texto.normalizar(referencia);
        List<T> iguales = coinciden.stream().filter(c -> Texto.normalizar(texto.apply(c)).equals(exacto)).toList();
        if (iguales.size() == 1) {
            return iguales.get(0);
        }
        throw new ReferenciaException("Hay varios %ss que coinciden con «%s»: %s. Pregúntale al asesor cuál es."
                .formatted(tipo, referencia.strip(),
                        coinciden.stream().limit(5).map(etiqueta).collect(Collectors.joining("; "))));
    }

    /** Candidatos cuyo texto contiene todas las palabras con contenido de la referencia. */
    private static <T> List<T> coincidencias(String referencia, List<T> candidatos, Function<T, String> texto) {
        List<String> palabras = Texto.palabrasClave(referencia);
        return candidatos.stream().filter(c -> {
            String normal = Texto.normalizar(texto.apply(c));
            return palabras.stream().allMatch(normal::contains);
        }).toList();
    }

    private static String orVacio(String valor) {
        return valor == null ? "" : valor;
    }
}
