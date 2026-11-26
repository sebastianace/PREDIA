package co.predia.comprador;

import co.predia.clientes.Cliente;
import co.predia.clientes.ClienteRepository;
import co.predia.clientes.PresupuestoCliente;
import co.predia.clientes.PresupuestoService;
import co.predia.clientes.CalculadoraPresupuesto.Limitante;
import co.predia.comprador.SimuladorCompraService.Controles;
import co.predia.comprador.SimuladorCompraService.SimulacionCompra;
import co.predia.comun.seguridad.UsuarioActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.comun.web.ReglaNegocioException;
import co.predia.credito.TasaCreditoDto;
import co.predia.inmobiliaria.UsuarioRepository;
import co.predia.inventario.EstadoInmueble;
import co.predia.inventario.Inmueble;
import co.predia.inventario.InmuebleRepository;
import co.predia.inventario.Operacion;
import co.predia.inventario.foto.FotoInfo;
import co.predia.inventario.foto.FotoInmuebleRepositorio;
import co.predia.inventario.foto.ImagenInfo;
import co.predia.inventario.foto.ImagenInmuebleRepositorio;
import co.predia.inventario.foto.ServicioFotosInmueble;
import co.predia.inventario.foto.ServicioImagenesInmueble;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Lo que ve y hace el comprador en su portal. Todo parte del cliente del token y de su inmobiliaria: un inmueble que no
 * se le compartió (o que se le compartió a otro cliente) responde 404, igual que uno que no existe. La ficha no incluye el
 * código interno, la dirección exacta ni nada del propietario. Las cifras las calcula Java; la IA no habla con el comprador.
 */
@Service
public class ServicioComprador {

    public static final int MAX_SOLICITUDES_SIN_ATENDER = 5;

    public record AsesorDto(String nombre, String email) {
    }

    /** Su presupuesto, explicado: el tope de compra y por qué. {@code null} si la inmobiliaria no tiene una tasa cargada. */
    public record PresupuestoComprador(BigDecimal topeDeCompra, Limitante limitante, BigDecimal ahorro,
                                       BigDecimal pctCuotaInicial, BigDecimal cuotaMaxima, BigDecimal pctCuotaMaxima,
                                       int plazoAnios, TasaCreditoDto tasa, List<String> avisos) {
    }

    public record PerfilComprador(String nombre, AsesorDto asesor, PresupuestoComprador presupuesto) {
    }

    public record InmuebleResumen(Long id, String titulo, String barrio, String ciudad, BigDecimal precio,
                                  BigDecimal areaM2, short habitaciones, short banos, short parqueaderos,
                                  boolean disponible, String nota, InmuebleCompartido.Interes interes, FotoInfo foto,
                                  BigDecimal excedenteSobreTope) {
    }

    public record FichaComprador(Long id, String titulo, String tipo, String barrio, String ciudad, Short estrato,
                                 BigDecimal areaM2, short habitaciones, short banos, short parqueaderos,
                                 boolean aceptaMascotas, boolean propiedadHorizontal, boolean esVis,
                                 BigDecimal valorAdministracion, BigDecimal precio, String descripcion,
                                 boolean disponible, String nota, InmuebleCompartido.Interes interes, FotoInfo foto,
                                 List<ImagenInfo> imagenes, Controles simulador, BigDecimal excedenteSobreTope) {
    }

    public record SolicitudDto(Long id, Long inmuebleId, String inmuebleTitulo, String mensaje, Instant creadaEn,
                               boolean atendida) {
    }

    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final InmuebleRepository inmuebles;
    private final InmuebleCompartidoRepository compartidos;
    private final SolicitudCompradorRepository solicitudes;
    private final PresupuestoService presupuestos;
    private final SimuladorCompraService simulador;
    private final FotoInmuebleRepositorio fotos;
    private final ServicioFotosInmueble servicioFotos;
    private final ServicioImagenesInmueble servicioImagenes;

    public ServicioComprador(ClienteRepository clientes, UsuarioRepository usuarios, InmuebleRepository inmuebles,
                             InmuebleCompartidoRepository compartidos, SolicitudCompradorRepository solicitudes,
                             PresupuestoService presupuestos, SimuladorCompraService simulador,
                             FotoInmuebleRepositorio fotos,
                             ServicioFotosInmueble servicioFotos, ServicioImagenesInmueble servicioImagenes) {
        this.clientes = clientes;
        this.usuarios = usuarios;
        this.inmuebles = inmuebles;
        this.compartidos = compartidos;
        this.solicitudes = solicitudes;
        this.presupuestos = presupuestos;
        this.simulador = simulador;
        this.fotos = fotos;
        this.servicioFotos = servicioFotos;
        this.servicioImagenes = servicioImagenes;
    }

    @Transactional(readOnly = true)
    public PerfilComprador perfil(UsuarioActual comprador) {
        Cliente c = cliente(comprador);
        AsesorDto asesor = usuarios.findByIdAndInmobiliariaId(c.getAutorizacionRegistradaPor(), c.getInmobiliariaId())
                .map(u -> new AsesorDto(u.getNombre(), u.getEmail())).orElse(null);
        return new PerfilComprador(c.getNombre(), asesor, presupuesto(c));
    }

    @Transactional(readOnly = true)
    public List<InmuebleResumen> inmuebles(UsuarioActual comprador) {
        Cliente c = cliente(comprador);
        BigDecimal tope = tope(c);
        return compartidos.findByInmobiliariaIdAndClienteIdOrderByCompartidoEnDesc(c.getInmobiliariaId(), c.getId())
                .stream().map(s -> resumen(s, inmueble(c, s.getInmuebleId()), tope)).toList();
    }

    @Transactional(readOnly = true)
    public FichaComprador ficha(UsuarioActual comprador, Long inmuebleId) {
        Cliente c = cliente(comprador);
        InmuebleCompartido s = compartido(c, inmuebleId);
        Inmueble i = inmueble(c, inmuebleId);
        boolean disponible = disponible(i);
        Controles controles = disponible ? simulador.controles(c.getInmobiliariaId(), i, c) : null;
        return new FichaComprador(i.getId(), i.getTitulo(), i.getTipo().name(), i.getBarrio(), i.getCiudad(),
                i.getEstrato(), i.getAreaM2(), i.getHabitaciones(), i.getBanos(), i.getParqueaderos(),
                i.isAceptaMascotas(), i.isPropiedadHorizontal(), i.isEsVis(), i.getValorAdministracion(),
                i.getPrecioVenta(), i.getDescripcion(), disponible, s.getNota(), s.getInteres(),
                fotos.info(c.getInmobiliariaId(), i.getId()).orElse(null),
                servicioImagenes.listar(c.getInmobiliariaId(), i.getId()), controles,
                CalculadoraCompra.excedenteSobreTope(i.getPrecioVenta(), tope(c)));
    }

    @Transactional(readOnly = true)
    public SimulacionCompra simular(UsuarioActual comprador, Long inmuebleId, BigDecimal cuotaInicial, Integer plazoAnios) {
        Cliente c = cliente(comprador);
        compartido(c, inmuebleId);
        return simulador.simular(c.getInmobiliariaId(), inmueble(c, inmuebleId), c, cuotaInicial, plazoAnios);
    }

    @Transactional
    public void marcarInteres(UsuarioActual comprador, Long inmuebleId, InmuebleCompartido.Interes interes) {
        Cliente c = cliente(comprador);
        InmuebleCompartido s = compartido(c, inmuebleId);
        s.marcarInteres(interes);
        compartidos.save(s);
    }

    @Transactional
    public SolicitudDto solicitar(UsuarioActual comprador, Long inmuebleId, String mensaje) {
        Cliente c = cliente(comprador);
        if (mensaje == null || mensaje.isBlank()) {
            throw new ReglaNegocioException("Escribe tu mensaje para el asesor.");
        }
        if (inmuebleId != null) {
            compartido(c, inmuebleId);
        }
        if (solicitudes.countByInmobiliariaIdAndClienteIdAndAtendidaEnIsNull(c.getInmobiliariaId(), c.getId())
                >= MAX_SOLICITUDES_SIN_ATENDER) {
            throw new ReglaNegocioException("Ya tiene " + MAX_SOLICITUDES_SIN_ATENDER + " mensajes esperando respuesta. "
                    + "Su asesor los atenderá pronto.");
        }
        return dto(solicitudes.save(new SolicitudComprador(c.getInmobiliariaId(), c.getId(), inmuebleId, mensaje.strip())),
                c);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDto> solicitudes(UsuarioActual comprador) {
        Cliente c = cliente(comprador);
        return solicitudes.findByInmobiliariaIdAndClienteIdOrderByCreadaEnDesc(c.getInmobiliariaId(), c.getId())
                .stream().map(s -> dto(s, c)).toList();
    }

    @Transactional(readOnly = true)
    public FotoInmuebleRepositorio.Bytes foto(UsuarioActual comprador, Long inmuebleId, boolean miniatura) {
        Cliente c = cliente(comprador);
        compartido(c, inmuebleId);
        return servicioFotos.leer(c.getInmobiliariaId(), inmuebleId, miniatura);
    }

    /** Una imagen de la galería, solo si el inmueble se le compartió al comprador. */
    @Transactional(readOnly = true)
    public ImagenInmuebleRepositorio.Bytes imagen(UsuarioActual comprador, Long inmuebleId, Long imagenId, boolean miniatura) {
        Cliente c = cliente(comprador);
        compartido(c, inmuebleId);
        return servicioImagenes.leer(c.getInmobiliariaId(), inmuebleId, imagenId, miniatura);
    }

    private PresupuestoComprador presupuesto(Cliente c) {
        try {
            PresupuestoCliente p = presupuestos.calcular(c.getInmobiliariaId(), c.getId(), null, null, false);
            return new PresupuestoComprador(p.presupuesto().topeDeCompra(), p.presupuesto().limitante(), p.ahorro(),
                    p.pctCuotaInicial(), p.presupuesto().cuotaMaxima(), p.pctCuotaMaximaIngreso(), p.plazoAnios(),
                    p.tasa(), p.avisos());
        } catch (ReglaNegocioException e) {
            // Sin una tasa cargada no se puede calcular el tope: el portal sigue funcionando sin esa tarjeta.
            return null;
        }
    }

    /** El tope de compra del cliente, o {@code null} si todavía no se puede calcular. */
    private BigDecimal tope(Cliente c) {
        PresupuestoComprador p = presupuesto(c);
        return p == null ? null : p.topeDeCompra();
    }

    private InmuebleResumen resumen(InmuebleCompartido s, Inmueble i, BigDecimal tope) {
        return new InmuebleResumen(i.getId(), i.getTitulo(), i.getBarrio(), i.getCiudad(), i.getPrecioVenta(),
                i.getAreaM2(), i.getHabitaciones(), i.getBanos(), i.getParqueaderos(), disponible(i), s.getNota(),
                s.getInteres(), fotos.info(i.getInmobiliariaId(), i.getId()).orElse(null),
                CalculadoraCompra.excedenteSobreTope(i.getPrecioVenta(), tope));
    }

    private SolicitudDto dto(SolicitudComprador s, Cliente c) {
        String titulo = s.getInmuebleId() == null ? null
                : inmuebles.findByIdAndInmobiliariaId(s.getInmuebleId(), c.getInmobiliariaId())
                .map(Inmueble::getTitulo).orElse(null);
        return new SolicitudDto(s.getId(), s.getInmuebleId(), titulo, s.getMensaje(), s.getCreadaEn(),
                s.getAtendidaEn() != null);
    }

    private static boolean disponible(Inmueble i) {
        return i.getEstado() == EstadoInmueble.DISPONIBLE && i.getOperacion() == Operacion.VENTA;
    }

    private InmuebleCompartido compartido(Cliente c, Long inmuebleId) {
        return compartidos.findByInmobiliariaIdAndClienteIdAndInmuebleId(c.getInmobiliariaId(), c.getId(), inmuebleId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", inmuebleId));
    }

    private Inmueble inmueble(Cliente c, Long id) {
        return inmuebles.findByIdAndInmobiliariaId(id, c.getInmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Inmueble", id));
    }

    private Cliente cliente(UsuarioActual u) {
        if (u.clienteId() == null) {
            throw new RecursoNoEncontradoException("No hay un cliente asociado a esta sesión.");
        }
        return clientes.findByIdAndInmobiliariaId(u.clienteId(), u.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", u.clienteId()));
    }
}
