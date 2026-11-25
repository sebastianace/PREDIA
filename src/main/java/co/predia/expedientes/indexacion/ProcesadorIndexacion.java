package co.predia.expedientes.indexacion;

import co.predia.comun.ia.EstadoIa;
import co.predia.comun.ia.ProveedorEmbeddings;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Procesa la cola de indexación: extrae el texto (con OCR donde haga falta), lo fragmenta, genera
 * los embeddings si hay IA disponible, clasifica el documento y borra el PDF temporal al terminar
 * (o al fallar definitivamente, tras 3 intentos). Sin brokers externos: toma trabajos con un
 * bloqueo "skip locked" sobre la propia tabla.
 */
@Component
public class ProcesadorIndexacion {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorIndexacion.class);
    private static final int TRABAJOS_POR_CICLO = 5;
    private static final int TAMANO_LOTE_EMBEDDINGS = 64;

    private final TransaccionesIndexacion transacciones;
    private final ExtractorPdf extractorPdf;
    private final ServicioOcr servicioOcr;
    private final PropiedadesOcr propiedadesOcr;
    private final Fragmentador fragmentador;
    private final ProveedorEmbeddings proveedorEmbeddings;
    private final ClasificadorDocumento clasificadorDocumento;
    private final EstadoIa estadoIa;

    public ProcesadorIndexacion(TransaccionesIndexacion transacciones, ExtractorPdf extractorPdf,
                                ServicioOcr servicioOcr, PropiedadesOcr propiedadesOcr, Fragmentador fragmentador,
                                ProveedorEmbeddings proveedorEmbeddings, ClasificadorDocumento clasificadorDocumento,
                                EstadoIa estadoIa) {
        this.transacciones = transacciones;
        this.extractorPdf = extractorPdf;
        this.servicioOcr = servicioOcr;
        this.propiedadesOcr = propiedadesOcr;
        this.fragmentador = fragmentador;
        this.proveedorEmbeddings = proveedorEmbeddings;
        this.clasificadorDocumento = clasificadorDocumento;
        this.estadoIa = estadoIa;
    }

    @Scheduled(fixedDelayString = "${predia.expedientes.intervalo-indexacion-ms:5000}",
            initialDelayString = "${predia.expedientes.espera-inicial-indexacion-ms:3000}")
    public void procesarPendientes() {
        for (int i = 0; i < TRABAJOS_POR_CICLO; i++) {
            Optional<TrabajoClaim> claim = transacciones.tomarSiguiente();
            if (claim.isEmpty()) {
                return;
            }
            procesarUno(claim.get());
        }
    }

    private void procesarUno(TrabajoClaim claim) {
        File archivo = new File(claim.rutaTemporal());
        try {
            List<PaginaTexto> paginas = leerPaginas(archivo);
            List<FragmentoCandidato> candidatos = fragmentador.fragmentar(paginas);
            List<float[]> embeddings = null;
            String modeloEmbeddings = null;
            if (estadoIa.disponible() && !candidatos.isEmpty()) {
                embeddings = generarEmbeddingsEnLotes(candidatos);
                modeloEmbeddings = proveedorEmbeddings.modelo();
            }
            Optional<ClasificacionSugerida> sugerencia = clasificar(candidatos);
            int paginasOcr = (int) paginas.stream().filter(PaginaTexto::esOcr).count();

            transacciones.guardarResultado(claim, candidatos, embeddings, modeloEmbeddings, paginas.size(),
                    paginasOcr, sugerencia);
            borrarSiExiste(archivo);
            log.info("Documento {} indexado: {} fragmentos, {} páginas ({} con OCR).", claim.documentoId(),
                    candidatos.size(), paginas.size(), paginasOcr);
        } catch (Exception e) {
            log.warn("Falló el intento de indexar el documento {}: {}", claim.documentoId(), e.toString());
            boolean definitivo = transacciones.registrarFallo(claim, mensajeCorto(e));
            if (definitivo) {
                log.error("Se agotaron los intentos para el documento {}.", claim.documentoId());
                borrarSiExiste(archivo);
            }
        }
    }

    private List<PaginaTexto> leerPaginas(File archivo) throws IOException {
        try (PDDocument documento = extractorPdf.abrir(archivo)) {
            if (extractorPdf.esEncriptado(documento)) {
                throw new IllegalArgumentException("El PDF está cifrado y no se puede leer.");
            }
            int totalPaginas = extractorPdf.contarPaginas(documento);
            List<PaginaTexto> paginas = new ArrayList<>(totalPaginas);
            for (int numero = 1; numero <= totalPaginas; numero++) {
                String texto = extractorPdf.extraerTexto(documento, numero);
                if (extractorPdf.tieneTextoUtil(texto)) {
                    paginas.add(new PaginaTexto(numero, texto, false));
                } else {
                    BufferedImage imagen = extractorPdf.renderizar(documento, numero, propiedadesOcr.dpi());
                    paginas.add(new PaginaTexto(numero, servicioOcr.reconocer(imagen), true));
                }
            }
            return paginas;
        }
    }

    private List<float[]> generarEmbeddingsEnLotes(List<FragmentoCandidato> candidatos) {
        List<String> textos = candidatos.stream().map(FragmentoCandidato::texto).toList();
        List<float[]> resultado = new ArrayList<>(textos.size());
        for (int i = 0; i < textos.size(); i += TAMANO_LOTE_EMBEDDINGS) {
            List<String> lote = textos.subList(i, Math.min(i + TAMANO_LOTE_EMBEDDINGS, textos.size()));
            resultado.addAll(proveedorEmbeddings.generar(lote));
        }
        return resultado;
    }

    private Optional<ClasificacionSugerida> clasificar(List<FragmentoCandidato> candidatos) {
        List<String> primerosFragmentos = candidatos.stream().limit(3).map(FragmentoCandidato::texto).toList();
        return clasificadorDocumento.clasificar(primerosFragmentos);
    }

    private static void borrarSiExiste(File archivo) {
        try {
            Files.deleteIfExists(archivo.toPath());
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo temporal {}: {}", archivo, e.getMessage());
        }
    }

    private static String mensajeCorto(Exception e) {
        String mensaje = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje;
    }
}
