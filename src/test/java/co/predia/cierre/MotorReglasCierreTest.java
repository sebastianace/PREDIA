package co.predia.cierre;

import co.predia.cierre.catalogo.ReglaAplica;
import co.predia.cierre.catalogo.ReglaVigencia;
import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.EstadoRegistrado;
import co.predia.cierre.negocio.HipotecaVigente;
import co.predia.cierre.reglas.EstadoRequisito;
import co.predia.cierre.reglas.MotorReglasCierre;
import co.predia.cierre.reglas.MotorReglasCierre.Contexto;
import co.predia.cierre.reglas.MotorReglasCierre.Evaluacion;
import co.predia.cierre.reglas.MotorReglasCierre.Registro;
import co.predia.cierre.reglas.MotorReglasCierre.Requisito;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Todas las pruebas usan un "hoy" fijo: el resultado nunca depende del día en que corran. */
class MotorReglasCierreTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);

    private static final Requisito CERTIFICADO_TRADICION =
            new Requisito(ReglaAplica.SIEMPRE, true, ReglaVigencia.DIAS_DESDE_EXPEDICION, 30);
    private static final Requisito PREDIAL = new Requisito(ReglaAplica.SIEMPRE, true, ReglaVigencia.ANIO_EN_CURSO, null);
    private static final Requisito ADMINISTRACION =
            new Requisito(ReglaAplica.SI_PROPIEDAD_HORIZONTAL, true, ReglaVigencia.MES_EN_CURSO, null);
    private static final Requisito CEDULA = new Requisito(ReglaAplica.SIEMPRE, true, ReglaVigencia.NINGUNA, null);

    private static Contexto contexto(LocalDate fechaFirma) {
        return new Contexto(true, false, false, EstadoCivil.SOLTERO, false, HipotecaVigente.NO, fechaFirma, 7, HOY);
    }

    private static Registro recibido(LocalDate expedicion) {
        return new Registro(EstadoRegistrado.RECIBIDO, expedicion);
    }

    @Test
    void certificadoDeTradicionExpedidoHace31DiasEstaVencido() {
        Evaluacion e = MotorReglasCierre.evaluar(CERTIFICADO_TRADICION, recibido(HOY.minusDays(31)),
                contexto(HOY.plusDays(10)));

        assertThat(e.estado()).isEqualTo(EstadoRequisito.VENCIDO);
        assertThat(e.venceEl()).isEqualTo(HOY.minusDays(1));
    }

    @Test
    void certificadoExpedidoHace25DiasConFirmaEn10DiasEstaPorVencer() {
        Evaluacion e = MotorReglasCierre.evaluar(CERTIFICADO_TRADICION, recibido(HOY.minusDays(25)),
                contexto(HOY.plusDays(10)));

        // Vence en 5 días: antes de la firma (en 10).
        assertThat(e.estado()).isEqualTo(EstadoRequisito.POR_VENCER);
        assertThat(e.venceEl()).isEqualTo(HOY.plusDays(5));
    }

    @Test
    void certificadoRecienExpedidoYVigenteHastaDespuesDeLaFirmaEstaRecibido() {
        Evaluacion e = MotorReglasCierre.evaluar(CERTIFICADO_TRADICION, recibido(HOY.minusDays(2)),
                contexto(HOY.plusDays(10)));

        assertThat(e.estado()).isEqualTo(EstadoRequisito.RECIBIDO);
    }

    @Test
    void sinFechaDeFirmaSoloCuentaElMargenDeAlerta() {
        // Vence en 20 días y la alerta es de 7: sigue RECIBIDO. Con vencimiento en 3 días, POR_VENCER.
        assertThat(MotorReglasCierre.evaluar(CERTIFICADO_TRADICION, recibido(HOY.minusDays(10)), contexto(null))
                .estado()).isEqualTo(EstadoRequisito.RECIBIDO);
        assertThat(MotorReglasCierre.evaluar(CERTIFICADO_TRADICION, recibido(HOY.minusDays(27)), contexto(null))
                .estado()).isEqualTo(EstadoRequisito.POR_VENCER);
    }

    @Test
    void elUltimoDiaDeVigenciaTodavíaEsValido() {
        // Expedido hace 30 días con 30 de vigencia: vence hoy. No está vencido (pero sí por vencer).
        Evaluacion e = MotorReglasCierre.evaluar(CERTIFICADO_TRADICION, recibido(HOY.minusDays(30)), contexto(null));

        assertThat(e.venceEl()).isEqualTo(HOY);
        assertThat(e.estado()).isEqualTo(EstadoRequisito.POR_VENCER);
    }

    @Test
    void pazYSalvoDeAdministracionNoAplicaSiElInmuebleNoEsPropiedadHorizontal() {
        Contexto casaSinPropiedadHorizontal = new Contexto(false, false, false, EstadoCivil.SOLTERO, false,
                HipotecaVigente.NO, null, 7, HOY);

        Evaluacion e = MotorReglasCierre.evaluar(ADMINISTRACION, new Registro(EstadoRegistrado.PENDIENTE, null),
                casaSinPropiedadHorizontal);

        assertThat(e.estado()).isEqualTo(EstadoRequisito.NO_APLICA);
    }

    @Test
    void pazYSalvoDeAdministracionEsDelMesEnCurso() {
        // Expedido el 3 de noviembre: vale hasta el 30 de noviembre.
        Evaluacion delMes = MotorReglasCierre.evaluar(ADMINISTRACION, recibido(LocalDate.of(2026, 11, 3)),
                contexto(null));
        assertThat(delMes.venceEl()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(delMes.estado()).isEqualTo(EstadoRequisito.RECIBIDO);

        // Si la firma es en diciembre, el de noviembre ya habrá vencido: POR_VENCER.
        assertThat(MotorReglasCierre.evaluar(ADMINISTRACION, recibido(LocalDate.of(2026, 11, 3)),
                contexto(LocalDate.of(2026, 12, 10))).estado()).isEqualTo(EstadoRequisito.POR_VENCER);

        // El de octubre ya está vencido.
        assertThat(MotorReglasCierre.evaluar(ADMINISTRACION, recibido(LocalDate.of(2026, 10, 20)), contexto(null))
                .estado()).isEqualTo(EstadoRequisito.VENCIDO);
    }

    @Test
    void elPredialEsDelAnioEnCurso() {
        assertThat(MotorReglasCierre.evaluar(PREDIAL, recibido(LocalDate.of(2026, 2, 1)), contexto(null)).estado())
                .isEqualTo(EstadoRequisito.RECIBIDO);
        assertThat(MotorReglasCierre.evaluar(PREDIAL, recibido(LocalDate.of(2025, 12, 15)), contexto(null)).estado())
                .isEqualTo(EstadoRequisito.VENCIDO);
    }

    @Test
    void laIaSoloSugiereUnDocumentoNuncaLoDaPorRecibidoNiEvaluaSuVigencia() {
        // Un certificado SUGERIDO con fecha ya vencida sigue siendo SUGERIDO: solo el asesor lo pasa a RECIBIDO.
        Evaluacion e = MotorReglasCierre.evaluar(CERTIFICADO_TRADICION,
                new Registro(EstadoRegistrado.SUGERIDO, HOY.minusDays(100)), contexto(null));

        assertThat(e.estado()).isEqualTo(EstadoRequisito.SUGERIDO);
        assertThat(e.venceEl()).isNull();
    }

    @Test
    void elAsesorPuedeMarcarUnRequisitoComoNoAplica() {
        Evaluacion e = MotorReglasCierre.evaluar(CEDULA, new Registro(EstadoRegistrado.NO_APLICA_MANUAL, null),
                contexto(null));

        assertThat(e.estado()).isEqualTo(EstadoRequisito.NO_APLICA);
    }

    @Test
    void lasReglasDeAplicacionDependenDelNegocio() {
        Contexto base = contexto(null);
        // Hipoteca: solo si el asesor confirma que está vigente; "desconocida" no se exige.
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_HIPOTECA, base)).isFalse();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_HIPOTECA,
                new Contexto(true, false, false, EstadoCivil.SOLTERO, false, HipotecaVigente.DESCONOCIDO, null, 7,
                        HOY))).isFalse();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_HIPOTECA,
                new Contexto(true, false, false, EstadoCivil.SOLTERO, false, HipotecaVigente.SI, null, 7, HOY)))
                .isTrue();
        // Cónyuge: casado o unión marital, pero no una persona jurídica.
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_CASADO_O_UNION,
                new Contexto(true, false, false, EstadoCivil.CASADO, false, HipotecaVigente.NO, null, 7, HOY))).isTrue();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_CASADO_O_UNION,
                new Contexto(true, false, false, EstadoCivil.UNION_MARITAL, false, HipotecaVigente.NO, null, 7, HOY)))
                .isTrue();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_CASADO_O_UNION,
                new Contexto(true, false, true, EstadoCivil.CASADO, false, HipotecaVigente.NO, null, 7, HOY))).isFalse();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_CASADO_O_UNION, base)).isFalse();
        // Apoderado, persona jurídica y valorización.
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_APODERADO,
                new Contexto(true, false, false, EstadoCivil.SOLTERO, true, HipotecaVigente.NO, null, 7, HOY))).isTrue();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_PERSONA_JURIDICA,
                new Contexto(true, false, true, null, false, HipotecaVigente.NO, null, 7, HOY))).isTrue();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_VALORIZACION,
                new Contexto(true, true, false, EstadoCivil.SOLTERO, false, HipotecaVigente.NO, null, 7, HOY))).isTrue();
        assertThat(MotorReglasCierre.aplica(ReglaAplica.SI_VALORIZACION, base)).isFalse();
    }

    @Test
    void elSemaforoCuentaSoloLosObligatoriosQueAplican() {
        List<Evaluacion> evaluaciones = List.of(
                new Evaluacion(EstadoRequisito.RECIBIDO, null, true),
                new Evaluacion(EstadoRequisito.RECIBIDO, null, true),
                new Evaluacion(EstadoRequisito.VENCIDO, HOY.minusDays(1), true),
                new Evaluacion(EstadoRequisito.PENDIENTE, null, true),
                new Evaluacion(EstadoRequisito.NO_APLICA, null, true),       // no cuenta
                new Evaluacion(EstadoRequisito.PENDIENTE, null, false));     // recomendado: no bloquea

        var semaforo = MotorReglasCierre.semaforo(evaluaciones);

        assertThat(semaforo.listoParaFirmar()).isFalse();
        assertThat(semaforo.obligatoriosQueAplican()).isEqualTo(4);
        assertThat(semaforo.recibidos()).isEqualTo(2);
        assertThat(semaforo.faltan()).isEqualTo(2);
        assertThat(semaforo.porcentajeAvance()).isEqualTo(50);
    }

    @Test
    void listoParaFirmarSoloConTodoRecibidoYVigente() {
        assertThat(MotorReglasCierre.semaforo(List.of(
                new Evaluacion(EstadoRequisito.RECIBIDO, null, true),
                new Evaluacion(EstadoRequisito.NO_APLICA, null, true),
                new Evaluacion(EstadoRequisito.PENDIENTE, null, false))).listoParaFirmar()).isTrue();

        // POR_VENCER y SUGERIDO todavía bloquean.
        assertThat(MotorReglasCierre.semaforo(List.of(
                new Evaluacion(EstadoRequisito.RECIBIDO, null, true),
                new Evaluacion(EstadoRequisito.POR_VENCER, HOY.plusDays(2), true))).listoParaFirmar()).isFalse();
        assertThat(MotorReglasCierre.semaforo(List.of(
                new Evaluacion(EstadoRequisito.SUGERIDO, null, true))).listoParaFirmar()).isFalse();
        // Sin ningún requisito obligatorio que aplique no se declara "listo".
        assertThat(MotorReglasCierre.semaforo(List.of()).listoParaFirmar()).isFalse();
    }
}
