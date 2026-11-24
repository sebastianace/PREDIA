package co.predia.comprador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Un inmueble que el asesor le compartió a un cliente. Es la lista blanca de lo que el comprador puede ver. */
@Entity
@Table(name = "inmueble_compartido")
public class InmuebleCompartido {

    public enum Interes { ME_INTERESA, NO_ME_INTERESA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "inmueble_id", nullable = false, updatable = false)
    private Long inmuebleId;

    @Column(length = 500)
    private String nota;

    @Column(name = "compartido_por")
    private Long compartidoPor;

    @Column(name = "compartido_en", nullable = false, updatable = false)
    private Instant compartidoEn = Instant.now();

    @Enumerated(EnumType.STRING)
    private Interes interes;

    @Column(name = "interes_en")
    private Instant interesEn;

    protected InmuebleCompartido() {
    }

    public InmuebleCompartido(Long inmobiliariaId, Long clienteId, Long inmuebleId, String nota, Long compartidoPor) {
        this.inmobiliariaId = inmobiliariaId;
        this.clienteId = clienteId;
        this.inmuebleId = inmuebleId;
        this.nota = nota;
        this.compartidoPor = compartidoPor;
    }

    public void actualizarNota(String nota) {
        this.nota = nota;
    }

    public void marcarInteres(Interes interes) {
        this.interes = interes;
        this.interesEn = interes == null ? null : Instant.now();
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getClienteId() { return clienteId; }
    public Long getInmuebleId() { return inmuebleId; }
    public String getNota() { return nota; }
    public Instant getCompartidoEn() { return compartidoEn; }
    public Interes getInteres() { return interes; }
    public Instant getInteresEn() { return interesEn; }
}
