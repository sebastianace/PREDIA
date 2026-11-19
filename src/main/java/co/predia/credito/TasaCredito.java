package co.predia.credito;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Tasa efectiva anual de un banco, con su fuente y fecha. Nunca se inventa: la carga el ADMIN. */
@Entity
@Table(name = "tasa_credito")
public class TasaCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(nullable = false)
    private String banco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTasa tipo;

    @Column(name = "tasa_ea", nullable = false)
    private BigDecimal tasaEa;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(nullable = false)
    private String fuente;

    @Column(name = "fecha_carga", nullable = false, insertable = false, updatable = false)
    private Instant fechaCarga;

    @Column(name = "es_ejemplo", nullable = false)
    private boolean esEjemplo;

    @Column(name = "cargada_por")
    private Long cargadaPor;

    protected TasaCredito() {
    }

    public TasaCredito(Long inmobiliariaId, String banco, TipoTasa tipo, BigDecimal tasaEa, LocalDate vigenteDesde,
                       String fuente, boolean esEjemplo, Long cargadaPor) {
        this.inmobiliariaId = inmobiliariaId;
        this.cargadaPor = cargadaPor;
        actualizar(banco, tipo, tasaEa, vigenteDesde, fuente, esEjemplo);
    }

    public void actualizar(String banco, TipoTasa tipo, BigDecimal tasaEa, LocalDate vigenteDesde, String fuente,
                           boolean esEjemplo) {
        this.banco = banco;
        this.tipo = tipo;
        this.tasaEa = tasaEa;
        this.vigenteDesde = vigenteDesde;
        this.fuente = fuente;
        this.esEjemplo = esEjemplo;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public String getBanco() { return banco; }
    public TipoTasa getTipo() { return tipo; }
    public BigDecimal getTasaEa() { return tasaEa; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public String getFuente() { return fuente; }
    public Instant getFechaCarga() { return fechaCarga; }
    public boolean isEsEjemplo() { return esEjemplo; }
}
