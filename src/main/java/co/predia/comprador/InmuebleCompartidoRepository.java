package co.predia.comprador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InmuebleCompartidoRepository extends JpaRepository<InmuebleCompartido, Long> {

    List<InmuebleCompartido> findByInmobiliariaIdAndClienteIdOrderByCompartidoEnDesc(Long inmobiliariaId, Long clienteId);

    Optional<InmuebleCompartido> findByInmobiliariaIdAndClienteIdAndInmuebleId(Long inmobiliariaId, Long clienteId,
                                                                               Long inmuebleId);
}
