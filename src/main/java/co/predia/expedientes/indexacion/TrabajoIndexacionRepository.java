package co.predia.expedientes.indexacion;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrabajoIndexacionRepository extends JpaRepository<TrabajoIndexacion, Long> {

    Optional<TrabajoIndexacion> findByDocumentoId(Long documentoId);

    /**
     * Toma el trabajo PENDIENTE más antiguo que ya puede reintentarse, con "FOR UPDATE SKIP LOCKED":
     * si otro hilo (o, más adelante, otra instancia) ya lo está tomando, este método no espera y pasa al siguiente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    Optional<TrabajoIndexacion> findFirstByEstadoAndProximoIntentoLessThanEqualOrderByProximoIntentoAsc(
            EstadoTrabajo estado, Instant ahora);

    List<TrabajoIndexacion> findByEstado(EstadoTrabajo estado);
}
