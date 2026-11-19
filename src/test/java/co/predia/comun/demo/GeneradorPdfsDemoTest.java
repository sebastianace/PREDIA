package co.predia.comun.demo;

import co.predia.expedientes.indexacion.Fragmentador;
import co.predia.expedientes.indexacion.PaginaTexto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Los PDF de ejemplo se generan con texto extraíble (sin OCR) y con secciones que el fragmentador reconoce. */
class GeneradorPdfsDemoTest {

    @TempDir Path temporal;

    private static String textoDe(Path pdf) throws Exception {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(documento);
        }
    }

    @Test
    void generaLosCincoPdfConTextoExtraibleYFechasRelativasAHoy() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 19);

        List<Path> pdfs = GeneradorPdfsDemo.generar(temporal, hoy);

        assertThat(pdfs).hasSize(5).allSatisfy(p -> assertThat(p).exists().isNotEmptyFile());
        assertThat(textoDe(temporal.resolve("certificado-tradicion-y-libertad.pdf")))
                .contains("CERTIFICADO DE TRADICIÓN Y LIBERTAD", "16 de noviembre de 2026");   // hoy - 3 días
        assertThat(textoDe(temporal.resolve("paz-y-salvo-administracion.pdf")))
                .contains("PAZ Y SALVO DE ADMINISTRACIÓN", "19 de noviembre de 2026", "noviembre de 2026");
    }

    @Test
    void elFragmentadorReconoceLasClausulasYLosArticulos() throws Exception {
        GeneradorPdfsDemo.generar(temporal, LocalDate.of(2026, 11, 19));
        Fragmentador fragmentador = new Fragmentador();

        var contrato = fragmentador.fragmentar(List.of(new PaginaTexto(1,
                textoDe(temporal.resolve("contrato-arrendamiento.pdf")), false)));
        var reglamento = fragmentador.fragmentar(List.of(new PaginaTexto(1,
                textoDe(temporal.resolve("reglamento-propiedad-horizontal.pdf")), false)));

        // La sección de un fragmento es el último encabezado visto: con todo en un fragmento, la última cláusula.
        assertThat(contrato.get(contrato.size() - 1).seccion()).startsWith("CLÁUSULA QUINTA");
        assertThat(reglamento.get(reglamento.size() - 1).seccion()).startsWith("ARTÍCULO 6");
    }
}
