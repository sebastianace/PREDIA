package co.predia.cierre.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequisitoCatalogoRepository extends JpaRepository<RequisitoCatalogo, Long> {

    List<RequisitoCatalogo> findByInmobiliariaIdOrderByOrdenAscIdAsc(Long inmobiliariaId);

    List<RequisitoCatalogo> findByInmobiliariaIdAndActivoTrueOrderByOrdenAscIdAsc(Long inmobiliariaId);

    Optional<RequisitoCatalogo> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    long countByInmobiliariaId(Long inmobiliariaId);
}
