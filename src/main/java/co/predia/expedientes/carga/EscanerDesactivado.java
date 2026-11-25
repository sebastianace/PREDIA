package co.predia.expedientes.carga;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Modo por defecto: sin antivirus, para que la app arranque con solo PostgreSQL. La inspección del PDF sigue activa. */
@Component
@ConditionalOnProperty(name = "predia.expedientes.escaneo.modo", havingValue = "DESACTIVADO", matchIfMissing = true)
public class EscanerDesactivado implements EscanerArchivos {

    @Override
    public Resultado escanear(Path archivo) {
        return Resultado.sinVerificar();
    }
}
