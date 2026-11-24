package co.predia.comprador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** «Quiero más información»: un aviso al asesor. No envía ningún correo ni responde nada por sí solo. */
@Entity
@Table(name = "solicitud_comprador")
public class SolicitudComprador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "inmueble_id", updatable = false)
    private Long inmuebleId;

    @Column(nullable = false, length = 1000, updatable = false)
    private String mensaje;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn = Instant.now();

    @Column(name = "atendida_en")
    private Instant atendidaEn;

    @Column(name = "atendida_por")
    private Long atendidaPor;

    protected SolicitudComprador() {
    }

    public SolicitudComprador(Long inmobiliariaId, Long clienteId, Long inmuebleId, String mensaje) {
        this.inmobiliariaId = inmobiliariaId;
        this.clienteId = clienteId;
        this.inmuebleId = inmuebleId;
        this.mensaje = mensaje;
    }

    public void atender(Long usuarioId) {
        if (atendidaEn == null) {
            atendidaEn = Instant.now();
            atendidaPor = usuarioId;
        }
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getClienteId() { return clienteId; }
    public Long getInmuebleId() { return inmuebleId; }
    public String getMensaje() { return mensaje; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getAtendidaEn() { return atendidaEn; }
}
