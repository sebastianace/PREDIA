package co.predia.expedientes.consulta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FusionRRFTest {

    @Test
    void loQueApareceEnAmbasListasQuedaPrimero() {
        List<Long> vectorial = List.of(10L, 20L, 30L);
        List<Long> textual = List.of(20L, 40L, 10L);

        List<Long> resultado = FusionRRF.fusionar(vectorial, textual);

        // 20 (2.º en vectorial, 1.º en textual) y 10 (1.º en vectorial, 3.º en textual) quedan
        // primeros, por delante de lo que solo aparece en una lista.
        assertThat(resultado).startsWith(20L, 10L).containsExactlyInAnyOrder(10L, 20L, 30L, 40L);
    }

    @Test
    void unaListaVaciaNoRompeLaFusion() {
        assertThat(FusionRRF.fusionar(List.of(1L, 2L), List.of())).containsExactly(1L, 2L);
        assertThat(FusionRRF.fusionar(List.of(), List.of())).isEmpty();
    }

    @Test
    void elOrdenDentroDeCadaListaImportaMasQueAparecerEnUnaSola() {
        // 1.º en vectorial debe ganarle a algo que solo aparece de último en la lista textual.
        List<Long> resultado = FusionRRF.fusionar(List.of(1L), List.of(99L, 98L, 2L));
        assertThat(resultado.get(0)).isEqualTo(1L);
    }
}
