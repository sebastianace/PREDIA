package co.predia.cierre.recordatorios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** Un mensaje para el propietario con lo que falta. Lo redacta la IA o la plantilla; lo envía solo el asesor. */
@Entity
@Table(name = "recordatorio")
public class Recordatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "negocio_id", nullable = false, updatable = false)
    private Long negocioId;

    @Column(name = "destinatario_email")
    private String destinatarioEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRecordatorio estado = EstadoRecordatorio.SUGERIDO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<ItemRecordatorio> items;

    @Column(nullable = false)
    private String asunto;

    @Column(name = "cuerpo_sugerido", nullable = false, updatable = false)
    private String cuerpoSugerido;

    @Column(name = "cuerpo_final")
    private String cuerpoFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_texto", nullable = false, updatable = false)
    private OrigenTexto origenTexto;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "editado_por")
    private Long editadoPor;

    @Column(name = "aprobado_por")
    private Long aprobadoPor;

    @Column(name = "aprobado_en")
    private Instant aprobadoEn;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    private String error;

    protected Recordatorio() {
    }

    public Recordatorio(Long inmobiliariaId, Long negocioId, String destinatarioEmail, List<ItemRecordatorio> items,
                        String asunto, String cuerpoSugerido, OrigenTexto origenTexto) {
        this.inmobiliariaId = inmobiliariaId;
        this.negocioId = negocioId;
        this.destinatarioEmail = destinatarioEmail;
        this.items = items;
        this.asunto = asunto;
        this.cuerpoSugerido = cuerpoSugerido;
        this.origenTexto = origenTexto;
    }

    public void editar(String asunto, String cuerpo, Long usuarioId) {
        this.asunto = asunto;
        this.cuerpoFinal = cuerpo;
        this.editadoPor = usuarioId;
    }

    public void aprobar(Long usuarioId, String destinatarioEmail) {
        this.estado = EstadoRecordatorio.APROBADO;
        this.aprobadoPor = usuarioId;
        this.aprobadoEn = Instant.now();
        this.destinatarioEmail = destinatarioEmail;
        this.error = null;
    }

    public void marcarEnviado() {
        this.estado = EstadoRecordatorio.ENVIADO;
        this.enviadoEn = Instant.now();
        this.error = null;
    }

    public void marcarErrorEnvio(String error) {
        this.estado = EstadoRecordatorio.ERROR_ENVIO;
        this.error = error;
    }

    public void descartar() {
        this.estado = EstadoRecordatorio.DESCARTADO;
    }

    /** El texto que se envía: el que editó el asesor o, si no lo tocó, el sugerido. */
    public String cuerpoVigente() {
        return cuerpoFinal != null ? cuerpoFinal : cuerpoSugerido;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getNegocioId() { return negocioId; }
    public String getDestinatarioEmail() { return destinatarioEmail; }
    public EstadoRecordatorio getEstado() { return estado; }
    public List<ItemRecordatorio> getItems() { return items; }
    public String getAsunto() { return asunto; }
    public String getCuerpoSugerido() { return cuerpoSugerido; }
    public String getCuerpoFinal() { return cuerpoFinal; }
    public OrigenTexto getOrigenTexto() { return origenTexto; }
    public Instant getCreadoEn() { return creadoEn; }
    public Long getEditadoPor() { return editadoPor; }
    public Long getAprobadoPor() { return aprobadoPor; }
    public Instant getAprobadoEn() { return aprobadoEn; }
    public Instant getEnviadoEn() { return enviadoEn; }
    public String getError() { return error; }
}
