package co.predia.comun.seguridad;

import co.predia.comun.web.DemasiadosIntentosException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno a los intentos fallidos de entrada (contraseñas y enlaces de invitación adivinados a fuerza bruta).
 *
 * <p>Cuenta solo los <b>fallos</b>: al superar el máximo dentro de la ventana, esa clave queda bloqueada hasta que la
 * ventana vence. Se lleva en memoria (una sola instancia de Predia); si se reinicia el servidor, los contadores
 * empiezan de cero. Un acierto borra el contador del correo, así que quien se equivoca un par de veces y luego
 * entra no queda penalizado.</p>
 *
 * <p>Se verifica el bloqueo <b>antes</b> de mirar la contraseña: mientras una clave está bloqueada ni la contraseña
 * correcta entra, y la respuesta no revela si acertó.</p>
 */
@Component
public class LimitadorIntentos {

    private static final int LIMITE_PARA_PURGAR = 10_000;

    private record Ventana(Instant inicio, int fallos) {
    }

    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    public LimitadorIntentos(PropiedadesSeguridad propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    // --- Inicio de sesión: se limita por correo y, aparte, por dirección de origen ---

    public void verificarLogin(String correo, String ip) {
        verificar(claveCorreo(correo), propiedades.intentosPorCorreo());
        verificar("login-ip:" + ip, propiedades.intentosPorIp());
    }

    public void fallaLogin(String correo, String ip) {
        registrarFallo(claveCorreo(correo));
        registrarFallo("login-ip:" + ip);
    }

    public void exitoLogin(String correo) {
        ventanas.remove(claveCorreo(correo));
    }

    // --- Enlaces de invitación: solo por dirección de origen (el token es lo que se intenta adivinar) ---

    public void verificarEnlace(String ip) {
        verificar("enlace-ip:" + ip, propiedades.intentosEnlacePorIp());
    }

    public void fallaEnlace(String ip) {
        registrarFallo("enlace-ip:" + ip);
    }

    /** Solo para pruebas: deja todos los contadores en cero. */
    public void reiniciar() {
        ventanas.clear();
    }

    private static String claveCorreo(String correo) {
        return "login-correo:" + (correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT));
    }

    private void verificar(String clave, int maximo) {
        Ventana v = ventanas.get(clave);
        if (v == null) {
            return;
        }
        Instant vence = v.inicio().plus(duracion());
        Instant ahora = reloj.instant();
        if (!ahora.isBefore(vence)) {
            ventanas.remove(clave, v);
            return;
        }
        if (v.fallos() >= maximo) {
            long segundos = Math.max(1, Duration.between(ahora, vence).toSeconds() + 1);
            throw new DemasiadosIntentosException(segundos);
        }
    }

    private void registrarFallo(String clave) {
        Instant ahora = reloj.instant();
        if (ventanas.size() > LIMITE_PARA_PURGAR) {
            purgar(ahora);
        }
        ventanas.merge(clave, new Ventana(ahora, 1), (actual, nueva) ->
                ahora.isBefore(actual.inicio().plus(duracion()))
                        ? new Ventana(actual.inicio(), actual.fallos() + 1)
                        : nueva);
    }

    private void purgar(Instant ahora) {
        ventanas.entrySet().removeIf(e -> !ahora.isBefore(e.getValue().inicio().plus(duracion())));
    }

    private Duration duracion() {
        return Duration.ofMinutes(propiedades.ventanaMinutos());
    }
}
