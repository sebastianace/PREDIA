package co.predia.expedientes.carga;

import co.predia.comun.web.EscaneoNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Escáner con ClamAV: envía el archivo al servicio {@code clamd} con el comando INSTREAM (el archivo viaja por el
 * socket, así que {@code clamd} no necesita ver el disco del servidor, y por eso funciona igual con Docker).
 * Protocolo: «zINSTREAM\0», trozos con su longitud (4 bytes, big-endian) y un trozo final de longitud 0; la
 * respuesta es «stream: OK», «stream: NombreDeLaAmenaza FOUND» o «... ERROR».
 */
@Component
@ConditionalOnProperty(name = "predia.expedientes.escaneo.modo", havingValue = "CLAMAV")
public class EscanerClamAv implements EscanerArchivos {

    private static final Logger log = LoggerFactory.getLogger(EscanerClamAv.class);
    private static final int TAMANO_TROZO = 8192;
    private static final int TIEMPO_CONEXION_MS = 3000;

    private final PropiedadesEscaneo propiedades;

    public EscanerClamAv(PropiedadesEscaneo propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public Resultado escanear(Path archivo) {
        try {
            return consultar(archivo);
        } catch (IOException | RuntimeException e) {
            // Se registra solo el tipo del fallo: nada del archivo ni de su dueño.
            log.warn("No se pudo consultar a ClamAV en {}:{} ({})", propiedades.host(), propiedades.puerto(),
                    e.getClass().getSimpleName());
            if (propiedades.obligatorio()) {
                throw new EscaneoNoDisponibleException(
                        "No se pudo verificar el archivo con el antivirus. Intenta de nuevo en unos minutos.", e);
            }
            return Resultado.sinVerificar();
        }
    }

    private Resultado consultar(Path archivo) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(propiedades.host(), propiedades.puerto()), TIEMPO_CONEXION_MS);
            socket.setSoTimeout(propiedades.tiempoMaximoSegundos() * 1000);

            DataOutputStream salida = new DataOutputStream(socket.getOutputStream());
            salida.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] trozo = new byte[TAMANO_TROZO];
            try (InputStream entrada = Files.newInputStream(archivo)) {
                int leidos;
                while ((leidos = entrada.read(trozo)) > 0) {
                    salida.writeInt(leidos);
                    salida.write(trozo, 0, leidos);
                }
            }
            salida.writeInt(0);
            salida.flush();

            return interpretar(leerRespuesta(socket.getInputStream()));
        }
    }

    /** La respuesta termina en un byte 0 (comando con «z») o al cerrarse la conexión. */
    private static String leerRespuesta(InputStream entrada) throws IOException {
        StringBuilder respuesta = new StringBuilder();
        int b;
        while ((b = entrada.read()) > 0) {
            respuesta.append((char) b);
            if (respuesta.length() > 1024) {
                throw new IOException("Respuesta de ClamAV demasiado larga");
            }
        }
        return respuesta.toString().trim();
    }

    /** Visible para las pruebas. */
    static Resultado interpretar(String respuesta) throws IOException {
        if (respuesta.endsWith("OK")) {
            return Resultado.limpio();
        }
        if (respuesta.endsWith("FOUND")) {
            String sinPrefijo = respuesta.startsWith("stream:") ? respuesta.substring("stream:".length()) : respuesta;
            String amenaza = sinPrefijo.substring(0, sinPrefijo.length() - "FOUND".length()).trim();
            return Resultado.infectado(amenaza.isEmpty() ? "desconocida" : amenaza);
        }
        // ERROR u otra cosa: no se puede dar por limpio.
        throw new IOException("Respuesta inesperada de ClamAV: " + respuesta);
    }
}
