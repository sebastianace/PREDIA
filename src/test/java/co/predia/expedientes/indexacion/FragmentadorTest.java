package co.predia.expedientes.indexacion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FragmentadorTest {

    private final Fragmentador fragmentador = new Fragmentador();

    /** Repite una frase (con un número para que no se repita idéntica) hasta sumar los tokens pedidos. */
    private String parrafoDe(int tokensAproximados) {
        StringBuilder texto = new StringBuilder();
        int n = 0;
        while (fragmentador.contarTokens(texto.toString()) < tokensAproximados) {
            texto.append("El apartamento número ").append(n++).append(" tiene balcón y vista a la montaña. ");
        }
        return texto.toString().strip();
    }

    @Test
    void unDocumentoCortoProduceUnSoloFragmento() {
        String texto = parrafoDe(300);
        List<FragmentoCandidato> resultado = fragmentador.fragmentar(List.of(new PaginaTexto(1, texto, false)));

        assertThat(resultado).hasSize(1);
        FragmentoCandidato f = resultado.get(0);
        assertThat(f.orden()).isZero();
        assertThat(f.paginaInicio()).isEqualTo(1);
        assertThat(f.paginaFin()).isEqualTo(1);
        assertThat(f.esOcr()).isFalse();
        assertThat(f.tokens()).isLessThanOrEqualTo(Fragmentador.TOKENS_MAXIMO);
    }

    @Test
    void unDocumentoLargoProduceVariosFragmentosQueSeSolapan() {
        // Varios párrafos independientes que en total superan ampliamente el máximo de un fragmento.
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            texto.append(parrafoDe(150)).append("\n\n");
        }
        List<FragmentoCandidato> resultado =
                fragmentador.fragmentar(List.of(new PaginaTexto(1, texto.toString(), false)));

        assertThat(resultado.size()).isGreaterThanOrEqualTo(2);
        for (int i = 0; i < resultado.size(); i++) {
            FragmentoCandidato f = resultado.get(i);
            assertThat(f.orden()).isEqualTo(i);
            assertThat(f.tokens()).isLessThanOrEqualTo(Fragmentador.TOKENS_MAXIMO);
        }
        // Todos menos el último deben alcanzar un tamaño razonable antes de cerrarse.
        for (int i = 0; i < resultado.size() - 1; i++) {
            assertThat(resultado.get(i).tokens()).isGreaterThanOrEqualTo(Fragmentador.TOKENS_MINIMO - 50);
        }

        // El fragmento siguiente empieza repitiendo el final del anterior (solape).
        String colaDelPrimero = ultimasPalabras(resultado.get(0).texto(), 6);
        assertThat(resultado.get(1).texto()).contains(colaDelPrimero);
    }

    @Test
    void detectaLaSeccionPorEncabezadoLegalYLaConservaHastaElSiguiente() {
        String pagina = """
                CLÁUSULA PRIMERA. OBJETO

                %s

                CLÁUSULA SEGUNDA. PRECIO

                %s
                """.formatted(parrafoDe(60), parrafoDe(60));

        List<FragmentoCandidato> resultado = fragmentador.fragmentar(List.of(new PaginaTexto(1, pagina, false)));

        assertThat(resultado).hasSize(1);
        // La sección vigente al cerrar el único fragmento es la última encabezada vista.
        assertThat(resultado.get(0).seccion()).isEqualTo("CLÁUSULA SEGUNDA. PRECIO");
    }

    @Test
    void detectaLosEncabezadosAunqueVayanPegadosAlTextoSinLineasEnBlanco() {
        // Así llega el texto de casi cualquier PDF: sin líneas en blanco entre párrafos.
        String pagina = "CONTRATO DE ARRENDAMIENTO\n"
                + "Entre los suscritos se celebra el presente contrato.\n"
                + "CLÁUSULA PRIMERA. OBJETO\n"
                + "El arrendador entrega el inmueble ubicado en la calle 30.\n"
                + "CLÁUSULA SEGUNDA. CANON\n"
                + "El canon mensual es de un millón de pesos.";

        List<FragmentoCandidato> resultado = fragmentador.fragmentar(List.of(new PaginaTexto(1, pagina, false)));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).seccion()).isEqualTo("CLÁUSULA SEGUNDA. CANON");
    }

    @Test
    void unaLineaEnMayusculasEnMitadDeUnaOracionNoSeTomaPorTitulo() {
        String pagina = "El arrendador entrega al arrendatario\nEL INMUEBLE\nubicado en la calle 30 de la ciudad.";

        List<FragmentoCandidato> resultado = fragmentador.fragmentar(List.of(new PaginaTexto(1, pagina, false)));

        assertThat(resultado.get(0).seccion()).isNull();
        assertThat(resultado.get(0).texto()).contains("EL INMUEBLE");
    }

    @Test
    void unaPaginaEscaneadaMarcaElFragmentoComoOcr() {
        List<FragmentoCandidato> resultado = fragmentador.fragmentar(List.of(
                new PaginaTexto(1, parrafoDe(100), false),
                new PaginaTexto(2, parrafoDe(100), true)));

        assertThat(resultado).hasSize(1);
        FragmentoCandidato f = resultado.get(0);
        assertThat(f.esOcr()).isTrue();
        assertThat(f.paginaInicio()).isEqualTo(1);
        assertThat(f.paginaFin()).isEqualTo(2);
    }

    @Test
    void unParrafoMuchoMasLargoQueElMaximoSeDivideEnVariosFragmentos() {
        String parrafoGigante = parrafoDe(2600); // un único bloque, sin saltos de línea dobles
        List<FragmentoCandidato> resultado =
                fragmentador.fragmentar(List.of(new PaginaTexto(1, parrafoGigante, false)));

        assertThat(resultado.size()).isGreaterThanOrEqualTo(3);
        for (FragmentoCandidato f : resultado) {
            assertThat(f.tokens()).isLessThanOrEqualTo(Fragmentador.TOKENS_MAXIMO);
            assertThat(f.tokens()).isGreaterThan(0);
        }
        // El orden es consecutivo desde 0.
        assertThat(resultado.stream().map(FragmentoCandidato::orden).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, resultado.size()).boxed().toList());
    }

    @Test
    void unTextoVacioNoProduceFragmentos() {
        assertThat(fragmentador.fragmentar(List.of(new PaginaTexto(1, "   ", false)))).isEmpty();
        assertThat(fragmentador.fragmentar(List.of())).isEmpty();
    }

    private static String ultimasPalabras(String texto, int cantidad) {
        String[] palabras = texto.trim().split("\\s+");
        int desde = Math.max(0, palabras.length - cantidad);
        return String.join(" ", java.util.Arrays.copyOfRange(palabras, desde, palabras.length));
    }
}
