package co.predia.clientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente comprador. Solo se guarda con la autorización de tratamiento de datos registrada (Ley 1581 de 2012):
 * la fecha y el asesor que la registró quedan en el propio registro, y la base de datos exige el consentimiento.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(nullable = false)
    private String nombre;

    private String email;

    private String telefono;

    @Column(name = "ingreso_familiar", nullable = false)
    private BigDecimal ingresoFamiliar;

    @Column(nullable = false)
    private BigDecimal ahorro = BigDecimal.ZERO;

    @Column(name = "deudas_mensuales", nullable = false)
    private BigDecimal deudasMensuales = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Intencion intencion;

    @Column(name = "habitaciones_min", nullable = false)
    private short habitacionesMin;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> zonas = new ArrayList<>();

    @Column(nullable = false)
    private String preferencias = "";

    @Column(name = "autorizacion_datos", nullable = false, updatable = false)
    private boolean autorizacionDatos;

    @Column(name = "autorizacion_fecha", nullable = false, updatable = false)
    private Instant autorizacionFecha;

    @Column(name = "autorizacion_registrada_por", nullable = false, updatable = false)
    private Long autorizacionRegistradaPor;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected Cliente() {
    }

    public Cliente(Long inmobiliariaId, Long registradaPor) {
        this.inmobiliariaId = inmobiliariaId;
        this.autorizacionDatos = true;
        this.autorizacionFecha = Instant.now();
        this.autorizacionRegistradaPor = registradaPor;
    }

    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = Instant.now();
    }

    public void actualizarPerfil(String nombre, String email, String telefono, BigDecimal ingresoFamiliar,
                                 BigDecimal ahorro, BigDecimal deudasMensuales, Intencion intencion,
                                 short habitacionesMin, List<String> zonas, String preferencias) {
        this.nombre = nombre;
        this.email = email;
        this.telefono = telefono;
        this.ingresoFamiliar = ingresoFamiliar;
        this.ahorro = ahorro;
        this.deudasMensuales = deudasMensuales;
        this.intencion = intencion;
        this.habitacionesMin = habitacionesMin;
        this.zonas = new ArrayList<>(zonas);
        this.preferencias = preferencias == null ? "" : preferencias;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public BigDecimal getIngresoFamiliar() { return ingresoFamiliar; }
    public BigDecimal getAhorro() { return ahorro; }
    public BigDecimal getDeudasMensuales() { return deudasMensuales; }
    public Intencion getIntencion() { return intencion; }
    public short getHabitacionesMin() { return habitacionesMin; }
    public List<String> getZonas() { return zonas; }
    public String getPreferencias() { return preferencias; }
    public boolean isAutorizacionDatos() { return autorizacionDatos; }
    public Instant getAutorizacionFecha() { return autorizacionFecha; }
    public Instant getCreadoEn() { return creadoEn; }
}
