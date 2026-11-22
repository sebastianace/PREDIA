package co.predia.asistente;

import co.predia.cierre.negocio.PropietarioRepository;
import co.predia.cierre.recordatorios.ServicioRecordatorios;
import co.predia.cierre.reglas.ServicioEstadoDocumental;
import co.predia.clientes.PresupuestoService;
import co.predia.credito.SimuladorCreditoService;
import co.predia.credito.reduccion.SimuladorReduccionService;
import co.predia.expedientes.consulta.MotorRag;
import co.predia.inventario.BusquedaInmuebles;
import co.predia.inventario.InmuebleRepository;
import org.springframework.stereotype.Component;

/**
 * Los servicios de Predia que usan las herramientas del asistente. Las herramientas llaman a los mismos servicios
 * que la interfaz (no duplican ninguna regla ni ningún cálculo).
 */
@Component
public record ServiciosHerramientas(
        ResolvedorEntidades resolvedor, MotorRag motorRag, BusquedaInmuebles busquedaInmuebles,
        PresupuestoService presupuestos, SimuladorCreditoService simulador, ServicioEstadoDocumental estadoDocumental,
        ServicioRecordatorios recordatorios, InmuebleRepository inmuebles, PropietarioRepository propietarios,
        SimuladorReduccionService reduccion) {
}
