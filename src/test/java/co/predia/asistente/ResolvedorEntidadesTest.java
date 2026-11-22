package co.predia.asistente;

import co.predia.cierre.negocio.EstadoCivil;
import co.predia.cierre.negocio.Negocio;
import co.predia.cierre.negocio.Propietario;
import co.predia.cierre.negocio.TipoPersona;
import co.predia.clientes.Cliente;
import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inventario.Inmueble;
import co.predia.soporte.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvedorEntidadesTest extends PruebaIntegracion {

    @Autowired ResolvedorEntidades resolvedor;

    private Inmueble inmueble(Inmobiliaria inmobiliaria, String codigo, String titulo, String barrio) {
        Inmueble i = crearInmueble(inmobiliaria, codigo);
        i.setTitulo(titulo);
        i.setBarrio(barrio);
        return inmuebles.save(i);
    }

    @Test
    void encuentraUnInmueblePorNombreSinImportarTildesAbreviaturasNiMayusculas() {
        Inmobiliaria a = crearInmobiliaria("Resolvedor A");
        Inmueble apto = inmueble(a, "APT-1102", "Apartamento 1102 Cañaveral", "Cañaveral");
        inmueble(a, "APT-CAB", "Apartamento Cabecera", "Cabecera");

        assertThat(resolvedor.inmueble(a.getId(), "apto 1102 de CANAVERAL").getId()).isEqualTo(apto.getId());
        assertThat(resolvedor.inmueble(a.getId(), "APT-1102").getId()).isEqualTo(apto.getId());
        assertThat(resolvedor.inmueble(a.getId(), String.valueOf(apto.getId())).getId()).isEqualTo(apto.getId());
    }

    @Test
    void siHayVariosCandidatosNoAdivinaYLosNombra() {
        Inmobiliaria a = crearInmobiliaria("Resolvedor Ambiguo");
        inmueble(a, "A-1", "Apto Torre Norte", "Cabecera");
        inmueble(a, "A-2", "Apto Torre Sur", "Cabecera");

        assertThatThrownBy(() -> resolvedor.inmueble(a.getId(), "apto torre"))
                .isInstanceOf(ReferenciaException.class)
                .hasMessageContaining("varios")
                .hasMessageContaining("Apto Torre Norte")
                .hasMessageContaining("Apto Torre Sur");
    }

    @Test
    void unaReferenciaVaciaOQueNoExisteSeExplica() {
        Inmobiliaria a = crearInmobiliaria("Resolvedor Vacío");

        assertThatThrownBy(() -> resolvedor.cliente(a.getId(), "  de la "))
                .isInstanceOf(ReferenciaException.class).hasMessageContaining("Indica");
        assertThatThrownBy(() -> resolvedor.cliente(a.getId(), "Nadie Conocido"))
                .isInstanceOf(ReferenciaException.class).hasMessageContaining("No encontré");
    }

    @Test
    void nuncaResuelveEntidadesDeOtraInmobiliariaNiPorIdNiPorNombre() {
        Inmobiliaria a = crearInmobiliaria("Resolvedor Dueña");
        Inmobiliaria b = crearInmobiliaria("Resolvedor Ajena");
        Usuario asesorA = crearUsuario(a, Rol.ASESOR);
        Cliente cliente = crearCliente(a, asesorA, "Paola y Juan", "7500000", "90000000");
        Inmueble apto = inmueble(a, "APT-1102", "Apartamento 1102 Cañaveral", "Cañaveral");

        assertThat(resolvedor.cliente(a.getId(), "Paola").getId()).isEqualTo(cliente.getId());
        assertThatThrownBy(() -> resolvedor.cliente(b.getId(), "Paola"))
                .isInstanceOf(ReferenciaException.class);
        assertThatThrownBy(() -> resolvedor.cliente(b.getId(), String.valueOf(cliente.getId())))
                .isInstanceOf(ReferenciaException.class);
        assertThatThrownBy(() -> resolvedor.inmueble(b.getId(), String.valueOf(apto.getId())))
                .isInstanceOf(ReferenciaException.class);
    }

    @Test
    void unNegocioSeEncuentraPorIdPorInmuebleOPorPropietarioYPrefiereElAbierto() {
        Inmobiliaria a = crearInmobiliaria("Resolvedor Negocios");
        Usuario asesor = crearUsuario(a, Rol.ASESOR);
        Inmueble apto = inmueble(a, "APT-1102", "Apartamento 1102 Cañaveral", "Cañaveral");
        Propietario dueno = crearPropietario(a, asesor, "Marta Ruiz", "marta@prueba.test", TipoPersona.NATURAL,
                EstadoCivil.SOLTERO);
        Negocio negocio = crearNegocio(a, asesor, apto, dueno, null);

        assertThat(resolvedor.negocio(a.getId(), String.valueOf(negocio.getId())).getId()).isEqualTo(negocio.getId());
        assertThat(resolvedor.negocio(a.getId(), "apto 1102 cañaveral").getId()).isEqualTo(negocio.getId());
        assertThat(resolvedor.negocio(a.getId(), "Marta").getId()).isEqualTo(negocio.getId());
        // "1102" no es el id de ningún negocio: se busca como texto del inmueble.
        assertThat(resolvedor.negocio(a.getId(), "1102").getId()).isEqualTo(negocio.getId());
    }
}
