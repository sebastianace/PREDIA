package co.predia.expedientes.consulta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** Trazabilidad: qué se preguntó, con qué evidencia se respondió y si hubo que decir "no lo sé". */
@Entity
@Table(name = "consulta_rag")
public class ConsultaRag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "inmueble_id")
    private Long inmuebleId;

    @Column(nullable = false, updatable = false)
    private String pregunta;

    private String respuesta;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "fragmentos_ids", columnDefinition = "bigint[]")
    private List<Long> fragmentosIds;

    @Column(name = "sin_evidencia", nullable = false)
    private boolean sinEvidencia;

    @Column(name = "modo_manual", nullable = false)
    private boolean modoManual;

    @Column(name = "modelo_chat")
    private String modeloChat;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    protected ConsultaRag() {
    }

    public ConsultaRag(Long inmobiliariaId, Long usuarioId, Long inmuebleId, String pregunta, String respuesta,
                       List<Long> fragmentosIds, boolean sinEvidencia, boolean modoManual, String modeloChat) {
        this.inmobiliariaId = inmobiliariaId;
        this.usuarioId = usuarioId;
        this.inmuebleId = inmuebleId;
        this.pregunta = pregunta;
        this.respuesta = respuesta;
        this.fragmentosIds = fragmentosIds;
        this.sinEvidencia = sinEvidencia;
        this.modoManual = modoManual;
        this.modeloChat = modeloChat;
    }

    public Long getId() { return id; }
}
