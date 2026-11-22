package co.predia.cierre.recordatorios;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecordatorioRepository extends JpaRepository<Recordatorio, Long> {

    Optional<Recordatorio> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    List<Recordatorio> findByInmobiliariaIdAndEstadoOrderByCreadoEnDesc(Long inmobiliariaId,
                                                                         EstadoRecordatorio estado);

    List<Recordatorio> findByNegocioIdAndInmobiliariaIdOrderByCreadoEnDesc(Long negocioId, Long inmobiliariaId);

    boolean existsByNegocioIdAndEstado(Long negocioId, EstadoRecordatorio estado);

    Optional<Recordatorio> findFirstByNegocioIdAndEstadoOrderByEnviadoEnDesc(Long negocioId,
                                                                             EstadoRecordatorio estado);
}
