package co.predia.clientes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findByInmobiliariaIdOrderByNombreAsc(Long inmobiliariaId);

    Optional<Cliente> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    long countByInmobiliariaId(Long inmobiliariaId);
}
