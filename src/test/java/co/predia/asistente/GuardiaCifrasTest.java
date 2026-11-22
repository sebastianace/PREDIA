package co.predia.asistente;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuardiaCifrasTest {

    private static final List<String> FUENTES = List.of(
            "{precioVenta=$300.000.000, topeDeCompra=$294.114.000, tasa=Banco Ejemplo, 12 % E.A., cuotaMensual=$2.250.000, "
                    + "cuotaSobreIngreso=30 %, avance=50 %, vence=1 de diciembre de 2026}");

    @Test
    void lasCifrasQueVienenDeLasHerramientasEstanRespaldadas() {
        String respuesta = "Pueden comprar hasta $294.114.000 con una cuota de $2.250.000 (30 % del ingreso), "
                + "tasa 12 % E.A.; el certificado vence el 1 de diciembre de 2026.";

        assertThat(GuardiaCifras.sinRespaldo(respuesta, FUENTES)).isEmpty();
    }

    @Test
    void unaCifraInventadaPorElModeloSeSenala() {
        String respuesta = "Pueden comprar hasta $350.000.000 con una cuota de $2.250.000.";

        assertThat(GuardiaCifras.sinRespaldo(respuesta, FUENTES)).containsExactly("350.000.000");
    }

    @Test
    void unaSumaHechaPorElModeloNoSeConsideraRespaldada() {
        // 2.250.000 + 2.250.000 no aparece en ninguna herramienta: la suma la hizo el modelo, no Java.
        String respuesta = "Entre los dos pagarían $4.500.000 al mes.";

        assertThat(GuardiaCifras.sinRespaldo(respuesta, FUENTES)).containsExactly("4.500.000");
    }

    @Test
    void unaEscrituraAbreviadaExactaDeLaMismaCifraSeAcepta() {
        String respuesta = "El apto vale 300 millones y la cuota es de 2,25 millones.";

        assertThat(GuardiaCifras.sinRespaldo(respuesta, FUENTES)).isEmpty();
    }

    @Test
    void unRedondeoHechoPorElModeloSeSenalaPorqueElModeloNoRedondea() {
        // $294.114.000 dicho como «294 millones» es un redondeo: lo hizo el modelo, no Java.
        String respuesta = "Alcanzan unos 294 millones.";

        assertThat(GuardiaCifras.sinRespaldo(respuesta, FUENTES)).containsExactly("294");
    }

    @Test
    void lasCifrasQueEscribioElAsesorTambienCuentanComoFuente() {
        List<String> fuentes = List.of("¿Qué alcanza con un presupuesto de $180.000.000?");

        assertThat(GuardiaCifras.sinRespaldo("Con $180.000.000 hay 2 opciones.", fuentes)).isEmpty();
    }

    @Test
    void losNumerosDeUnSoloDigitoNoSeRevisan() {
        assertThat(GuardiaCifras.sinRespaldo("1. Pide el certificado [1]. 2. Pide el paz y salvo.", List.of()))
                .isEmpty();
    }
}
