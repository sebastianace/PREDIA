package co.predia.cierre.reglas;

import co.predia.cierre.negocio.EstadoRegistrado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** El estado documental de un negocio: semáforo, avance y el detalle de cada requisito. */
public record EstadoDocumental(
        Long negocioId, boolean listoParaFirmar, String semaforo, int faltan, int obligatoriosQueAplican,
        int recibidos, int porcentajeAvance, List<Item> items, List<String> advertencias, String aviso) {

    public static final String AVISO = "Lista de referencia; confirma los requisitos con la notaría.";

    /** Un requisito del negocio con su estado calculado (efectivo) y lo que registró el asesor. */
    public record Item(
            Long id, Long requisitoId, String codigo, String nombre, String descripcion, boolean obligatorio,
            EstadoRequisito estado, String detalle, LocalDate venceEl, EstadoRegistrado estadoRegistrado,
            LocalDate fechaExpedicion, Long documentoId, String documentoNombre, String recibidoPor,
            Instant recibidoEn, String nota, boolean requiereFecha, List<String> palabrasClave) {

        /** Lo que hay que pedirle al propietario: obligatorio y todavía no entregado (o ya no vigente). */
        public boolean faltaParaElPropietario() {
            return obligatorio && (estado == EstadoRequisito.PENDIENTE || estado == EstadoRequisito.VENCIDO
                    || estado == EstadoRequisito.POR_VENCER);
        }
    }

    public List<Item> faltantesParaElPropietario() {
        return items.stream().filter(Item::faltaParaElPropietario).toList();
    }
}
