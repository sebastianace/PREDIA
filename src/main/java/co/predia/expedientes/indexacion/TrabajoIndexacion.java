package co.predia.expedientes.indexacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Cola de indexación: el procesador toma trabajos PENDIENTE con un bloqueo "skip locked". */
@Entity
@Table(name = "trabajo_indexacion")
public class TrabajoIndexacion {

    private static final int MAX_INTENTOS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "documento_id", nullable = false, updatable = false)
    private Long documentoId;

    @Column(name = "ruta_temporal", nullable = false)
    private String rutaTemporal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTrabajo estado = EstadoTrabajo.PENDIENTE;

    @Column(nullable = false)
    private int intentos = 0;

    @Column(name = "proximo_intento", nullable = false)
    private Instant proximoIntento = Instant.now();

    private String error;

    @Version
    private long version;

    protected TrabajoIndexacion() {
    }

    public TrabajoIndexacion(Long inmobiliariaId, Long documentoId, String rutaTemporal) {
        this.inmobiliariaId = inmobiliariaId;
        this.documentoId = documentoId;
        this.rutaTemporal = rutaTemporal;
    }

    public void marcarEnCurso() {
        this.estado = EstadoTrabajo.EN_CURSO;
    }

    public void marcarTerminado() {
        this.estado = EstadoTrabajo.TERMINADO;
        this.error = null;
    }

    /** Registra el fallo; devuelve true si ya se agotaron los reintentos (fallo definitivo). */
    public boolean registrarFallo(String mensaje) {
        this.intentos++;
        this.error = mensaje;
        if (this.intentos >= MAX_INTENTOS) {
            this.estado = EstadoTrabajo.FALLIDO;
            return true;
        }
        this.estado = EstadoTrabajo.PENDIENTE;
        this.proximoIntento = Instant.now().plusSeconds(30L * intentos);
        return false;
    }

    /** Un trabajo que quedó EN_CURSO por un reinicio del servidor vuelve a la cola, sin contar como intento. */
    public void reanudar() {
        if (this.estado == EstadoTrabajo.EN_CURSO) {
            this.estado = EstadoTrabajo.PENDIENTE;
        }
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getDocumentoId() { return documentoId; }
    public String getRutaTemporal() { return rutaTemporal; }
    public EstadoTrabajo getEstado() { return estado; }
    public int getIntentos() { return intentos; }
    public Instant getProximoIntento() { return proximoIntento; }
    public String getError() { return error; }
}
