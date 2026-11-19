package co.predia.cierre.negocio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Propietario-vendedor (persona natural o jurídica). Sus datos personales (documento, correo, teléfono) no se
 * escriben en los logs, y no se guarda sin la autorización de tratamiento de datos (Ley 1581 de 2012).
 */
@Entity
@Table(name = "propietario")
public class Propietario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_persona", nullable = false)
    private TipoPersona tipoPersona;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "tipo_documento", nullable = false)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false)
    private String numeroDocumento;

    private String email;

    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil")
    private EstadoCivil estadoCivil;

    @Column(name = "autorizacion_datos", nullable = false, updatable = false)
    private boolean autorizacionDatos;

    @Column(name = "autorizacion_fecha", nullable = false, updatable = false)
    private Instant autorizacionFecha;

    @Column(name = "autorizacion_registrada_por", nullable = false, updatable = false)
    private Long autorizacionRegistradaPor;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    protected Propietario() {
    }

    public Propietario(Long inmobiliariaId, Long registradaPor) {
        this.inmobiliariaId = inmobiliariaId;
        this.autorizacionDatos = true;
        this.autorizacionFecha = Instant.now();
        this.autorizacionRegistradaPor = registradaPor;
    }

    public void actualizar(TipoPersona tipoPersona, String nombre, String tipoDocumento, String numeroDocumento,
                           String email, String telefono, EstadoCivil estadoCivil) {
        this.tipoPersona = tipoPersona;
        this.nombre = nombre;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.email = email;
        this.telefono = telefono;
        // El estado civil solo tiene sentido en una persona natural.
        this.estadoCivil = tipoPersona == TipoPersona.NATURAL ? estadoCivil : null;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public TipoPersona getTipoPersona() { return tipoPersona; }
    public String getNombre() { return nombre; }
    public String getTipoDocumento() { return tipoDocumento; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public EstadoCivil getEstadoCivil() { return estadoCivil; }
    public boolean isAutorizacionDatos() { return autorizacionDatos; }
    public Instant getAutorizacionFecha() { return autorizacionFecha; }
}
