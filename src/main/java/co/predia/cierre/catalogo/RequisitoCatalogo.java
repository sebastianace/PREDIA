package co.predia.cierre.catalogo;

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

import java.util.ArrayList;
import java.util.List;

/** Un documento que la inmobiliaria exige al vendedor para firmar. Configurable por inmobiliaria. */
@Entity
@Table(name = "requisito_catalogo")
public class RequisitoCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false, updatable = false)
    private Long inmobiliariaId;

    @Column(nullable = false, updatable = false)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String descripcion = "";

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private boolean activo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "regla_aplica", nullable = false)
    private ReglaAplica reglaAplica = ReglaAplica.SIEMPRE;

    @Column(nullable = false)
    private boolean obligatorio = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "regla_vigencia", nullable = false)
    private ReglaVigencia reglaVigencia = ReglaVigencia.NINGUNA;

    @Column(name = "vigencia_dias")
    private Integer vigenciaDias;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento")
    private TipoDocumentoSugerido tipoDocumento;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "palabras_clave", nullable = false, columnDefinition = "text[]")
    private List<String> palabrasClave = new ArrayList<>();

    protected RequisitoCatalogo() {
    }

    public RequisitoCatalogo(Long inmobiliariaId, String codigo, String nombre, String descripcion, int orden,
                             ReglaAplica reglaAplica, boolean obligatorio, ReglaVigencia reglaVigencia,
                             Integer vigenciaDias, TipoDocumentoSugerido tipoDocumento, List<String> palabrasClave) {
        this.inmobiliariaId = inmobiliariaId;
        this.codigo = codigo;
        this.orden = orden;
        this.tipoDocumento = tipoDocumento;
        this.palabrasClave = new ArrayList<>(palabrasClave);
        actualizar(nombre, descripcion, orden, true, reglaAplica, obligatorio, reglaVigencia, vigenciaDias);
    }

    /** Lo que el ADMIN puede configurar. El código y la clasificación asociada no cambian. */
    public void actualizar(String nombre, String descripcion, int orden, boolean activo, ReglaAplica reglaAplica,
                           boolean obligatorio, ReglaVigencia reglaVigencia, Integer vigenciaDias) {
        this.nombre = nombre;
        this.descripcion = descripcion == null ? "" : descripcion;
        this.orden = orden;
        this.activo = activo;
        this.reglaAplica = reglaAplica;
        this.obligatorio = obligatorio;
        this.reglaVigencia = reglaVigencia;
        this.vigenciaDias = reglaVigencia == ReglaVigencia.DIAS_DESDE_EXPEDICION ? vigenciaDias : null;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public int getOrden() { return orden; }
    public boolean isActivo() { return activo; }
    public ReglaAplica getReglaAplica() { return reglaAplica; }
    public boolean isObligatorio() { return obligatorio; }
    public ReglaVigencia getReglaVigencia() { return reglaVigencia; }
    public Integer getVigenciaDias() { return vigenciaDias; }
    public TipoDocumentoSugerido getTipoDocumento() { return tipoDocumento; }
    public List<String> getPalabrasClave() { return palabrasClave; }
}
