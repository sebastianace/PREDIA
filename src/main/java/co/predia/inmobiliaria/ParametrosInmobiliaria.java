package co.predia.inmobiliaria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parámetros de las reglas de negocio de una inmobiliaria. Los usan los servicios Java
 * (presupuesto, crédito, cierre); el modelo de lenguaje nunca los aplica por su cuenta.
 */
@Entity
@Table(name = "parametros_inmobiliaria")
public class ParametrosInmobiliaria {

    @Id
    @Column(name = "inmobiliaria_id")
    private Long inmobiliariaId;

    @Column(name = "pct_cuota_max_ingreso", nullable = false)
    private BigDecimal pctCuotaMaxIngreso = new BigDecimal("0.30");

    @Column(name = "pct_cuota_inicial", nullable = false)
    private BigDecimal pctCuotaInicial = new BigDecimal("0.30");

    @Column(name = "pct_cuota_inicial_vis", nullable = false)
    private BigDecimal pctCuotaInicialVis = new BigDecimal("0.20");

    @Column(name = "plazo_default_anios", nullable = false)
    private int plazoDefaultAnios = 20;

    @Column(name = "dias_alerta_vencimiento", nullable = false)
    private int diasAlertaVencimiento = 7;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ciudades_con_valorizacion", nullable = false, columnDefinition = "text[]")
    private List<String> ciudadesConValorizacion = new ArrayList<>();

    @Column(name = "umbral_similitud_rag", nullable = false)
    private BigDecimal umbralSimilitudRag = new BigDecimal("0.300");

    @Column(name = "email_remitente")
    private String emailRemitente;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected ParametrosInmobiliaria() {
    }

    public ParametrosInmobiliaria(Long inmobiliariaId) {
        this.inmobiliariaId = inmobiliariaId;
    }

    public Long getInmobiliariaId() { return inmobiliariaId; }
    public BigDecimal getPctCuotaMaxIngreso() { return pctCuotaMaxIngreso; }
    public BigDecimal getPctCuotaInicial() { return pctCuotaInicial; }
    public BigDecimal getPctCuotaInicialVis() { return pctCuotaInicialVis; }
    public int getPlazoDefaultAnios() { return plazoDefaultAnios; }
    public int getDiasAlertaVencimiento() { return diasAlertaVencimiento; }
    public List<String> getCiudadesConValorizacion() { return ciudadesConValorizacion; }
    public BigDecimal getUmbralSimilitudRag() { return umbralSimilitudRag; }
    public String getEmailRemitente() { return emailRemitente; }
    public Instant getActualizadoEn() { return actualizadoEn; }

    public void actualizar(BigDecimal pctCuotaMaxIngreso, BigDecimal pctCuotaInicial, BigDecimal pctCuotaInicialVis,
                           int plazoDefaultAnios, int diasAlertaVencimiento, List<String> ciudadesConValorizacion,
                           BigDecimal umbralSimilitudRag, String emailRemitente) {
        this.pctCuotaMaxIngreso = pctCuotaMaxIngreso;
        this.pctCuotaInicial = pctCuotaInicial;
        this.pctCuotaInicialVis = pctCuotaInicialVis;
        this.plazoDefaultAnios = plazoDefaultAnios;
        this.diasAlertaVencimiento = diasAlertaVencimiento;
        this.ciudadesConValorizacion = new ArrayList<>(ciudadesConValorizacion);
        this.umbralSimilitudRag = umbralSimilitudRag;
        this.emailRemitente = emailRemitente;
        this.actualizadoEn = Instant.now();
    }
}
