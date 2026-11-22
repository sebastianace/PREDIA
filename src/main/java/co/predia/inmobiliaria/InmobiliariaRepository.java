package co.predia.inmobiliaria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InmobiliariaRepository extends JpaRepository<Inmobiliaria, Long> {

    Optional<Inmobiliaria> findByNit(String nit);
}
