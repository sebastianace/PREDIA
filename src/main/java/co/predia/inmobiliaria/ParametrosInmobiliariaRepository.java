package co.predia.inmobiliaria;

import org.springframework.data.jpa.repository.JpaRepository;

/** La clave primaria es el propio inmobiliaria_id, así que findById ya queda filtrado por tenant. */
public interface ParametrosInmobiliariaRepository extends JpaRepository<ParametrosInmobiliaria, Long> {
}
