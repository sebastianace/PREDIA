package co.predia.usuarios;

import co.predia.inmobiliaria.Inmobiliaria;
import co.predia.inmobiliaria.InmobiliariaRepository;
import co.predia.inmobiliaria.ParametrosInmobiliariaRepository;
import co.predia.inmobiliaria.Rol;
import co.predia.inmobiliaria.Usuario;
import co.predia.inmobiliaria.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El primer administrador de una instalación nueva: se crea sin contraseña, con un enlace, y solo si no hay ADMIN. */
class PrimerAdministradorTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final InmobiliariaRepository inmobiliarias = mock(InmobiliariaRepository.class);
    private final ParametrosInmobiliariaRepository parametros = mock(ParametrosInmobiliariaRepository.class);
    private final ServicioInvitaciones invitaciones = mock(ServicioInvitaciones.class);

    private PrimerAdministrador con(PropiedadesUsuarios.PrimerAdmin datos) {
        return new PrimerAdministrador(usuarios, inmobiliarias, parametros, invitaciones,
                new PropiedadesUsuarios("https://predia.ejemplo", "no-responder@ejemplo", datos));
    }

    @Test
    void sinNingunAdminCreaLaInmobiliariaYUnAdministradorSinContrasena() {
        when(usuarios.countByRol(Rol.ADMIN)).thenReturn(0L);
        when(usuarios.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(inmobiliarias.save(any(Inmobiliaria.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(invitaciones.emitir(any(Usuario.class), any())).thenReturn(
                new ServicioInvitaciones.Emitida("t", "https://predia.ejemplo/activar?token=t", Instant.now()));

        String enlace = con(new PropiedadesUsuarios.PrimerAdmin(" Ana@Inmobiliaria.CO ", "Ana Ruiz",
                "Inmobiliaria Real", null, null)).crearSiHaceFalta();

        assertThat(enlace).isEqualTo("https://predia.ejemplo/activar?token=t");
        ArgumentCaptor<Usuario> admin = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(admin.capture());
        assertThat(admin.getValue().getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getValue().getEmail()).isEqualTo("ana@inmobiliaria.co");
        assertThat(admin.getValue().tieneContrasena()).isFalse();
        assertThat(admin.getValue().isActivo()).isFalse();
        ArgumentCaptor<Inmobiliaria> inmobiliaria = ArgumentCaptor.forClass(Inmobiliaria.class);
        verify(inmobiliarias).save(inmobiliaria.capture());
        assertThat(inmobiliaria.getValue().getNombre()).isEqualTo("Inmobiliaria Real");
    }

    @Test
    void siYaHayUnAdministradorNoHaceNada() {
        when(usuarios.countByRol(Rol.ADMIN)).thenReturn(1L);

        assertThat(con(new PropiedadesUsuarios.PrimerAdmin("a@b.co", "A", "B", null, null)).crearSiHaceFalta()).isNull();
        verify(usuarios, never()).save(any());
        verify(inmobiliarias, never()).save(any());
    }

    @Test
    void sinLasVariablesNoCreaNadaYSoloAvisa() {
        when(usuarios.countByRol(Rol.ADMIN)).thenReturn(0L);

        assertThat(con(new PropiedadesUsuarios.PrimerAdmin("", null, null, null, null)).crearSiHaceFalta()).isNull();
        verify(usuarios, never()).save(any());
    }
}
