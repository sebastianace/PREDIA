package co.predia.expedientes.indexacion;

import co.predia.expedientes.Documento;
import co.predia.expedientes.DocumentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Las tres transacciones cortas del procesador de indexación: tomar un trabajo de la cola, guardar
 * el resultado y registrar un fallo. Van en un bean aparte para que @Transactional funcione: Spring
 * no aplica transacciones a llamadas internas (this.metodo()) dentro de la misma clase.
 */
@Service
public class TransaccionesIndexacion {

    private final TrabajoIndexacionRepository trabajos;
    private final DocumentoRepository documentos;
    private final FragmentoRepositorioJdbc fragmentos;

    public TransaccionesIndexacion(TrabajoIndexacionRepository trabajos, DocumentoRepository documentos,
                                   FragmentoRepositorioJdbc fragmentos) {
        this.trabajos = trabajos;
        this.documentos = documentos;
        this.fragmentos = fragmentos;
    }

    @Transactional
    public Optional<TrabajoClaim> tomarSiguiente() {
        return trabajos
                .findFirstByEstadoAndProximoIntentoLessThanEqualOrderByProximoIntentoAsc(EstadoTrabajo.PENDIENTE,
                        Instant.now())
                .map(trabajo -> {
                    trabajo.marcarEnCurso();
                    Documento documento = documentos.findById(trabajo.getDocumentoId())
                            .orElseThrow(() -> new IllegalStateException("El documento del trabajo ya no existe."));
                    documento.marcarProcesando();
                    return new TrabajoClaim(trabajo.getId(), trabajo.getInmobiliariaId(), documento.getId(),
                            documento.getInmuebleId(), trabajo.getRutaTemporal());
                });
    }

    @Transactional
    public void guardarResultado(TrabajoClaim claim, List<FragmentoCandidato> candidatos, List<float[]> embeddings,
                                 String modeloEmbeddings, int paginas, int paginasOcr,
                                 Optional<ClasificacionSugerida> sugerencia) {
        // Por si es un reintento que ya había guardado fragmentos de un intento anterior incompleto.
        fragmentos.borrarPorDocumento(claim.documentoId());
        if (!candidatos.isEmpty()) {
            fragmentos.guardarTodos(claim.inmobiliariaId(), claim.documentoId(), claim.inmuebleId(), candidatos,
                    embeddings, modeloEmbeddings);
        }
        int guardados = fragmentos.contarPorDocumento(claim.documentoId());
        if (guardados != candidatos.size()) {
            throw new IllegalStateException(
                    "Se esperaban %d fragmentos y se guardaron %d.".formatted(candidatos.size(), guardados));
        }

        Documento documento = documentos.findById(claim.documentoId())
                .orElseThrow(() -> new IllegalStateException("El documento ya no existe."));
        documento.marcarIndexado(paginas, paginasOcr, embeddings != null);
        sugerencia.ifPresent(s -> documento.registrarSugerencia(s.tipoDocumento(), parsearFecha(s.fechaExpedicion()),
                confianzaAcotada(s.confianza())));

        TrabajoIndexacion trabajo = trabajos.findById(claim.id())
                .orElseThrow(() -> new IllegalStateException("El trabajo ya no existe."));
        trabajo.marcarTerminado();
    }

    /** Devuelve true si el fallo ya es definitivo (se agotaron los reintentos). */
    @Transactional
    public boolean registrarFallo(TrabajoClaim claim, String mensaje) {
        TrabajoIndexacion trabajo = trabajos.findById(claim.id()).orElse(null);
        if (trabajo == null) {
            return true;
        }
        boolean definitivo = trabajo.registrarFallo(mensaje);
        if (definitivo) {
            documentos.findById(claim.documentoId()).ifPresent(d -> d.marcarError(mensaje));
        }
        return definitivo;
    }

    private static LocalDate parsearFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(fecha.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal confianzaAcotada(double confianza) {
        double acotada = Math.max(0, Math.min(1, confianza));
        return BigDecimal.valueOf(acotada).setScale(3, RoundingMode.HALF_UP);
    }
}
