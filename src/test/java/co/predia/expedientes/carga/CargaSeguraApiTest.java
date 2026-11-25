package co.predia.expedientes.carga;

import co.predia.comun.web.EscaneoNoDisponibleException;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que pasa al subir un PDF cuando el antivirus o la revisión del archivo lo rechazan: nada se guarda (ni el
 * documento, ni el trabajo, ni el temporal). El antivirus se simula; las pruebas nunca necesitan ClamAV.
 */
class CargaSeguraApiTest extends PruebaIntegracion {

    @MockitoBean EscanerArchivos escaner;
    @Autowired PropiedadesExpedientes propiedades;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void antivirusLimpioPorDefecto() {
        when(escaner.escanear(any())).thenReturn(EscanerArchivos.Resultado.limpio());
    }

    @Test
    void unArchivoLimpioSeAcepta() throws Exception {
        Preparacion p = preparar("SEG-1");

        subir(p, "contrato.pdf", pdfConTexto("Contrato de compraventa.")).andExpect(status().isAccepted());

        assertThat(documentosDe(p.inmueble)).isEqualTo(1);
    }

    @Test
    void unArchivoConAmenazaSeRechazaYNoDejaNada() throws Exception {
        Preparacion p = preparar("SEG-2");
        when(escaner.escanear(any())).thenReturn(EscanerArchivos.Resultado.infectado("Eicar-Test-Signature"));
        Set<Path> antes = archivosTemporales();

        subir(p, "contrato.pdf", pdfConTexto("Contrato de compraventa."))
                .andExpect(status().isUnprocessableEntity())
                // El usuario sabe que se rechazó por seguridad, pero no el nombre de la amenaza.
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("rechazado por seguridad")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Eicar"))));

        assertThat(documentosDe(p.inmueble)).isZero();
        assertThat(archivosTemporales()).isSubsetOf(antes);
    }

    @Test
    void siNoSePuedeVerificarConElAntivirusElArchivoNoSeAcepta() throws Exception {
        Preparacion p = preparar("SEG-3");
        when(escaner.escanear(any())).thenThrow(new EscaneoNoDisponibleException("No se pudo verificar.",
                new IOException("sin conexión")));
        Set<Path> antes = archivosTemporales();

        subir(p, "contrato.pdf", pdfConTexto("Contrato de compraventa.")).andExpect(status().isServiceUnavailable());

        assertThat(documentosDe(p.inmueble)).isZero();
        assertThat(archivosTemporales()).isSubsetOf(antes);
    }

    @Test
    void unPdfConJavaScriptSeRechazaAunqueElAntivirusNoLoVea() throws Exception {
        Preparacion p = preparar("SEG-4");
        Set<Path> antes = archivosTemporales();

        subir(p, "contrato.pdf", pdfConJavaScript())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("JavaScript")));

        assertThat(documentosDe(p.inmueble)).isZero();
        assertThat(archivosTemporales()).isSubsetOf(antes);
    }

    @Test
    void unArchivoQueSoloLlevaLaExtensionPdfSeRechaza() throws Exception {
        Preparacion p = preparar("SEG-5");
        byte[] imagen = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

        subir(p, "foto.pdf", imagen)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El archivo no es un PDF válido."));

        assertThat(documentosDe(p.inmueble)).isZero();
    }

    // --- ayudas -------------------------------------------------------------------------------------------------

    private record Preparacion(Usuario asesor, Inmueble inmueble) {
    }

    private Preparacion preparar(String codigo) {
        Inmobiliaria inmobiliaria = crearInmobiliaria("Inmobiliaria " + codigo);
        return new Preparacion(crearUsuario(inmobiliaria, Rol.ASESOR), crearInmueble(inmobiliaria, codigo));
    }

    private org.springframework.test.web.servlet.ResultActions subir(Preparacion p, String nombre, byte[] contenido)
            throws Exception {
        return mvc.perform(multipart("/api/documentos")
                .file(new MockMultipartFile("archivo", nombre, "application/pdf", contenido))
                .param("inmuebleId", p.inmueble.getId().toString())
                .header("Authorization", bearer(p.asesor)));
    }

    private int documentosDe(Inmueble inmueble) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM documento WHERE inmueble_id = ?", Integer.class,
                inmueble.getId());
        return n == null ? 0 : n;
    }

    private Set<Path> archivosTemporales() throws IOException {
        Path directorio = Path.of(propiedades.directorioTemporal());
        if (!Files.isDirectory(directorio)) {
            return Set.of();
        }
        try (Stream<Path> archivos = Files.list(directorio)) {
            return new HashSet<>(archivos.toList());
        }
    }

    private static byte[] pdfConJavaScript() throws IOException {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage());
            documento.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('hola');"));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }
}
