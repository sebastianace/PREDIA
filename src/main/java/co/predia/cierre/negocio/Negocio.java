package co.predia.cierre.negocio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Une el inmueble, el propietario-vendedor, el cliente comprador y la fecha tentativa de firma. */
@Entity
@Table(name = "negocio")
public class Negocio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "inmueble_id", nullable = false, updatable = false)
    private Long inmuebleId;

    @Column(name = "propietario_id", nullable = false)
    private Long propietarioId;

    @Column(name = "cliente_comprador_id")
    private Long clienteCompradorId;

    @Column(name = "asesor_id", nullable = false, updatable = false)
    private Long asesorId;

    @Column(name = "fecha_firma_tentativa")
    private LocalDate fechaFirmaTentativa;

    @Column(name = "actua_por_apoderado", nullable = false)
    private boolean actuaPorApoderado;

    @Enumerated(EnumType.STRING)
    @Column(name = "hipoteca_vigente", nullable = false)
    private HipotecaVigente hipotecaVigente = HipotecaVigente.DESCONOCIDO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNegocio estado = EstadoNegocio.ABIERTO;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected Negocio() {
    }

    public Negocio(Long inmobiliariaId, Long inmuebleId, Long asesorId) {
        this.inmobiliariaId = inmobiliariaId;
        this.inmuebleId = inmuebleId;
        this.asesorId = asesorId;
    }

    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = Instant.now();
    }

    public void actualizar(Long propietarioId, Long clienteCompradorId, LocalDate fechaFirmaTentativa,
                           boolean actuaPorApoderado, HipotecaVigente hipotecaVigente, EstadoNegocio estado) {
        this.propietarioId = propietarioId;
        this.clienteCompradorId = clienteCompradorId;
        this.fechaFirmaTentativa = fechaFirmaTentativa;
        this.actuaPorApoderado = actuaPorApoderado;
        this.hipotecaVigente = hipotecaVigente;
        this.estado = estado;
    }

    // --- Cierre ---------------------------------------------------------------------------------------------------

    @Column(name = "firmado_en")
    private Instant firmadoEn;

    @Column(name = "firmado_por")
    private Long firmadoPor;

    @Column(name = "valor_cierre")
    private BigDecimal valorCierre;

    @Column(name = "nota_cierre")
    private String notaCierre;

    @Column(name = "cancelado_en")
    private Instant canceladoEn;

    /** La venta se firmó: queda registrado cuándo, quién lo confirmó y por qué valor. */
    public void cerrar(Long usuarioId, BigDecimal valor, String nota, Instant cuando) {
        this.estado = EstadoNegocio.FIRMADO;
        this.firmadoEn = cuando;
        this.firmadoPor = usuarioId;
        this.valorCierre = valor;
        this.notaCierre = nota;
        this.canceladoEn = null;
    }

    /** El negocio se cayó: pasa a CANCELADO y anota cuándo (cuenta para la tasa de cierre de su asesor). */
    public void cancelar(Instant cuando) {
        cambiarEstadoSinCierre(EstadoNegocio.CANCELADO, cuando);
    }

    /** Deshace un cierre (solo lo hace un administrador): el negocio vuelve a estar abierto y deja de contar como venta. */
    public void reabrir() {
        this.estado = EstadoNegocio.ABIERTO;
        this.firmadoEn = null;
        this.firmadoPor = null;
        this.valorCierre = null;
        this.notaCierre = null;
    }

    /** Pasa a CANCELADO (anota cuándo) o vuelve a ABIERTO; el cierre FIRMADO solo se hace con {@link #cerrar}. */
    void cambiarEstadoSinCierre(EstadoNegocio nuevo, Instant cuando) {
        if (nuevo == EstadoNegocio.CANCELADO && this.estado != EstadoNegocio.CANCELADO) {
            this.canceladoEn = cuando;
        } else if (nuevo != EstadoNegocio.CANCELADO) {
            this.canceladoEn = null;
        }
        this.estado = nuevo;
    }

    public Instant getFirmadoEn() { return firmadoEn; }
    public Long getFirmadoPor() { return firmadoPor; }
    public BigDecimal getValorCierre() { return valorCierre; }
    public String getNotaCierre() { return notaCierre; }
    public Instant getCanceladoEn() { return canceladoEn; }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getInmuebleId() { return inmuebleId; }
    public Long getPropietarioId() { return propietarioId; }
    public Long getClienteCompradorId() { return clienteCompradorId; }
    public Long getAsesorId() { return asesorId; }
    public LocalDate getFechaFirmaTentativa() { return fechaFirmaTentativa; }
    public boolean isActuaPorApoderado() { return actuaPorApoderado; }
    public HipotecaVigente getHipotecaVigente() { return hipotecaVigente; }
    public EstadoNegocio getEstado() { return estado; }
    public Instant getCreadoEn() { return creadoEn; }
}
