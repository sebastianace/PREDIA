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
