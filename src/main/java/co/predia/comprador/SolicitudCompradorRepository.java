package co.predia.comprador;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitudCompradorRepository extends JpaRepository<SolicitudComprador, Long> {

    Optional<SolicitudComprador> findByIdAndInmobiliariaId(Long id, Long inmobiliariaId);

    List<SolicitudComprador> findByInmobiliariaIdAndClienteIdOrderByCreadaEnDesc(Long inmobiliariaId, Long clienteId);

    long countByInmobiliariaIdAndClienteIdAndAtendidaEnIsNull(Long inmobiliariaId, Long clienteId);

    /** Las solicitudes sin atender de toda la inmobiliaria (lo que ve el ADMIN). */
    List<SolicitudComprador> findByInmobiliariaIdAndAtendidaEnIsNullOrderByCreadaEnAsc(Long inmobiliariaId);

    /** Las sin atender de los clientes que registró un asesor (lo que ve el ASESOR). */
    @Query("""
            select s from SolicitudComprador s
            where s.inmobiliariaId = :inmobiliariaId and s.atendidaEn is null
              and s.clienteId in (select c.id from Cliente c
                                  where c.inmobiliariaId = :inmobiliariaId and c.autorizacionRegistradaPor = :asesorId)
            order by s.creadaEn asc""")
    List<SolicitudComprador> pendientesDelAsesor(@Param("inmobiliariaId") Long inmobiliariaId,
                                                 @Param("asesorId") Long asesorId);
}
