package co.predia.cierre.negocio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequisitoNegocioRepository extends JpaRepository<RequisitoNegocio, Long> {

    List<RequisitoNegocio> findByNegocioId(Long negocioId);

    List<RequisitoNegocio> findByInmobiliariaIdAndNegocioId(Long inmobiliariaId, Long negocioId);

    Optional<RequisitoNegocio> findByIdAndNegocioIdAndInmobiliariaId(Long id, Long negocioId, Long inmobiliariaId);
}
