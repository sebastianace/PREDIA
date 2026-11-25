package co.predia.expedientes.carga;

import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.expedientes.Documento;
import co.predia.expedientes.DocumentoClasificado;
import co.predia.expedientes.DocumentoDto;
import co.predia.expedientes.DocumentoRepository;
import co.predia.expedientes.EstadoIndexacion;
import co.predia.expedientes.indexacion.ExtractorPdf;
import co.predia.expedientes.indexacion.TipoDocumentoSugerido;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.inventario.InmuebleRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Recibe el PDF, lo valida y lo guarda SOLO de forma temporal: aquí no queda nada más que su hash
 * y sus metadatos. El contenido se procesa después, de forma asíncrona (ver ProcesadorIndexacion).
 */
@Service
public class ServicioCargaDocumento {

    private static final Logger log = LoggerFactory.getLogger(ServicioCargaDocumento.class);

    private static final Set<String> TIPOS_CONTENIDO_VALIDOS = Set.of("application/pdf", "application/x-pdf");

    private final PropiedadesExpedientes propiedades;
    private final DocumentoRepository documentos;
    private final InmuebleRepository inmuebles;
    private final ExtractorPdf extractorPdf;
    private final RegistroDocumentoTrabajo registro;
    private final NegocioRepository negocios;
    private final ApplicationEventPublisher eventos;
    private final EscanerArchivos escaner;
    private final InspectorPdf inspector;

    public ServicioCargaDocumento(PropiedadesExpedientes propiedades, DocumentoRepository documentos,
                                  InmuebleRepository inmuebles, ExtractorPdf extractorPdf,
                                  RegistroDocumentoTrabajo registro, NegocioRepository negocios,
                                  ApplicationEventPublisher eventos, EscanerArchivos escaner,
                                  InspectorPdf inspector) {
        this.escaner = escaner;
        this.inspector = inspector;
        this.negocios = negocios;
        this.eventos = eventos;
        this.propiedades = propiedades;
        this.documentos = documentos;
        this.inmuebles = inmuebles;
        this.extractorPdf = extractorPdf;
        this.registro = registro;
    }

    public ResultadoCarga cargar(MultipartFile archivo, Long inmuebleId, Long negocioId, UsuarioActual usuario) {
        validarTipoDeclarado(archivo);
        inmuebles.findByIdAndInmobiliariaId(inmuebleId, usuario.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", inmuebleId));
        if (negocioId != null) {
            // Un documento solo se asocia a un negocio de la propia inmobiliaria y de ese mismo inmueble.
            negocios.findByIdAndInmobiliariaId(negocioId, usuario.inmobiliariaId())
                    .filter(n -> n.getInmuebleId().equals(inmuebleId))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Negocio", negocioId));
        }

        ArchivoGuardado guardado = guardarTemporalConHash(archivo);
        try {
            // Primero el antivirus, antes de que PDFBox abra el archivo; después la revisión del propio PDF.
            escanearAntivirus(guardado);
            validarEsPdfLegible(guardado.ruta());

            Optional<Documento> existente =
                    documentos.findByInmobiliariaIdAndHashSha256(usuario.inmobiliariaId(), guardado.hash());
            if (existente.isPresent()) {
                // Ya se había subido antes: no se duplica el trabajo de indexación.
                borrarSilenciosamente(guardado.ruta());
                return new ResultadoCarga(DocumentoDto.desde(existente.get()), true);
            }

            DocumentoDto nuevo = registro.registrar(inmuebleId, negocioId,
                    nombreSanitizado(archivo.getOriginalFilename()), guardado.hash(), guardado.tamanoBytes(),
                    guardado.ruta().toString(), usuario);
            return new ResultadoCarga(nuevo, false);
        } catch (RuntimeException e) {
            borrarSilenciosamente(guardado.ruta());
            throw e;
        }
    }

    /** {@code yaExistia}: true si el documento ya se había subido (mismo hash) y no se creó un trabajo nuevo. */
    public record ResultadoCarga(DocumentoDto documento, boolean yaExistia) {
    }

    /** El asesor confirma (o corrige) lo que la IA sugirió al clasificar el documento. */
    @Transactional
    public DocumentoDto confirmarClasificacion(Long documentoId, TipoDocumentoSugerido tipo,
                                               LocalDate fechaExpedicion, UsuarioActual usuario) {
        Documento documento = documentos.findByIdAndInmobiliariaId(documentoId, usuario.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento", documentoId));
        if (documento.getEstadoIndexacion() != EstadoIndexacion.INDEXADO) {
            throw new ReglaNegocioException("Solo se puede clasificar un documento ya indexado.");
        }
        documento.confirmarClasificacion(tipo, fechaExpedicion, usuario.id());
        // Si el asesor corrigió el tipo, la sugerencia del requisito se ajusta (sigue siendo solo una sugerencia).
        eventos.publishEvent(new DocumentoClasificado(documento.getId(), documento.getInmobiliariaId(),
                documento.getNegocioId(), tipo, fechaExpedicion));
        return DocumentoDto.desde(documento);
    }

    private void validarTipoDeclarado(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException("Debes adjuntar un archivo.");
        }
        if (archivo.getSize() > propiedades.tamanoMaximoBytes()) {
            throw new ReglaNegocioException(
                    "El archivo supera el tamaño máximo permitido (%d MB).".formatted(propiedades.tamanoMaximoMb()));
        }
        String nombre = archivo.getOriginalFilename();
        boolean extensionValida = nombre != null && nombre.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean tipoValido = archivo.getContentType() != null
                && TIPOS_CONTENIDO_VALIDOS.contains(archivo.getContentType().toLowerCase(Locale.ROOT));
        if (!extensionValida && !tipoValido) {
            throw new ReglaNegocioException("Solo se aceptan archivos PDF.");
        }
    }

    /** Un archivo con una amenaza no se acepta; en el log solo queda su hash y el nombre de la amenaza. */
    private void escanearAntivirus(ArchivoGuardado guardado) {
        EscanerArchivos.Resultado resultado = escaner.escanear(guardado.ruta());
        if (resultado.estaInfectado()) {
            log.warn("Archivo rechazado por el antivirus: sha256={} amenaza={}", guardado.hash(), resultado.amenaza());
            throw new ReglaNegocioException("El archivo fue rechazado por seguridad: se detectó contenido malicioso.");
        }
        if (!resultado.verificado()) {
            log.debug("Archivo aceptado sin escaneo antivirus: sha256={}", guardado.hash());
        }
    }

    private void validarEsPdfLegible(Path ruta) {
        try {
            if (!inspector.tieneFirmaPdf(ruta)) {
                throw new ReglaNegocioException("El archivo no es un PDF válido.");
            }
        } catch (IOException e) {
            throw new ReglaNegocioException("El archivo no es un PDF válido.");
        }
        try (PDDocument documento = extractorPdf.abrir(ruta.toFile())) {
            if (extractorPdf.esEncriptado(documento)) {
                throw new ReglaNegocioException("El PDF está protegido con contraseña; súbelo sin cifrar.");
            }
            if (extractorPdf.contarPaginas(documento) == 0) {
                throw new ReglaNegocioException("El PDF no tiene páginas.");
            }
            inspector.motivoDeRechazo(documento).ifPresent(motivo -> {
                throw new ReglaNegocioException("El archivo fue rechazado por seguridad. " + motivo);
            });
        } catch (IOException e) {
            throw new ReglaNegocioException("El archivo no es un PDF válido.");
        }
    }

    private ArchivoGuardado guardarTemporalConHash(MultipartFile archivo) {
        try {
            Path directorio = Path.of(propiedades.directorioTemporal());
            Files.createDirectories(directorio);
            Path destino = directorio.resolve(UUID.randomUUID() + ".pdf");

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream origen = archivo.getInputStream();
                 DigestInputStream conHash = new DigestInputStream(origen, sha256)) {
                Files.copy(conHash, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            String hashHex = HexFormat.of().formatHex(sha256.digest());
            return new ArchivoGuardado(destino, hashHex, Files.size(destino));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ReglaNegocioException("No se pudo guardar el archivo: " + e.getMessage());
        }
    }

    private static void borrarSilenciosamente(Path ruta) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException ignorado) {
            // No es crítico: el barrido al arrancar (LimpiadorArranque) se encarga de los huérfanos.
        }
    }

    private static String nombreSanitizado(String original) {
        String base = original == null ? "documento.pdf" : Path.of(original).getFileName().toString();
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private record ArchivoGuardado(Path ruta, String hash, long tamanoBytes) {
    }
}
