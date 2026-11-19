package co.predia.expedientes;

import co.predia.expedientes.indexacion.TipoDocumentoSugerido;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Un documento del expediente de un inmueble (y, opcionalmente, de un negocio). El PDF original
 * nunca se guarda aquí: solo su hash, sus metadatos y, en la tabla fragmento, su texto y sus vectores.
 */
@Entity
@Table(name = "documento")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(name = "inmueble_id", nullable = false, updatable = false)
    private Long inmuebleId;

    /** Se referencia por id, sin FK todavía: la tabla negocio llega en la Fase 5. */
    @Column(name = "negocio_id")
    private Long negocioId;

    @Column(name = "nombre_archivo", nullable = false, updatable = false)
    private String nombreArchivo;

    @Column(name = "hash_sha256", nullable = false, updatable = false)
    private String hashSha256;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private long tamanoBytes;

    private Integer paginas;

    @Column(name = "paginas_ocr", nullable = false)
    private int paginasOcr = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_indexacion", nullable = false)
    private EstadoIndexacion estadoIndexacion = EstadoIndexacion.EN_COLA;

    private String error;

    @Column(name = "indexado_en")
    private Instant indexadoEn;

    @Column(nullable = false)
    private boolean vectorizado = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sugerido")
    private TipoDocumentoSugerido tipoSugerido;

    @Column(name = "fecha_expedicion_sugerida")
    private LocalDate fechaExpedicionSugerida;

    private BigDecimal confianza;

    @Enumerated(EnumType.STRING)
    @Column(name = "clasificacion_estado", nullable = false)
    private ClasificacionEstado clasificacionEstado = ClasificacionEstado.SIN_CLASIFICAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_confirmado")
    private TipoDocumentoSugerido tipoConfirmado;

    @Column(name = "fecha_expedicion_confirmada")
    private LocalDate fechaExpedicionConfirmada;

    @Column(name = "confirmado_por")
    private Long confirmadoPor;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "roles_permitidos", nullable = false, columnDefinition = "text[]")
    private List<String> rolesPermitidos = new ArrayList<>(List.of("ADMIN", "ASESOR"));

    @Column(name = "subido_por", nullable = false, updatable = false)
    private Long subidoPor;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;

    protected Documento() {
    }

    public Documento(Long inmobiliariaId, Long inmuebleId, Long negocioId, String nombreArchivo, String hashSha256,
                     long tamanoBytes, Long subidoPor) {
        this.inmobiliariaId = inmobiliariaId;
        this.inmuebleId = inmuebleId;
        this.negocioId = negocioId;
        this.nombreArchivo = nombreArchivo;
        this.hashSha256 = hashSha256;
        this.tamanoBytes = tamanoBytes;
        this.subidoPor = subidoPor;
    }

    public void marcarProcesando() {
        this.estadoIndexacion = EstadoIndexacion.PROCESANDO;
        this.error = null;
    }

    public void marcarIndexado(int paginas, int paginasOcr, boolean vectorizado) {
        this.paginas = paginas;
        this.paginasOcr = paginasOcr;
        this.vectorizado = vectorizado;
        this.estadoIndexacion = EstadoIndexacion.INDEXADO;
        this.indexadoEn = Instant.now();
        this.error = null;
    }

    public void marcarError(String error) {
        this.estadoIndexacion = EstadoIndexacion.ERROR;
        this.error = error;
    }

    public void registrarSugerencia(TipoDocumentoSugerido tipo, LocalDate fechaExpedicion, BigDecimal confianza) {
        this.tipoSugerido = tipo;
        this.fechaExpedicionSugerida = fechaExpedicion;
        this.confianza = confianza;
        this.clasificacionEstado = ClasificacionEstado.SUGERIDA;
    }

    public void confirmarClasificacion(TipoDocumentoSugerido tipo, LocalDate fechaExpedicion, Long usuarioId) {
        this.tipoConfirmado = tipo;
        this.fechaExpedicionConfirmada = fechaExpedicion;
        this.confirmadoPor = usuarioId;
        this.confirmadoEn = Instant.now();
        this.clasificacionEstado = ClasificacionEstado.CONFIRMADA;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public Long getInmuebleId() { return inmuebleId; }
    public Long getNegocioId() { return negocioId; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getHashSha256() { return hashSha256; }
    public long getTamanoBytes() { return tamanoBytes; }
    public Integer getPaginas() { return paginas; }
    public int getPaginasOcr() { return paginasOcr; }
    public EstadoIndexacion getEstadoIndexacion() { return estadoIndexacion; }
    public String getError() { return error; }
    public Instant getIndexadoEn() { return indexadoEn; }
    public boolean isVectorizado() { return vectorizado; }
    public TipoDocumentoSugerido getTipoSugerido() { return tipoSugerido; }
    public LocalDate getFechaExpedicionSugerida() { return fechaExpedicionSugerida; }
    public BigDecimal getConfianza() { return confianza; }
    public ClasificacionEstado getClasificacionEstado() { return clasificacionEstado; }
    public TipoDocumentoSugerido getTipoConfirmado() { return tipoConfirmado; }
    public LocalDate getFechaExpedicionConfirmada() { return fechaExpedicionConfirmada; }
    public Long getConfirmadoPor() { return confirmadoPor; }
    public Instant getConfirmadoEn() { return confirmadoEn; }
    public List<String> getRolesPermitidos() { return rolesPermitidos; }
    public Long getSubidoPor() { return subidoPor; }
    public Instant getCreadoEn() { return creadoEn; }
}
