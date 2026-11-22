package co.predia.clientes;

import java.util.List;

/**
 * Perfil que la IA extrae de lo que dijo el cliente. Es solo una SUGERENCIA: el asesor lo revisa y lo confirma
 * antes de guardarlo. Los montos van en pesos colombianos, como números enteros.
 */
public record PerfilSugerido(String nombre, Long ingresoFamiliar, Long ahorro, Long deudasMensuales,
                             Intencion intencion, Integer habitacionesMin, List<String> zonas, String preferencias,
                             String email, String telefono) {
}
