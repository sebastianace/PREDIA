package co.predia.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitacionAccesoRepository extends JpaRepository<InvitacionAcceso, Long> {

    Optional<InvitacionAcceso> findByTokenHash(String tokenHash);

    List<InvitacionAcceso> findByUsuarioIdAndUsadaEnIsNull(Long usuarioId);

    Optional<InvitacionAcceso> findFirstByUsuarioIdOrderByCreadaEnDesc(Long usuarioId);
}
