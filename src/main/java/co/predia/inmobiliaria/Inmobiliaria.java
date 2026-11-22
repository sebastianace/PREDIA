package co.predia.inmobiliaria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Inmobiliaria cliente de Predia (tenant). Todos los datos de negocio cuelgan de ella. */
@Entity
@Table(name = "inmobiliaria")
public class Inmobiliaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String nit;

    @Column(nullable = false)
    private String ciudad;

    @Column(name = "creada_en", nullable = false, insertable = false, updatable = false)
    private Instant creadaEn;

    protected Inmobiliaria() {
    }

    public Inmobiliaria(String nombre, String nit, String ciudad) {
        this.nombre = nombre;
        this.nit = nit;
        this.ciudad = ciudad;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getNit() { return nit; }
    public String getCiudad() { return ciudad; }
    public Instant getCreadaEn() { return creadaEn; }
}
