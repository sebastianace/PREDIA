package co.predia.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Un enlace para crear o recuperar la contraseña. Solo se guarda el SHA-256 del token, nunca el token en claro. */
@Entity
@Table(name = "invitacion_acceso")
public class InvitacionAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usada_en")
    private Instant usadaEn;

    @Column(name = "creada_por", updatable = false)
    private Long creadaPor;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn = Instant.now();

    protected InvitacionAcceso() {
    }

    public InvitacionAcceso(Long inmobiliariaId, Long usuarioId, String tokenHash, Instant expiraEn, Long creadaPor) {
        this.inmobiliariaId = inmobiliariaId;
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
        this.creadaPor = creadaPor;
    }

    public boolean vigente(Instant ahora) {
        return usadaEn == null && ahora.isBefore(expiraEn);
    }

    /** Se gasta al usarla, y también cuando se envía otra: así solo vale la última. */
    public void gastar(Instant ahora) {
        if (usadaEn == null) {
            usadaEn = ahora;
        }
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getUsuarioId() { return usuarioId; }
    public Instant getExpiraEn() { return expiraEn; }
    public Instant getUsadaEn() { return usadaEn; }
    public Instant getCreadaEn() { return creadaEn; }
}
