package co.predia.inmobiliaria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Usuario de una inmobiliaria: personal (ADMIN o ASESOR) o un propietario con acceso a su portal. Mientras no active
 * su cuenta con el enlace de invitación no tiene contraseña y está inactivo.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inmobiliaria_id", nullable = false)
    private Long inmobiliariaId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "propietario_id")
    private Long propietarioId;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "ultimo_ingreso")
    private Instant ultimoIngreso;

    protected Usuario() {
    }

    /** Un usuario invitado: queda inactivo y sin contraseña hasta que use el enlace de la invitación. */
    public static Usuario invitado(Long inmobiliariaId, String nombre, String email, Rol rol, Long propietarioId,
                                   Long creadoPor) {
        Usuario u = new Usuario(inmobiliariaId, nombre, email, null, rol);
        u.activo = false;
        u.propietarioId = propietarioId;
        u.creadoPor = creadoPor;
        return u;
    }

    /** Fija la contraseña (al activar la cuenta o al recuperarla) y deja el usuario activo. */
    public void fijarContrasena(String hash) {
        this.passwordHash = hash;
        this.activo = true;
    }

    public void cambiarRol(Rol rol) {
        this.rol = rol;
    }

    /** Reactivar solo es posible si ya tiene contraseña; si nunca la creó, necesita una invitación. */
    public void cambiarActivo(boolean activo) {
        this.activo = activo && passwordHash != null;
    }

    public void registrarIngreso() {
        this.ultimoIngreso = Instant.now();
    }

    public void actualizarDatos(String nombre, String email) {
        this.nombre = nombre;
        this.email = email;
    }

    public Usuario(Long inmobiliariaId, String nombre, String email, String passwordHash, Rol rol) {
        this.inmobiliariaId = inmobiliariaId;
        this.nombre = nombre;
        this.email = email;
        this.passwordHash = passwordHash;
        this.rol = rol;
    }

    public Long getId() { return id; }
    public Long getInmobiliariaId() { return inmobiliariaId; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public Long getPropietarioId() { return propietarioId; }
    public Long getCreadoPor() { return creadoPor; }
    public Instant getUltimoIngreso() { return ultimoIngreso; }
    public boolean tieneContrasena() { return passwordHash != null; }
}
