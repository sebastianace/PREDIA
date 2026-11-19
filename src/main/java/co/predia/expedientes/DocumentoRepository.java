package co.predia.expedientes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    Optional<Documento> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    Optional<Documento> findByInmobiliariaIdAndHashSha256(Long inmobiliariaId, String hashSha256);

    List<Documento> findByInmobiliariaIdAndInmuebleIdOrderByCreadoEnDesc(Long inmobiliariaId, Long inmuebleId);

    List<Documento> findByInmobiliariaIdOrderByCreadoEnDesc(Long inmobiliariaId);
}
