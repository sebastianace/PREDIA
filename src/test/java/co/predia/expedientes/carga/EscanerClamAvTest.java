package co.predia.expedientes.carga;

import co.predia.comun.web.EscaneoNoDisponibleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El protocolo INSTREAM contra un clamd falso en un socket local: no hace falta ClamAV ni Docker. El «archivo
 * infectado» es el texto de prueba EICAR, que los antivirus reconocen y que no es un virus real.
 */
class EscanerClamAvTest {

    private static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @TempDir
    Path directorio;

    private ServerSocket servidor;

    @AfterEach
    void cerrar() throws IOException {
        if (servidor != null) {
            servidor.close();
        }
    }

    @Test
    void unArchivoLimpioSeDaPorLimpioYElServidorRecibeElArchivoCompleto() throws Exception {
        byte[] contenido = new byte[20_000];
        java.util.Arrays.fill(contenido, (byte) 'a');
        Path archivo = directorio.resolve("limpio.pdf");
        Files.write(archivo, contenido);

        CompletableFuture<byte[]> recibido = clamdFalso("stream: OK");
        EscanerArchivos.Resultado resultado = escaner(true).escanear(archivo);

        assertThat(resultado.estaInfectado()).isFalse();
        assertThat(resultado.verificado()).isTrue();
        assertThat(recibido.get()).isEqualTo(contenido);
    }

    @Test
    void unArchivoInfectadoDevuelveElNombreDeLaAmenaza() throws Exception {
        Path archivo = directorio.resolve("eicar.pdf");
        Files.writeString(archivo, EICAR);

        CompletableFuture<byte[]> recibido = clamdFalso("stream: Eicar-Test-Signature FOUND");
        EscanerArchivos.Resultado resultado = escaner(true).escanear(archivo);

        assertThat(resultado.estaInfectado()).isTrue();
        assertThat(resultado.amenaza()).isEqualTo("Eicar-Test-Signature");
        assertThat(new String(recibido.get(), StandardCharsets.US_ASCII)).isEqualTo(EICAR);
    }

    @Test
    void sinAntivirusYObligatorioElArchivoNoSeAcepta() throws Exception {
        Path archivo = directorio.resolve("x.pdf");
        Files.writeString(archivo, "contenido");
        int puertoCerrado = puertoLibre();

        assertThatThrownBy(() -> new EscanerClamAv(
                new PropiedadesEscaneo("CLAMAV", "localhost", puertoCerrado, true, 2)).escanear(archivo))
                .isInstanceOf(EscaneoNoDisponibleException.class);
    }

    @Test
    void sinAntivirusYNoObligatorioSeAceptaPeroQuedaSinVerificar() throws Exception {
        Path archivo = directorio.resolve("x.pdf");
        Files.writeString(archivo, "contenido");
        int puertoCerrado = puertoLibre();

        EscanerArchivos.Resultado resultado = new EscanerClamAv(
                new PropiedadesEscaneo("CLAMAV", "localhost", puertoCerrado, false, 2)).escanear(archivo);

        assertThat(resultado.estaInfectado()).isFalse();
        assertThat(resultado.verificado()).isFalse();
    }

    @Test
    void unaRespuestaDeErrorNuncaSeDaPorLimpia() throws Exception {
        Path archivo = directorio.resolve("x.pdf");
        Files.writeString(archivo, "contenido");

        clamdFalso("INSTREAM size limit exceeded. ERROR");

        assertThatThrownBy(() -> escaner(true).escanear(archivo)).isInstanceOf(EscaneoNoDisponibleException.class);
    }

    @Test
    void elEscanerDesactivadoNoVerificaNada() {
        EscanerArchivos.Resultado resultado = new EscanerDesactivado().escanear(directorio.resolve("no-existe.pdf"));

        assertThat(resultado.estaInfectado()).isFalse();
        assertThat(resultado.verificado()).isFalse();
    }

    // --- ayudas -------------------------------------------------------------------------------------------------

    private EscanerClamAv escaner(boolean obligatorio) {
        return new EscanerClamAv(new PropiedadesEscaneo("CLAMAV", "localhost", servidor.getLocalPort(), obligatorio, 5));
    }

    private static int puertoLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Un clamd mínimo: lee «zINSTREAM\0», junta los trozos hasta el de longitud 0 y responde. Devuelve (cuando
     * termina) los bytes que recibió, para comprobar que el archivo viajó completo.
     */
    private CompletableFuture<byte[]> clamdFalso(String respuesta) throws IOException {
        servidor = new ServerSocket(0);
        return CompletableFuture.supplyAsync(() -> {
            try (Socket cliente = servidor.accept()) {
                DataInputStream entrada = new DataInputStream(cliente.getInputStream());
                byte[] comando = new byte["zINSTREAM\0".length()];
                entrada.readFully(comando);
                assertThat(new String(comando, StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");

                ByteArrayOutputStream recibido = new ByteArrayOutputStream();
                int longitud;
                while ((longitud = entrada.readInt()) > 0) {
                    byte[] trozo = new byte[longitud];
                    entrada.readFully(trozo);
                    recibido.write(trozo);
                }
                OutputStream salida = cliente.getOutputStream();
                salida.write((respuesta + "\0").getBytes(StandardCharsets.US_ASCII));
                salida.flush();
                return recibido.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
