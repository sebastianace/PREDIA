package co.predia.cierre.recordatorios;

import co.predia.cierre.negocio.EstadoNegocio;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.comun.web.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Trabajo diario: por cada negocio abierto que tenga documentos pendientes o por vencer, deja un recordatorio
 * SUGERIDO en la bandeja del asesor. NUNCA envía nada: el envío exige la aprobación explícita del asesor.
 */
@Component
public class GeneradorDiario {

    private static final Logger log = LoggerFactory.getLogger(GeneradorDiario.class);

    /** Para no abrumar al propietario, no se sugiere otro recordatorio si se le envió uno hace poco. */
    static final int DIAS_ENTRE_RECORDATORIOS = 3;

    private final NegocioRepository negocios;
    private final RecordatorioRepository recordatorios;
    private final ServicioRecordatorios servicio;
    private final Clock reloj;

    public GeneradorDiario(NegocioRepository negocios, RecordatorioRepository recordatorios,
                           ServicioRecordatorios servicio, Clock reloj) {
        this.negocios = negocios;
        this.recordatorios = recordatorios;
        this.servicio = servicio;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${predia.cierre.cron-recordatorios:0 0 7 * * *}", zone = "America/Bogota")
    public void ejecutar() {
        int creados = generarParaTodos(LocalDate.now(reloj));
        log.info("Trabajo diario de recordatorios: {} borrador(es) nuevo(s) en las bandejas de los asesores.", creados);
    }

    /** Devuelve cuántos recordatorios SUGERIDOS se crearon. */
    public int generarParaTodos(LocalDate hoy) {
        int creados = 0;
        for (Negocio negocio : negocios.findByEstado(EstadoNegocio.ABIERTO)) {
            try {
                if (yaHayUnoPendiente(negocio) || seEnvioHacePoco(negocio, hoy)) {
                    continue;
                }
                servicio.generar(negocio, hoy);
                creados++;
            } catch (ReglaNegocioException sinPendientes) {
                // Nada que pedir: el negocio está al día.
            } catch (Exception e) {
                log.warn("No se pudo generar el recordatorio del negocio {}: {}", negocio.getId(), e.getMessage());
            }
        }
        return creados;
    }

    private boolean yaHayUnoPendiente(Negocio negocio) {
        return recordatorios.existsByNegocioIdAndEstado(negocio.getId(), EstadoRecordatorio.SUGERIDO);
    }

    private boolean seEnvioHacePoco(Negocio negocio, LocalDate hoy) {
        return recordatorios.findFirstByNegocioIdAndEstadoOrderByEnviadoEnDesc(negocio.getId(),
                        EstadoRecordatorio.ENVIADO)
                .map(r -> r.getEnviadoEn() != null && ChronoUnit.DAYS.between(
                        r.getEnviadoEn().atZone(ZoneId.of("America/Bogota")).toLocalDate(), hoy)
                        < DIAS_ENTRE_RECORDATORIOS)
                .orElse(false);
    }
}
