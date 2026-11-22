package co.predia.expedientes.carga;

import co.predia.comun.seguridad.UsuarioActual;
import co.predia.expedientes.Documento;
import co.predia.expedientes.DocumentoDto;
import co.predia.expedientes.DocumentoRepository;
import co.predia.expedientes.indexacion.TrabajoIndexacion;
import co.predia.expedientes.indexacion.TrabajoIndexacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el documento y su trabajo de indexación en una sola transacción. Va en un bean aparte
 * (y no como método de ServicioCargaDocumento) para que @Transactional sí tenga efecto: Spring no
 * aplica transacciones a una llamada interna (this.metodo()) dentro de la misma clase.
 */
@Service
class RegistroDocumentoTrabajo {

    private final DocumentoRepository documentos;
    private final TrabajoIndexacionRepository trabajos;

    RegistroDocumentoTrabajo(DocumentoRepository documentos, TrabajoIndexacionRepository trabajos) {
        this.documentos = documentos;
        this.trabajos = trabajos;
    }

    @Transactional
    DocumentoDto registrar(Long inmuebleId, Long negocioId, String nombreArchivo, String hashSha256,
                           long tamanoBytes, String rutaTemporal, UsuarioActual usuario) {
        Documento documento = new Documento(usuario.inmobiliariaId(), inmuebleId, negocioId, nombreArchivo,
                hashSha256, tamanoBytes, usuario.id());
        documentos.save(documento);
        trabajos.save(new TrabajoIndexacion(usuario.inmobiliariaId(), documento.getId(), rutaTemporal));
        return DocumentoDto.desde(documento);
    }
}
