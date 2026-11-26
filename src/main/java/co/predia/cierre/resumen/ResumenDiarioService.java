package co.predia.cierre.resumen;

import co.predia.cierre.negocio.EstadoNegocio;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.NegocioRepository;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.cierre.recordatorios.EstadoRecordatorio;
import co.predia.cierre.recordatorios.RecordatorioRepository;
import co.predia.cierre.reglas.EstadoDocumental;
import co.predia.cierre.reglas.EstadoRequisito;
import co.predia.cierre.reglas.ServicioEstadoDocumental;
import co.predia.comprador.ServicioSolicitudes;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Resumen diario del asesor: qué negocios están bloqueados y qué documentos vencen esta semana. Se calcula al
 * consultarlo, con las mismas reglas de siempre. El ADMIN ve toda la inmobiliaria; el asesor, sus negocios.
 */
@Service
public class ResumenDiarioService {

    static final int DIAS_DE_LA_SEMANA = 7;

    private final NegocioRepository negocios;
    private final ServicioEstadoDocumental estadoDocumental;
    private final InmuebleRepository inmuebles;
    private final PropietarioRepository propietarios;
    private final RecordatorioRepository recordatorios;
    private final ServicioSolicitudes solicitudes;
    private final Clock reloj;

    public ResumenDiarioService(NegocioRepository negocios, ServicioEstadoDocumental estadoDocumental,
                                InmuebleRepository inmuebles, PropietarioRepository propietarios,
                                RecordatorioRepository recordatorios, ServicioSolicitudes solicitudes,
                                Clock reloj) {
        this.negocios = negocios;
        this.estadoDocumental = estadoDocumental;
        this.inmuebles = inmuebles;
        this.propietarios = propietarios;
        this.recordatorios = recordatorios;
        this.solicitudes = solicitudes;
        this.reloj = reloj;
    }

    public record NegocioBloqueado(Long negocioId, String inmueble, String propietario, LocalDate fechaFirma,
                                   String semaforo, int faltan, int vencidos, int porVencer, List<String> pendientes) {
    }

    public record Vencimiento(Long negocioId, String inmueble, String documento, LocalDate venceEl,
                              long diasRestantes) {
    }

    public record Resumen(LocalDate fecha, int negociosAbiertos, List<NegocioBloqueado> bloqueados,
                          List<Vencimiento> vencenEstaSemana, long recordatoriosPorAprobar,
                          long solicitudesPorAtender) {
    }

    public Resumen resumen(UsuarioActual usuario) {
        return resumen(usuario, LocalDate.now(reloj));
    }

    public Resumen resumen(UsuarioActual usuario, LocalDate hoy) {
        Long tenant = usuario.inmobiliariaId();
        List<Negocio> abiertos = usuario.esAdmin()
                ? negocios.findByInmobiliariaIdAndEstadoOrderByCreadoEnDesc(tenant, EstadoNegocio.ABIERTO)
                : negocios.findByInmobiliariaIdAndAsesorIdAndEstadoOrderByCreadoEnDesc(tenant, usuario.id(),
                EstadoNegocio.ABIERTO);

        List<NegocioBloqueado> bloqueados = new ArrayList<>();
        List<Vencimiento> vencimientos = new ArrayList<>();
        long porAprobar = 0;
        for (Negocio negocio : abiertos) {
            EstadoDocumental ed = estadoDocumental.calcular(negocio, hoy);
            String titulo = inmuebles.findByIdAndInmobiliariaId(negocio.getInmuebleId(), tenant)
                    .map(Inmueble::getTitulo).orElse("");
            if (!ed.listoParaFirmar()) {
                String propietario = propietarios.findByIdAndInmobiliariaId(negocio.getPropietarioId(), tenant)
                        .map(Propietario::getNombre).orElse("");
                List<EstadoDocumental.Item> faltantes = ed.items().stream()
                        .filter(i -> i.obligatorio() && i.estado() != EstadoRequisito.RECIBIDO
                                && i.estado() != EstadoRequisito.NO_APLICA).toList();
                bloqueados.add(new NegocioBloqueado(negocio.getId(), titulo, propietario,
                        negocio.getFechaFirmaTentativa(), ed.semaforo(), ed.faltan(),
                        (int) faltantes.stream().filter(i -> i.estado() == EstadoRequisito.VENCIDO).count(),
                        (int) faltantes.stream().filter(i -> i.estado() == EstadoRequisito.POR_VENCER).count(),
                        faltantes.stream().map(i -> i.nombre() + " (" + i.estado().name().toLowerCase()
                                .replace('_', ' ') + ")").toList()));
            }
            for (EstadoDocumental.Item i : ed.items()) {
                boolean vigente = i.estado() == EstadoRequisito.RECIBIDO || i.estado() == EstadoRequisito.POR_VENCER;
                if (vigente && i.venceEl() != null && !i.venceEl().isBefore(hoy)
                        && ChronoUnit.DAYS.between(hoy, i.venceEl()) <= DIAS_DE_LA_SEMANA) {
                    vencimientos.add(new Vencimiento(negocio.getId(), titulo, i.nombre(), i.venceEl(),
                            ChronoUnit.DAYS.between(hoy, i.venceEl())));
                }
            }
            if (recordatorios.existsByNegocioIdAndEstado(negocio.getId(), EstadoRecordatorio.SUGERIDO)) {
                porAprobar++;
            }
        }
        vencimientos.sort(java.util.Comparator.comparing(Vencimiento::venceEl));
        return new Resumen(hoy, abiertos.size(), bloqueados, vencimientos, porAprobar,
                solicitudes.contarPendientes(usuario));
    }
}
