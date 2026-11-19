package co.predia.expedientes;

import co.predia.expedientes.indexacion.TipoDocumentoSugerido;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DocumentoDto(
        Long id, Long inmuebleId, Long negocioId, String nombreArchivo, long tamanoBytes, Integer paginas,
        int paginasOcr, EstadoIndexacion estadoIndexacion, String error, Instant indexadoEn, boolean vectorizado,
        TipoDocumentoSugerido tipoSugerido, LocalDate fechaExpedicionSugerida, BigDecimal confianza,
        ClasificacionEstado clasificacionEstado, TipoDocumentoSugerido tipoConfirmado,
        LocalDate fechaExpedicionConfirmada, Instant creadoEn) {

    public static DocumentoDto desde(Documento d) {
        return new DocumentoDto(d.getId(), d.getInmuebleId(), d.getNegocioId(), d.getNombreArchivo(),
                d.getTamanoBytes(), d.getPaginas(), d.getPaginasOcr(), d.getEstadoIndexacion(), d.getError(),
                d.getIndexadoEn(), d.isVectorizado(), d.getTipoSugerido(), d.getFechaExpedicionSugerida(),
                d.getConfianza(), d.getClasificacionEstado(), d.getTipoConfirmado(), d.getFechaExpedicionConfirmada(),
                d.getCreadoEn());
    }
}
