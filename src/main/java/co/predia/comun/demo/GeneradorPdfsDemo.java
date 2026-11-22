package co.predia.comun.demo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera los PDF de ejemplo de la demo: un contrato, una escritura, un reglamento y dos certificados. TODOS son
 * FICTICIOS (personas, números y notarías inventados). Se regeneran cuando haga falta para que las fechas de los
 * certificados queden relativas a hoy:
 *
 * <pre>./mvnw -q compile exec:java -Dexec.mainClass=co.predia.comun.demo.GeneradorPdfsDemo</pre>
 */
public final class GeneradorPdfsDemo {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));
    private static final float MARGEN = 56;
    private static final float TAMANO = 10.5f;
    private static final float INTERLINEA = 14.5f;
    /** Entre párrafos va un espacio grande, que PDFBox extrae como una línea en blanco (así se detectan las secciones). */
    private static final float ENTRE_PARRAFOS = INTERLINEA * 2.2f;

    private GeneradorPdfsDemo() {
    }

    public static void main(String[] args) throws IOException {
        Path destino = Path.of(args.length > 0 ? args[0] : "demo/pdfs");
        for (Path p : generar(destino, LocalDate.now())) {
            System.out.println("Generado: " + p);
        }
    }

    public static List<Path> generar(Path destino, LocalDate hoy) throws IOException {
        Files.createDirectories(destino);
        List<Path> generados = new ArrayList<>();
        generados.add(escribir(destino.resolve("contrato-arrendamiento.pdf"), contrato(hoy)));
        generados.add(escribir(destino.resolve("escritura-publica-4521.pdf"), escritura()));
        generados.add(escribir(destino.resolve("reglamento-propiedad-horizontal.pdf"), reglamento()));
        generados.add(escribir(destino.resolve("certificado-tradicion-y-libertad.pdf"), certificadoTradicion(hoy)));
        generados.add(escribir(destino.resolve("paz-y-salvo-administracion.pdf"), pazYSalvoAdministracion(hoy)));
        return generados;
    }

    private static List<String> contrato(LocalDate hoy) {
        return List.of(
                "CONTRATO DE ARRENDAMIENTO DE VIVIENDA URBANA",
                "Entre los suscritos, Altos Inmobiliaria, en adelante EL ARRENDADOR, y el señor Carlos Mendoza, en "
                        + "adelante EL ARRENDATARIO, se celebra el presente contrato de arrendamiento, que se regula por "
                        + "las siguientes cláusulas. (Documento ficticio, solo para demostración.)",
                "CLÁUSULA PRIMERA. OBJETO",
                "El arrendador entrega al arrendatario, a título de arrendamiento, el inmueble ubicado en la Calle 30 "
                        + "# 26-15, Torre 2, Apartamento 1102, del conjunto residencial Cañaveral, en el municipio de "
                        + "Floridablanca, Santander. El inmueble incluye un (1) parqueadero cubierto y un depósito.",
                "CLÁUSULA SEGUNDA. CANON DE ARRENDAMIENTO",
                "El canon mensual de arrendamiento se fija en la suma de un millón ochocientos mil pesos ($1.800.000) "
                        + "moneda corriente, pagaderos dentro de los primeros cinco (5) días de cada mes. El valor de la "
                        + "administración será asumido por el arrendatario.",
                "CLÁUSULA TERCERA. DURACIÓN",
                "El presente contrato tendrá una duración de doce (12) meses, contados a partir del " + FECHA.format(hoy)
                        + ", prorrogables automáticamente por periodos iguales, salvo aviso escrito de cualquiera de las "
                        + "partes con no menos de treinta (30) días de anticipación.",
                "CLÁUSULA CUARTA. MASCOTAS",
                "El arrendatario podrá tener mascotas de tamaño pequeño o mediano, y responderá por los daños que "
                        + "ellas causen al inmueble o a las zonas comunes.",
                "CLÁUSULA QUINTA. DEPÓSITO",
                "El arrendatario entrega en este acto, a título de depósito, la suma equivalente a un (1) mes de "
                        + "canon, que será devuelta al finalizar el contrato, previa verificación del estado del inmueble.",
                "Para constancia se firma en Bucaramanga, el " + FECHA.format(hoy) + ".");
    }

    private static List<String> escritura() {
        return List.of(
                "ESCRITURA PÚBLICA NÚMERO 4521",
                "En la Notaría Ocho del Círculo de Bucaramanga, a los doce (12) días del mes de octubre del año dos mil "
                        + "veinte (2020), compareció el señor Jorge Ramírez Pardo, identificado con cédula de ciudadanía "
                        + "número 91.234.567 de Bucaramanga. (Documento ficticio, solo para demostración.)",
                "PRIMERO. OBJETO",
                "El compareciente manifiesta que es propietario del Apartamento 1102, Torre 2, del conjunto residencial "
                        + "Cañaveral, ubicado en la Calle 30 número 26-15 del municipio de Floridablanca, Santander, con "
                        + "un área construida de setenta y ocho metros cuadrados (78 m2).",
                "SEGUNDO. TRADICIÓN",
                "El inmueble fue adquirido mediante compraventa a la constructora Altos S.A.S., según consta en la "
                        + "Escritura Pública número 1890 de la Notaría Tercera de Bucaramanga, del año dos mil quince "
                        + "(2015), debidamente registrada.",
                "TERCERO. LIBERTAD",
                "El compareciente declara que el inmueble se encuentra libre de embargos, demandas y gravámenes.");
    }

    private static List<String> reglamento() {
        return List.of(
                "REGLAMENTO DE PROPIEDAD HORIZONTAL - CONJUNTO RESIDENCIAL CAÑAVERAL",
                "Conjunto residencial ficticio ubicado en Floridablanca, Santander. (Documento ficticio, solo para "
                        + "demostración.)",
                "ARTÍCULO 1. ZONAS COMUNES",
                "Son zonas comunes del conjunto la piscina, el gimnasio, la zona infantil, el salón social y los "
                        + "parqueaderos de visitantes. Su uso se regula por este reglamento y por las decisiones de la "
                        + "asamblea de copropietarios.",
                "ARTÍCULO 2. CUOTA DE ADMINISTRACIÓN",
                "Cada propietario pagará una cuota de administración mensual, dentro de los primeros diez (10) días de "
                        + "cada mes. El retraso causa intereses de mora a la tasa máxima legal.",
                "ARTÍCULO 3. MASCOTAS",
                "Se permiten mascotas. Los propietarios deben recoger sus deyecciones, mantenerlas con correa en las "
                        + "zonas comunes y responder por los daños que causen.",
                "ARTÍCULO 4. PARQUEADEROS",
                "Cada apartamento tiene derecho a un (1) parqueadero cubierto asignado. Los parqueaderos de visitantes "
                        + "son de uso temporal, por un máximo de seis (6) horas.",
                "ARTÍCULO 5. HORARIO DE SILENCIO",
                "Entre las diez de la noche y las seis de la mañana se debe guardar silencio en todo el conjunto.",
                "ARTÍCULO 6. PAZ Y SALVO",
                "Para vender un apartamento, el propietario debe estar a paz y salvo por todo concepto con la "
                        + "administración, y solicitar el certificado correspondiente al administrador.");
    }

    private static List<String> certificadoTradicion(LocalDate hoy) {
        LocalDate expedicion = hoy.minusDays(3);
        return List.of(
                "CERTIFICADO DE TRADICIÓN Y LIBERTAD",
                "Oficina de Registro de Instrumentos Públicos de Bucaramanga. (Documento ficticio, solo para "
                        + "demostración.)",
                "Fecha de expedición: " + FECHA.format(expedicion),
                "Matrícula inmobiliaria: 300-000000 (ficticia). Inmueble: Apartamento 1102, Torre 2, conjunto "
                        + "residencial Cañaveral, Calle 30 número 26-15, Floridablanca.",
                "ANOTACIÓN 1. COMPRAVENTA",
                "Escritura Pública 1890 de la Notaría Tercera de Bucaramanga, del año 2015. De: Altos S.A.S. A: Jorge "
                        + "Ramírez Pardo.",
                "ESTADO ACTUAL",
                "El inmueble no registra embargos, demandas ni hipotecas vigentes a la fecha de expedición de este "
                        + "certificado.");
    }

    private static List<String> pazYSalvoAdministracion(LocalDate hoy) {
        return List.of(
                "PAZ Y SALVO DE ADMINISTRACIÓN",
                "Conjunto residencial Cañaveral - Administración. (Documento ficticio, solo para demostración.)",
                "Fecha de expedición: " + FECHA.format(hoy),
                "La administración del conjunto residencial Cañaveral certifica que el propietario del Apartamento "
                        + "1102, Torre 2, señor Jorge Ramírez Pardo, se encuentra a paz y salvo por todo concepto de "
                        + "cuotas de administración, hasta el mes de " + hoy.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, Locale.forLanguageTag("es-CO")) + " de " + hoy.getYear() + ".");
    }

    // --- Escritura del PDF: texto con salto de línea automático y varias páginas ---

    private static Path escribir(Path archivo, List<String> parrafos) throws IOException {
        PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float ancho = PDRectangle.A4.getWidth() - 2 * MARGEN;

        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = null;
            PDPageContentStream contenido = null;
            float y = 0;
            for (String parrafo : parrafos) {
                boolean titulo = esTitulo(parrafo);
                PDFont fuente = titulo ? negrita : normal;
                List<String> lineas = partir(sinCaracteresRaros(parrafo), fuente, ancho);
                float alto = lineas.size() * INTERLINEA;
                if (pagina == null || y - alto < MARGEN) {
                    if (contenido != null) {
                        contenido.close();
                    }
                    pagina = new PDPage(PDRectangle.A4);
                    documento.addPage(pagina);
                    contenido = new PDPageContentStream(documento, pagina);
                    y = PDRectangle.A4.getHeight() - MARGEN;
                }
                for (String linea : lineas) {
                    contenido.beginText();
                    contenido.setFont(fuente, TAMANO);
                    contenido.newLineAtOffset(MARGEN, y);
                    contenido.showText(linea);
                    contenido.endText();
                    y -= INTERLINEA;
                }
                y -= ENTRE_PARRAFOS - INTERLINEA;
            }
            if (contenido != null) {
                contenido.close();
            }
            documento.save(archivo.toFile());
        }
        return archivo;
    }

    private static boolean esTitulo(String parrafo) {
        return parrafo.length() < 80 && parrafo.equals(parrafo.toUpperCase(Locale.forLanguageTag("es-CO")));
    }

    private static List<String> partir(String texto, PDFont fuente, float ancho) throws IOException {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (fuente.getStringWidth(candidata) / 1000 * TAMANO > ancho && !actual.isEmpty()) {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(candidata);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return lineas;
    }

    /** Las fuentes estándar solo soportan Latin-1: se cambia lo que no esté (guiones largos, comillas curvas). */
    private static String sinCaracteresRaros(String texto) {
        String t = texto.replace('–', '-').replace('—', '-').replace('“', '"').replace('”', '"').replace('’', '\'');
        StringBuilder sb = new StringBuilder();
        for (char c : t.toCharArray()) {
            sb.append(c <= 0xFF ? c : Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).charAt(0));
        }
        return sb.toString();
    }
}
