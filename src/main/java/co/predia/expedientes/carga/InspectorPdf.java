package co.predia.expedientes.carga;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Revisión estructural de un PDF antes de aceptarlo (no reemplaza al antivirus, lo complementa): comprueba que sea
 * un PDF de verdad y rechaza el contenido activo que un expediente inmobiliario no necesita (JavaScript, acciones que
 * lanzan programas y archivos incrustados). Recorre todos los objetos del documento con PDFBox, así que también ve
 * lo que va comprimido en flujos de objetos o con nombres ofuscados; no depende de buscar texto en los bytes.
 */
@Component
public class InspectorPdf {

    /** La especificación admite hasta 1.024 bytes de basura antes de la firma «%PDF-». */
    private static final int BYTES_BUSQUEDA_FIRMA = 1024;
    private static final byte[] FIRMA = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int PROFUNDIDAD_MAXIMA = 64;

    private static final COSName JS = COSName.getPDFName("JS");
    private static final COSName JAVASCRIPT = COSName.getPDFName("JavaScript");
    private static final COSName LAUNCH = COSName.getPDFName("Launch");
    private static final COSName EF = COSName.getPDFName("EF");
    private static final COSName EMBEDDED_FILE = COSName.getPDFName("EmbeddedFile");
    private static final COSName EMBEDDED_FILES = COSName.getPDFName("EmbeddedFiles");

    /** ¿Empieza el archivo con la firma de un PDF? La extensión y el tipo que declara el cliente no lo garantizan. */
    public boolean tieneFirmaPdf(Path ruta) throws IOException {
        byte[] inicio = new byte[BYTES_BUSQUEDA_FIRMA];
        int leidos;
        try (InputStream entrada = Files.newInputStream(ruta)) {
            leidos = entrada.readNBytes(inicio, 0, inicio.length);
        }
        for (int i = 0; i + FIRMA.length <= leidos; i++) {
            boolean coincide = true;
            for (int j = 0; j < FIRMA.length && coincide; j++) {
                coincide = inicio[i + j] == FIRMA[j];
            }
            if (coincide) {
                return true;
            }
        }
        return false;
    }

    /** El motivo (para el usuario) por el que el PDF no se acepta, o vacío si no tiene contenido activo. */
    public Optional<String> motivoDeRechazo(PDDocument documento) {
        Map<COSObjectKey, Long> objetos = documento.getDocument().getXrefTable();
        for (COSObjectKey clave : objetos.keySet()) {
            COSBase objeto = documento.getDocument().getObjectFromPool(clave).getObject();
            Optional<String> motivo = revisar(objeto, 0);
            if (motivo.isPresent()) {
                return motivo;
            }
        }
        // El diccionario del trailer también puede llevar entradas directas.
        return revisar(documento.getDocument().getTrailer(), 0);
    }

    /**
     * Revisa un valor sin seguir las referencias indirectas: cada objeto indirecto se revisa una sola vez por su
     * propia entrada en la tabla de referencias, y así no hay ciclos que recorrer.
     */
    private Optional<String> revisar(COSBase valor, int profundidad) {
        if (valor == null || profundidad > PROFUNDIDAD_MAXIMA || valor instanceof COSObject) {
            return Optional.empty();
        }
        if (valor instanceof COSDictionary diccionario) {
            Optional<String> motivo = motivoDelDiccionario(diccionario);
            if (motivo.isPresent()) {
                return motivo;
            }
            for (Map.Entry<COSName, COSBase> entrada : diccionario.entrySet()) {
                Optional<String> interno = revisar(entrada.getValue(), profundidad + 1);
                if (interno.isPresent()) {
                    return interno;
                }
            }
        } else if (valor instanceof COSArray arreglo) {
            for (COSBase elemento : arreglo) {
                Optional<String> interno = revisar(elemento, profundidad + 1);
                if (interno.isPresent()) {
                    return interno;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> motivoDelDiccionario(COSDictionary diccionario) {
        if (diccionario.containsKey(JS) || diccionario.containsKey(JAVASCRIPT)) {
            return Optional.of("El PDF contiene JavaScript.");
        }
        COSBase accion = diccionario.getItem(COSName.S);
        if (JAVASCRIPT.equals(accion)) {
            return Optional.of("El PDF contiene JavaScript.");
        }
        if (LAUNCH.equals(accion)) {
            return Optional.of("El PDF intenta abrir otros programas o archivos.");
        }
        if (diccionario.containsKey(EF) || diccionario.containsKey(EMBEDDED_FILES)
                || EMBEDDED_FILE.equals(diccionario.getItem(COSName.TYPE))) {
            return Optional.of("El PDF trae archivos incrustados.");
        }
        return Optional.empty();
    }
}
