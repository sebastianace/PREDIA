package co.predia.usuarios;

import co.predia.inmobiliaria.ParametrosInmobiliaria;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Los correos de invitación. El texto lo escribe Predia con una plantilla (no la IA): al propietario se le trata de
 * «usted», al personal de «tú». El envío lo dispara siempre una persona (el asesor o el ADMIN).
 */
@Component
public class CorreoInvitaciones {

    private static final Logger log = LoggerFactory.getLogger(CorreoInvitaciones.class);
    static final String MARCADOR_ENLACE = "[enlace personal, vigente 72 horas]";
    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'a las' h:mm a", Locale.forLanguageTag("es-CO"))
            .withZone(ZoneId.of("America/Bogota"));

    public record Correo(String para, String asunto, String cuerpo) {
    }

    private final JavaMailSender correo;
    private final ParametrosInmobiliariaRepository parametros;
    private final PropiedadesUsuarios propiedades;

    public CorreoInvitaciones(JavaMailSender correo, ParametrosInmobiliariaRepository parametros,
                              PropiedadesUsuarios propiedades) {
        this.correo = correo;
        this.parametros = parametros;
        this.propiedades = propiedades;
    }

    /** El correo tal como se enviará; sin enlace, el texto lleva un marcador (para la vista previa). */
    public Correo redactar(Rol rol, String nombre, String email, String inmobiliaria, String venta,
                           String nombreQuienInvita, String enlace, Instant expiraEn, boolean recuperar) {
        String link = enlace == null ? MARCADOR_ENLACE : enlace;
        String vence = expiraEn == null ? "en 72 horas" : "el " + FECHA.format(expiraEn);
        if (rol == Rol.COMPRADOR) {
            String asunto = recuperar ? "Nueva contraseña para su portal de " + inmobiliaria
                    : "Vea los inmuebles que " + inmobiliaria + " le compartió";
            String cuerpo = """
                    Hola, %s:

                    %s

                    %s

                    Este enlace es personal, sirve una sola vez y vence %s. Después podrá entrar cuando quiera con su \
                    correo y la contraseña que elija.

                    Si no esperaba este mensaje, puede ignorarlo.

                    %s
                    %s""".formatted(nombre,
                    recuperar ? "Para elegir una contraseña nueva para su portal, abra este enlace:"
                            : ("En su portal de %s puede ver los inmuebles que su asesor le compartió, simular su compra "
                            + "y escribirle directamente. Para crear su contraseña, abra este enlace:").formatted(inmobiliaria),
                    link, vence, nombreQuienInvita, inmobiliaria);
            return new Correo(email, asunto, cuerpo);
        }
        if (rol == Rol.PROPIETARIO) {
            String asunto = recuperar ? "Nueva contraseña para su portal de " + inmobiliaria
                    : "Siga " + venta + " desde su portal";
            String cuerpo = """
                    Hola, %s:

                    %s

                    %s

                    Este enlace es personal, sirve una sola vez y vence %s. Después podrá entrar cuando quiera con su \
                    correo y la contraseña que elija.

                    Si no esperaba este mensaje, puede ignorarlo.

                    %s
                    %s""".formatted(nombre,
                    recuperar ? "Para elegir una contraseña nueva para su portal, abra este enlace:"
                            : ("En su portal de %s puede ver qué documentos faltan para firmar %s, cuáles "
                            + "están por vencer y enviarlos directamente. Para crear su contraseña, abra este enlace:")
                            .formatted(inmobiliaria, venta),
                    link, vence, nombreQuienInvita, inmobiliaria);
            return new Correo(email, asunto, cuerpo);
        }
        String asunto = recuperar ? "Nueva contraseña para Predia" : "Te invitaron a Predia en " + inmobiliaria;
        String cuerpo = """
                Hola, %s:

                %s

                %s

                El enlace es personal, sirve una sola vez y vence %s.

                %s
                %s""".formatted(nombre,
                recuperar ? "Para elegir una contraseña nueva, abre este enlace:"
                        : "%s te dio acceso a Predia como %s. Para crear tu contraseña, abre este enlace:"
                        .formatted(nombreQuienInvita, rol == Rol.ADMIN ? "administrador" : "asesor"),
                link, vence, nombreQuienInvita, inmobiliaria);
        return new Correo(email, asunto, cuerpo);
    }

    /** Envía el correo. Si falla, devuelve false: la invitación sigue vigente y el enlace se puede compartir aparte. */
    public boolean enviar(Long inmobiliariaId, Correo c, String responderA) {
        try {
            String remitente = parametros.findById(inmobiliariaId).map(ParametrosInmobiliaria::getEmailRemitente)
                    .filter(r -> r != null && !r.isBlank()).orElse(propiedades.remitente());
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(remitente);
            if (responderA != null) {
                m.setReplyTo(responderA);
            }
            m.setTo(c.para());
            m.setSubject(c.asunto());
            m.setText(c.cuerpo());
            correo.send(m);
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar la invitación: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
