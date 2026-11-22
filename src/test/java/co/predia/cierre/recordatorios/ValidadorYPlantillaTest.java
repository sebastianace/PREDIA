package co.predia.cierre.recordatorios;

import co.predia.cierre.recordatorios.ValidadorTextoRecordatorio.Documento;
import co.predia.cierre.recordatorios.ValidadorTextoRecordatorio.NoPedido;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorYPlantillaTest {

    private static final Documento TRADICION = new Documento("Certificado de tradición y libertad",
            List.of("tradición y libertad", "tradicion y libertad"));
    private static final Documento ADMINISTRACION = new Documento("Paz y salvo de administración",
            List.of("paz y salvo de administración"));
    private static final NoPedido HIPOTECA = new NoPedido("Carta del banco para levantar la hipoteca",
            List.of("hipoteca"));
    private static final NoPedido PREDIAL = new NoPedido("Paz y salvo del impuesto predial", List.of("predial"));

    private static ValidadorTextoRecordatorio.Resultado validar(String texto) {
        return ValidadorTextoRecordatorio.validar(texto, List.of(TRADICION, ADMINISTRACION),
                List.of(HIPOTECA, PREDIAL), Set.of("18", "2026", "30"));
    }

    @Test
    void aceptaUnMensajeQueMencionaTodoLoQueFaltaYNadaMas() {
        var r = validar("Hola, le recordamos el certificado de tradición y libertad (venció el 18 de noviembre de "
                + "2026; debe ser de máximo 30 días) y el paz y salvo de administración.");

        assertThat(r.esValido()).isTrue();
    }

    @Test
    void marcaUnDocumentoPendienteQueElMensajeNoMenciona() {
        var r = validar("Hola, le recordamos el certificado de tradición y libertad (venció el 18 de noviembre).");

        assertThat(r.problemas()).anyMatch(p -> p.contains("Paz y salvo de administración"));
    }

    @Test
    void marcaUnDocumentoQueNoEstaPendiente() {
        var r = validar("Necesitamos el certificado de tradición y libertad, el paz y salvo de administración y "
                + "también el paz y salvo predial.");

        assertThat(r.esValido()).isFalse();
        assertThat(r.problemas()).anyMatch(p -> p.contains("predial") && p.contains("no está pendiente"));
    }

    @Test
    void marcaNumerosQueNoSalenDeLaLista() {
        var r = validar("Necesitamos el certificado de tradición y libertad y el paz y salvo de administración "
                + "antes del 25 de diciembre.");

        assertThat(r.problemas()).anyMatch(p -> p.contains("25"));
    }

    @Test
    void ignoraMayusculasYAcentosAlComparar() {
        var r = ValidadorTextoRecordatorio.validar("CERTIFICADO DE TRADICION Y LIBERTAD y PAZ Y SALVO DE ADMINISTRACION.",
                List.of(TRADICION, ADMINISTRACION), List.of(HIPOTECA), Set.of());

        assertThat(r.esValido()).isTrue();
        assertThat(ValidadorTextoRecordatorio.validar("sin hipotecá ni nada", List.of(), List.of(HIPOTECA), Set.of())
                .esValido()).isFalse();
    }

    @Test
    void laPlantillaSiempreMencionaLoQueFaltaYPasaLaMismaValidacion() {
        List<ItemRecordatorio> items = List.of(
                new ItemRecordatorio("CERTIFICADO_TRADICION", "Certificado de tradición y libertad", "VENCIDO",
                        "Venció el 14 de noviembre de 2026. Se necesita uno expedido hace máximo 30 días.", "2026-11-14"),
                new ItemRecordatorio("ADMINISTRACION", "Paz y salvo de administración", "PENDIENTE",
                        "Aún no lo hemos recibido.", null));
        var entrada = new RedactorRecordatorio.Entrada("Jorge Ramírez", "Apto 1102 Cañaveral", "Laura Gómez",
                "Altos Inmobiliaria", items,
                List.of(TRADICION, ADMINISTRACION), List.of(HIPOTECA, PREDIAL));

        var borrador = RedactorRecordatorio.plantilla(entrada);

        assertThat(borrador.origen()).isEqualTo(OrigenTexto.PLANTILLA);
        assertThat(borrador.cuerpo()).contains("Certificado de tradición y libertad", "Paz y salvo de administración",
                "Apto 1102 Cañaveral", "Laura Gómez");
        var resultado = ValidadorTextoRecordatorio.validar(borrador.asunto() + "\n" + borrador.cuerpo(),
                entrada.pedidos(), entrada.noPedidos(), RedactorRecordatorio.numerosPermitidos(entrada));
        assertThat(resultado.problemas()).isEmpty();
    }
}
