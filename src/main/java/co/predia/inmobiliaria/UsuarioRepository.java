package co.predia.inmobiliaria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Solo para el login: es la única búsqueda que no parte de una inmobiliaria conocida. */
    Optional<Usuario> findByEmailIgnoreCaseAndActivoTrue(String email);

    Optional<Usuario> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);
}
