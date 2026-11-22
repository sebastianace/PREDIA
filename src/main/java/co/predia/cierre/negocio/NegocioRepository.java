package co.predia.cierre.negocio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NegocioRepository extends JpaRepository<Negocio, Long> {

    Optional<Negocio> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    List<Negocio> findByInmobiliariaIdOrderByCreadoEnDesc(Long inmobiliariaId);

    List<Negocio> findByInmobiliariaIdAndEstadoOrderByCreadoEnDesc(Long inmobiliariaId, EstadoNegocio estado);

    List<Negocio> findByInmobiliariaIdAndAsesorIdAndEstadoOrderByCreadoEnDesc(Long inmobiliariaId, Long asesorId,
                                                                              EstadoNegocio estado);

    /** Para el trabajo diario, que recorre todas las inmobiliarias. */
    List<Negocio> findByEstado(EstadoNegocio estado);

    long countByInmobiliariaId(Long inmobiliariaId);
}
