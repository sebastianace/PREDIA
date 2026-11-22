package co.predia.cierre.negocio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PropietarioRepository extends JpaRepository<Propietario, Long> {

    List<Propietario> findByInmobiliariaIdOrderByNombreAsc(Long inmobiliariaId);

    Optional<Propietario> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    long countByInmobiliariaId(Long inmobiliariaId);
}
