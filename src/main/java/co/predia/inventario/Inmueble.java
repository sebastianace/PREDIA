package co.predia.inventario;

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

/**
 * Inmueble del inventario. La columna "embedding" (pgvector) no se mapea aquí:
 * se escribe y consulta con SQL explícito para garantizar el filtro por inmobiliaria.
 */
@Entity
@Table(name = "inmueble")
public class Inmueble {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoInmueble tipo;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false)
    private String barrio;

    @Column(nullable = false)
    private String ciudad;

    private Short estrato;

    @Column(name = "area_m2", nullable = false)
    private BigDecimal areaM2;

    @Column(nullable = false)
    private short habitaciones;

    @Column(nullable = false)
    private short banos;

    @Column(nullable = false)
    private short parqueaderos;

    @Column(name = "acepta_mascotas", nullable = false)
    private boolean aceptaMascotas;

    @Column(name = "valor_administracion", nullable = false)
    private BigDecimal valorAdministracion = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operacion operacion;

    @Column(name = "precio_venta")
    private BigDecimal precioVenta;

    @Column(name = "canon_arriendo")
    private BigDecimal canonArriendo;

    @Column(name = "es_vis", nullable = false)
    private boolean esVis;

    @Column(name = "propiedad_horizontal", nullable = false)
    private boolean propiedadHorizontal;

    @Column(nullable = false)
    private String descripcion = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoInmueble estado = EstadoInmueble.DISPONIBLE;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected Inmueble() {
    }

    public Inmueble(Long inmobiliariaId, String codigo, String titulo, TipoInmueble tipo, Operacion operacion) {
        this.inmobiliariaId = inmobiliariaId;
        this.codigo = codigo;
        this.titulo = titulo;
        this.tipo = tipo;
        this.operacion = operacion;
    }

    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = Instant.now();
    }

    /** Precio de referencia según la operación: precio de venta o canon de arriendo. */
    public BigDecimal precioReferencia() {
        return operacion == Operacion.VENTA ? precioVenta : canonArriendo;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public TipoInmueble getTipo() { return tipo; }
    public void setTipo(TipoInmueble tipo) { this.tipo = tipo; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getBarrio() { return barrio; }
    public void setBarrio(String barrio) { this.barrio = barrio; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
    public Short getEstrato() { return estrato; }
    public void setEstrato(Short estrato) { this.estrato = estrato; }
    public BigDecimal getAreaM2() { return areaM2; }
    public void setAreaM2(BigDecimal areaM2) { this.areaM2 = areaM2; }
    public short getHabitaciones() { return habitaciones; }
    public void setHabitaciones(short habitaciones) { this.habitaciones = habitaciones; }
    public short getBanos() { return banos; }
    public void setBanos(short banos) { this.banos = banos; }
    public short getParqueaderos() { return parqueaderos; }
    public void setParqueaderos(short parqueaderos) { this.parqueaderos = parqueaderos; }
    public boolean isAceptaMascotas() { return aceptaMascotas; }
    public void setAceptaMascotas(boolean aceptaMascotas) { this.aceptaMascotas = aceptaMascotas; }
    public BigDecimal getValorAdministracion() { return valorAdministracion; }
    public void setValorAdministracion(BigDecimal valorAdministracion) { this.valorAdministracion = valorAdministracion; }
    public Operacion getOperacion() { return operacion; }
    public void setOperacion(Operacion operacion) { this.operacion = operacion; }
    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }
    public BigDecimal getCanonArriendo() { return canonArriendo; }
    public void setCanonArriendo(BigDecimal canonArriendo) { this.canonArriendo = canonArriendo; }
    public boolean isEsVis() { return esVis; }
    public void setEsVis(boolean esVis) { this.esVis = esVis; }
    public boolean isPropiedadHorizontal() { return propiedadHorizontal; }
    public void setPropiedadHorizontal(boolean propiedadHorizontal) { this.propiedadHorizontal = propiedadHorizontal; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public EstadoInmueble getEstado() { return estado; }
    public void setEstado(EstadoInmueble estado) { this.estado = estado; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
