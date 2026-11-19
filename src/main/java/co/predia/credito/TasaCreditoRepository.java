package co.predia.credito;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TasaCreditoRepository extends JpaRepository<TasaCredito, Long> {

    List<TasaCredito> findByInmobiliariaIdOrderByBancoAscVigenteDesdeDesc(Long inmobiliariaId);

    Optional<TasaCredito> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    /** La tasa vigente más reciente de un tipo (la de "vigente desde" más cercana a hoy, sin pasarse). */
    Optional<TasaCredito> findFirstByInmobiliariaIdAndTipoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDescIdDesc(
            Long inmobiliariaId, TipoTasa tipo, LocalDate hoy);

    Optional<TasaCredito> findFirstByInmobiliariaIdAndTipoAndBancoIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDescIdDesc(
            Long inmobiliariaId, TipoTasa tipo, String banco, LocalDate hoy);

    long countByInmobiliariaId(Long inmobiliariaId);
}
