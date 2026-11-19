package co.predia.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Todas las consultas reciben la inmobiliaria. No se usa findById directamente desde los servicios:
 * se usa findByIdAndInmobiliariaId para que un id de otra inmobiliaria responda "no existe".
 */
public interface InmuebleRepository extends JpaRepository<Inmueble, Long> {

    List<Inmueble> findByInmobiliariaIdOrderByTituloAsc(Long inmobiliariaId);

    List<Inmueble> findByInmobiliariaIdAndEstadoOrderByTituloAsc(Long inmobiliariaId, EstadoInmueble estado);

    Optional<Inmueble> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    boolean existsByInmobiliariaIdAndCodigo(Long inmobiliariaId, String codigo);
}
