package co.predia.expedientes.indexacion;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Divide el texto de un documento en fragmentos de 500 a 1.000 tokens, con un solapamiento de unos
 * 100 tokens entre fragmentos consecutivos, conservando página, sección y orden. No depende de Spring
 * ni de la base de datos: es lógica pura, fácil de probar.
 */
@Component
public class Fragmentador {

    static final int TOKENS_MINIMO = 500;
    static final int TOKENS_MAXIMO = 1000;
    static final int TOKENS_SOLAPE = 100;

    private static final Pattern PATRON_SEPARADOR_PARRAFOS = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern PATRON_ENCABEZADO_LEGAL =
            Pattern.compile("^(CL[AÁ]USULA|ART[IÍ]CULO|CAP[IÍ]TULO)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATRON_FRASES = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern PATRON_PALABRAS = Pattern.compile("\\s+");

    private final Encoding codificador;

    public Fragmentador() {
        EncodingRegistry registro = Encodings.newDefaultEncodingRegistry();
        // cl100k_base: el mismo vocabulario que usan text-embedding-3-small y los modelos de chat de OpenAI.
        this.codificador = registro.getEncoding(EncodingType.CL100K_BASE);
    }

    public int contarTokens(String texto) {
        return codificador.countTokens(texto);
    }

    public List<FragmentoCandidato> fragmentar(List<PaginaTexto> paginas) {
        List<Bloque> bloques = asignarSecciones(construirBloques(paginas));
        List<FragmentoCandidato> candidatos = new ArrayList<>();
        List<Bloque> buffer = new ArrayList<>();

        for (Bloque bloque : bloques) {
            for (Bloque parte : dividirSiExcedeElMaximo(bloque)) {
                int tokensParte = contarTokens(parte.texto());
                if (!buffer.isEmpty() && tokensDe(buffer) + tokensParte > TOKENS_MAXIMO) {
                    candidatos.add(cerrarFragmento(buffer, candidatos.size()));
                    List<Bloque> solape = tomarSolape(buffer);
                    // Si el solape más esta parte ya se pasarían del máximo, se prefiere perder el
                    // solape antes que violar el tamaño máximo del fragmento.
                    buffer = (!solape.isEmpty() && tokensDe(solape) + tokensParte > TOKENS_MAXIMO)
                            ? new ArrayList<>()
                            : new ArrayList<>(solape);
                }
                buffer.add(parte);
            }
        }
        if (!buffer.isEmpty()) {
            candidatos.add(cerrarFragmento(buffer, candidatos.size()));
        }
        return candidatos;
    }

    // --- Construcción de bloques (un párrafo o encabezado, con su página de origen) ---

    private List<Bloque> construirBloques(List<PaginaTexto> paginas) {
        List<Bloque> bloques = new ArrayList<>();
        for (PaginaTexto pagina : paginas) {
            if (pagina.texto() == null || pagina.texto().isBlank()) {
                continue;
            }
            for (String parrafo : PATRON_SEPARADOR_PARRAFOS.split(pagina.texto())) {
                agregarBloquesDelParrafo(bloques, pagina, parrafo.strip());
            }
        }
        return bloques;
    }

    /**
     * Un párrafo puede traer varios encabezados pegados al texto (los PDF casi nunca separan los títulos con una
     * línea en blanco). Cada encabezado se separa en su propio bloque. Una línea en mayúsculas solo cuenta como
     * título si empieza el párrafo o la línea anterior cerró una frase: así un nombre en mayúsculas en medio de una
     * oración no se confunde con un título. Los encabezados legales (CLÁUSULA, ARTÍCULO…) siempre cuentan.
     */
    private void agregarBloquesDelParrafo(List<Bloque> bloques, PaginaTexto pagina, String parrafo) {
        if (parrafo.isEmpty()) {
            return;
        }
        StringBuilder texto = new StringBuilder();
        String anterior = null;
        for (String linea : parrafo.lines().map(String::strip).filter(l -> !l.isEmpty()).toList()) {
            boolean esTitulo = esEncabezado(linea)
                    && (anterior == null || cierraUnaFrase(anterior) || PATRON_ENCABEZADO_LEGAL.matcher(linea).matches());
            if (esTitulo) {
                vaciar(bloques, pagina, texto);
                bloques.add(new Bloque(pagina.numero(), linea, pagina.esOcr(), true));
            } else {
                if (!texto.isEmpty()) {
                    texto.append('\n');
                }
                texto.append(linea);
            }
            anterior = linea;
        }
        vaciar(bloques, pagina, texto);
    }

    private static void vaciar(List<Bloque> bloques, PaginaTexto pagina, StringBuilder texto) {
        if (!texto.isEmpty()) {
            bloques.add(new Bloque(pagina.numero(), texto.toString(), pagina.esOcr(), false));
            texto.setLength(0);
        }
    }

    private static boolean cierraUnaFrase(String linea) {
        char ultimo = linea.charAt(linea.length() - 1);
        return ".:;!?)".indexOf(ultimo) >= 0;
    }

    private boolean esEncabezado(String texto) {
        if (texto.contains("\n") || texto.length() > 80) {
            return false;
        }
        if (PATRON_ENCABEZADO_LEGAL.matcher(texto).matches()) {
            return true;
        }
        long letras = texto.chars().filter(Character::isLetter).count();
        long mayusculas = texto.chars().filter(Character::isUpperCase).count();
        return letras >= 4 && mayusculas >= letras * 0.7;
    }

    // --- División de bloques demasiado largos en átomos pequeños (frases, o palabras si hace falta) ---
    // El empaquetado hasta el tamaño objetivo lo hace fragmentar(), igual que con los párrafos normales:
    // así el solapamiento funciona también dentro de un párrafo gigante.

    private List<Bloque> dividirSiExcedeElMaximo(Bloque bloque) {
        if (contarTokens(bloque.texto()) <= TOKENS_MAXIMO) {
            return List.of(bloque);
        }
        List<Bloque> atomos = new ArrayList<>();
        for (String frase : PATRON_FRASES.split(bloque.texto())) {
            if (frase.isBlank()) {
                continue;
            }
            if (contarTokens(frase) > TOKENS_MAXIMO) {
                for (String palabra : PATRON_PALABRAS.split(frase)) {
                    if (!palabra.isBlank()) {
                        atomos.add(bloque.conTexto(palabra));
                    }
                }
            } else {
                atomos.add(bloque.conTexto(frase.strip()));
            }
        }
        return atomos;
    }

    // --- Cierre de fragmentos y solapamiento ---

    private int tokensDe(List<Bloque> buffer) {
        return contarTokens(unir(buffer));
    }

    private String unir(List<Bloque> buffer) {
        return buffer.stream().map(Bloque::texto).reduce((a, b) -> a + "\n\n" + b).orElse("");
    }

    /** A cada bloque le anota la sección vigente: un encabezado abre la suya y los párrafos siguientes la heredan. */
    private List<Bloque> asignarSecciones(List<Bloque> bloques) {
        List<Bloque> anotados = new ArrayList<>(bloques.size());
        String vigente = null;
        for (Bloque bloque : bloques) {
            if (bloque.esEncabezado()) {
                vigente = bloque.texto();
            }
            anotados.add(bloque.conSeccion(vigente));
        }
        return anotados;
    }

    /**
     * La sección que se cita es la que el fragmento realmente contiene: una sola, o el rango «primera — última» si
     * cruza varias (un fragmento de 500–1.000 tokens puede abarcar varias cláusulas). Así la cita nunca señala una
     * cláusula que no está en el fragmento.
     */
    private String seccionDe(List<Bloque> buffer) {
        String primera = null;
        String ultima = null;
        for (Bloque bloque : buffer) {
            if (bloque.seccion() == null) {
                continue;
            }
            if (primera == null) {
                primera = bloque.seccion();
            }
            ultima = bloque.seccion();
        }
        if (primera == null || primera.equals(ultima)) {
            return primera;
        }
        // Un encabezado mide a lo sumo 80 caracteres, así que el rango cabe en la columna (VARCHAR(200)).
        return primera + " — " + ultima;
    }

    private FragmentoCandidato cerrarFragmento(List<Bloque> buffer, int orden) {
        String seccion = seccionDe(buffer);
        String texto = unir(buffer);
        int paginaInicio = buffer.stream().mapToInt(Bloque::pagina).min().orElseThrow();
        int paginaFin = buffer.stream().mapToInt(Bloque::pagina).max().orElseThrow();
        boolean esOcr = buffer.stream().anyMatch(Bloque::esOcr);
        return new FragmentoCandidato(orden, paginaInicio, paginaFin, seccion, texto, contarTokens(texto), esOcr);
    }

    /** Toma los últimos bloques del fragmento que se cierra hasta sumar ~100 tokens, para el siguiente. */
    private List<Bloque> tomarSolape(List<Bloque> buffer) {
        List<Bloque> cola = new ArrayList<>();
        int tokens = 0;
        for (int i = buffer.size() - 1; i >= 0 && tokens < TOKENS_SOLAPE; i--) {
            Bloque bloque = buffer.get(i);
            cola.add(0, bloque);
            tokens += contarTokens(bloque.texto());
        }
        return cola;
    }

    private record Bloque(int pagina, String texto, boolean esOcr, boolean esEncabezado, String seccion) {
        Bloque(int pagina, String texto, boolean esOcr, boolean esEncabezado) {
            this(pagina, texto, esOcr, esEncabezado, null);
        }

        Bloque conTexto(String nuevoTexto) {
            return new Bloque(pagina, nuevoTexto, esOcr, false, seccion);
        }

        Bloque conSeccion(String nuevaSeccion) {
            return new Bloque(pagina, texto, esOcr, esEncabezado, nuevaSeccion);
        }
    }
}
