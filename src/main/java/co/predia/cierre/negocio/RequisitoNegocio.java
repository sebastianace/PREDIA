package co.predia.cierre.negocio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lo que se sabe de un requisito en un negocio. Aquí solo queda lo que hizo una persona o sugirió la IA:
 * el estado que se muestra (VENCIDO, POR_VENCER…) lo calcula MotorReglasCierre.
 */
@Entity
@Table(name = "requisito_negocio")
public class RequisitoNegocio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @Column(name = "requisito_id", nullable = false, updatable = false)
    private Long requisitoId;

    @Column(name = "documento_id")
    private Long documentoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_registrado", nullable = false)
    private EstadoRegistrado estadoRegistrado = EstadoRegistrado.PENDIENTE;

    @Column(name = "fecha_expedicion")
    private LocalDate fechaExpedicion;

    @Column(name = "sugerido_en")
    private Instant sugeridoEn;

    @Column(name = "recibido_por")
    private Long recibidoPor;

    @Column(name = "recibido_en")
    private Instant recibidoEn;

    private String nota;

    @Enumerated(EnumType.STRING)
    @Column(name = "sugerido_por")
    private OrigenSugerencia sugeridoPor;

    protected RequisitoNegocio() {
    }

    public RequisitoNegocio(Long inmobiliariaId, Long negocioId, Long requisitoId) {
        this.inmobiliariaId = inmobiliariaId;
        this.negocioId = negocioId;
        this.requisitoId = requisitoId;
    }

    /** La IA reconoció un documento: el requisito pasa a SUGERIDO. Nunca pisa lo que una persona ya decidió. */
    public boolean sugerir(Long documentoId, LocalDate fechaExpedicion) {
        if (estadoRegistrado != EstadoRegistrado.PENDIENTE && estadoRegistrado != EstadoRegistrado.SUGERIDO) {
            return false;
        }
        this.estadoRegistrado = EstadoRegistrado.SUGERIDO;
        this.documentoId = documentoId;
        this.fechaExpedicion = fechaExpedicion;
        this.sugeridoEn = Instant.now();
        this.sugeridoPor = OrigenSugerencia.IA;
        return true;
    }

    /**
     * El propietario subió el documento desde su portal: queda SUGERIDO para que el asesor lo revise. Puede reemplazar
     * un documento que ya venció o está por vencer (el que conserva la persona deja de servir), nunca uno vigente.
     */
    public void sugerirPorPropietario(Long documentoId, LocalDate fechaExpedicion) {
        this.estadoRegistrado = EstadoRegistrado.SUGERIDO;
        this.documentoId = documentoId;
        this.fechaExpedicion = fechaExpedicion;
        this.sugeridoEn = Instant.now();
        this.sugeridoPor = OrigenSugerencia.PROPIETARIO;
        this.recibidoPor = null;
        this.recibidoEn = null;
        this.nota = null;
    }

    /** Solo el asesor puede dar un requisito por recibido. */
    public void recibir(Long documentoId, LocalDate fechaExpedicion, Long asesorId, String nota) {
        this.estadoRegistrado = EstadoRegistrado.RECIBIDO;
        this.documentoId = documentoId;
        this.fechaExpedicion = fechaExpedicion;
        this.recibidoPor = asesorId;
        this.recibidoEn = Instant.now();
        this.nota = nota;
    }

    public void marcarNoAplica(Long asesorId, String nota) {
        this.estadoRegistrado = EstadoRegistrado.NO_APLICA_MANUAL;
        this.documentoId = null;
        this.fechaExpedicion = null;
        this.recibidoPor = asesorId;
        this.recibidoEn = Instant.now();
        this.nota = nota;
    }

    public void reabrir() {
        this.estadoRegistrado = EstadoRegistrado.PENDIENTE;
        this.documentoId = null;
        this.fechaExpedicion = null;
        this.sugeridoEn = null;
        this.sugeridoPor = null;
        this.recibidoPor = null;
        this.recibidoEn = null;
        this.nota = null;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getNegocioId() { return negocioId; }
    public Long getRequisitoId() { return requisitoId; }
    public Long getDocumentoId() { return documentoId; }
    public EstadoRegistrado getEstadoRegistrado() { return estadoRegistrado; }
    public LocalDate getFechaExpedicion() { return fechaExpedicion; }
    public Instant getSugeridoEn() { return sugeridoEn; }
    public Long getRecibidoPor() { return recibidoPor; }
    public Instant getRecibidoEn() { return recibidoEn; }
    public String getNota() { return nota; }
    public OrigenSugerencia getSugeridoPor() { return sugeridoPor; }
}
