package co.predia.cierre.negocio;

import co.predia.cierre.catalogo.RequisitoCatalogo;
import co.predia.cierre.catalogo.RequisitoCatalogoRepository;
import co.predia.expedientes.DocumentoClasificado;
import co.predia.expedientes.indexacion.TipoDocumentoSugerido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cuando se indexa un documento de un negocio y la IA reconoce su tipo y su fecha, el requisito que corresponde
 * pasa a SUGERIDO. Nunca a RECIBIDO: "la IA sugiere, las reglas deciden y la persona confirma".
 */
@Component
public class SugerenciaRequisitos {

    private static final Logger log = LoggerFactory.getLogger(SugerenciaRequisitos.class);

    private final RequisitoCatalogoRepository catalogo;
    private final RequisitoNegocioRepository requisitosNegocio;

    public SugerenciaRequisitos(RequisitoCatalogoRepository catalogo, RequisitoNegocioRepository requisitosNegocio) {
        this.catalogo = catalogo;
        this.requisitosNegocio = requisitosNegocio;
    }

    @EventListener
    public void alClasificar(DocumentoClasificado e) {
        if (e.negocioId() == null || e.tipo() == null) {
            return;
        }
        try {
            Map<Long, RequisitoCatalogo> delCatalogo = catalogo
                    .findByInmobiliariaIdOrderByOrdenAscIdAsc(e.inmobiliariaId()).stream()
                    .collect(Collectors.toMap(RequisitoCatalogo::getId, r -> r));
            List<RequisitoNegocio> filas = requisitosNegocio.findByInmobiliariaIdAndNegocioId(e.inmobiliariaId(),
                    e.negocioId());

            for (RequisitoNegocio fila : filas) {
                RequisitoCatalogo requisito = delCatalogo.get(fila.getRequisitoId());
                if (requisito == null || fila.getSugeridoPor() == OrigenSugerencia.PROPIETARIO) {
                    // Lo que eligió el propietario no lo cambia la IA: lo revisa el asesor.
                    continue;
                }
                boolean coincide = requisito.getTipoDocumento() == e.tipo() && e.tipo() != TipoDocumentoSugerido.OTRO;
                if (coincide) {
                    if (fila.sugerir(e.documentoId(), e.fechaExpedicion())) {
                        requisitosNegocio.save(fila);
                    }
                } else if (fila.getEstadoRegistrado() == EstadoRegistrado.SUGERIDO
                        && e.documentoId().equals(fila.getDocumentoId())) {
                    // El asesor corrigió el tipo: la sugerencia anterior de este mismo documento ya no vale.
                    fila.reabrir();
                    requisitosNegocio.save(fila);
                }
            }
        } catch (Exception ex) {
            // Sugerir es un extra: si falla, el documento queda indexado igual.
            log.warn("No se pudo sugerir el requisito del documento {}: {}", e.documentoId(), ex.getMessage());
        }
    }
}
