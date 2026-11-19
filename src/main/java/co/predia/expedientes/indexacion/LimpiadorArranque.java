package co.predia.expedientes.indexacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Al arrancar: los trabajos que quedaron EN_CURSO por un reinicio del servidor vuelven a la cola, y se
 * borran los archivos temporales huérfanos (de trabajos ya terminados o que nunca llegaron a crearse).
 */
@Component
public class LimpiadorArranque implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LimpiadorArranque.class);

    private final TrabajoIndexacionRepository trabajos;
    private final co.predia.expedientes.carga.PropiedadesExpedientes propiedades;

    public LimpiadorArranque(TrabajoIndexacionRepository trabajos,
                             co.predia.expedientes.carga.PropiedadesExpedientes propiedades) {
        this.trabajos = trabajos;
        this.propiedades = propiedades;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<TrabajoIndexacion> enCurso = trabajos.findByEstado(EstadoTrabajo.EN_CURSO);
        enCurso.forEach(TrabajoIndexacion::reanudar);
        if (!enCurso.isEmpty()) {
            log.info("{} trabajo(s) que quedaron a medias por un reinicio vuelven a la cola.", enCurso.size());
        }

        Set<String> rutasEnUso = new HashSet<>();
        trabajos.findByEstado(EstadoTrabajo.PENDIENTE).forEach(t -> rutasEnUso.add(t.getRutaTemporal()));

        Path directorio = Path.of(propiedades.directorioTemporal());
        if (!Files.isDirectory(directorio)) {
            return;
        }
        try (Stream<Path> archivos = Files.list(directorio)) {
            archivos.filter(Files::isRegularFile)
                    .filter(p -> !rutasEnUso.contains(p.toString()))
                    .forEach(p -> borrar(p.toFile()));
        } catch (IOException e) {
            log.warn("No se pudo revisar el directorio temporal de expedientes: {}", e.getMessage());
        }
    }

    private void borrar(File archivo) {
        if (archivo.delete()) {
            log.info("Se borró el archivo temporal huérfano {}.", archivo.getName());
        }
    }
}
