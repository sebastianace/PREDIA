package co.predia.asistente;

import co.predia.expedientes.consulta.Cita;

import java.util.ArrayList;
import java.util.List;

/** Lo que hicieron las herramientas durante UNA petición del asistente: cuáles se usaron, qué devolvieron y qué crearon. */
public class RegistroHerramientas {

    private final List<String> usadas = new ArrayList<>();
    private final List<String> salidas = new ArrayList<>();
    private final List<Long> recordatoriosCreados = new ArrayList<>();
    private List<Cita> citas = List.of();

    void usada(String herramienta) {
        usadas.add(herramienta);
    }

    void salida(String salida) {
        salidas.add(salida);
    }

    void recordatorioCreado(Long id) {
        recordatoriosCreados.add(id);
    }

    /** Las citas de la última consulta a los expedientes (las del número [n] que cita la respuesta). */
    void citas(List<Cita> citas) {
        this.citas = citas;
    }

    public List<String> usadas() {
        return List.copyOf(usadas);
    }

    public List<String> salidas() {
        return List.copyOf(salidas);
    }

    public List<Long> recordatoriosCreados() {
        return List.copyOf(recordatoriosCreados);
    }

    public List<Cita> citas() {
        return citas;
    }
}
