package co.predia.inmobiliaria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Solo para el login: es la única búsqueda que no parte de una inmobiliaria conocida. */
    Optional<Usuario> findByEmailIgnoreCaseAndActivoTrue(String email);

    Optional<Usuario> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    long countByRol(Rol rol);

    long countByInmobiliariaIdAndRolAndActivoTrue(Long inmobiliariaId, Rol rol);

    List<Usuario> findByInmobiliariaIdAndRolInOrderByNombreAsc(Long inmobiliariaId, java.util.Collection<Rol> roles);

    Optional<Usuario> findByInmobiliariaIdAndPropietarioId(Long inmobiliariaId, Long propietarioId);
}
