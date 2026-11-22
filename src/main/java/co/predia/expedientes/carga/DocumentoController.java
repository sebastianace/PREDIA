package co.predia.expedientes.carga;

import co.predia.comun.seguridad.SesionActual;
import co.predia.comun.web.RecursoNoEncontradoException;
import co.predia.expedientes.DocumentoDto;
import co.predia.expedientes.DocumentoRepository;
import co.predia.expedientes.indexacion.TipoDocumentoSugerido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** Subida y consulta de los documentos del expediente. El PDF solo se guarda mientras se indexa. */
@RestController
@RequestMapping("/api/documentos")
@Tag(name = "Expedientes")
public class DocumentoController {

    private final ServicioCargaDocumento servicioCarga;
    private final DocumentoRepository documentos;
    private final SesionActual sesion;

    public DocumentoController(ServicioCargaDocumento servicioCarga, DocumentoRepository documentos,
                               SesionActual sesion) {
        this.servicioCarga = servicioCarga;
        this.documentos = documentos;
        this.sesion = sesion;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sube un PDF para indexarlo; se guarda solo temporalmente")
    public ResponseEntity<DocumentoDto> subir(@RequestPart("archivo") MultipartFile archivo,
                                              @RequestParam Long inmuebleId,
                                              @RequestParam(required = false) Long negocioId) {
        ServicioCargaDocumento.ResultadoCarga resultado =
                servicioCarga.cargar(archivo, inmuebleId, negocioId, sesion.usuario());
        HttpStatus estado = resultado.yaExistia() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(estado).body(resultado.documento());
    }

    @GetMapping
    @Operation(summary = "Lista los documentos de la inmobiliaria, por inmueble")
    public List<DocumentoDto> listar(@RequestParam Long inmuebleId) {
        return documentos.findByInmobiliariaIdAndInmuebleIdOrderByCreadoEnDesc(sesion.inmobiliariaId(), inmuebleId)
                .stream().map(DocumentoDto::desde).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un documento: su estado de indexación y su clasificación")
    public DocumentoDto detalle(@PathVariable Long id) {
        return documentos.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .map(DocumentoDto::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento", id));
    }

    @PutMapping("/{id}/clasificacion")
    @Operation(summary = "El asesor confirma o corrige el tipo y la fecha que sugirió la IA")
    public DocumentoDto confirmarClasificacion(@PathVariable Long id, @Valid @RequestBody SolicitudClasificacion solicitud) {
        return servicioCarga.confirmarClasificacion(id, solicitud.tipoDocumento(), solicitud.fechaExpedicion(),
                sesion.usuario());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un documento y sus fragmentos")
    public void eliminar(@PathVariable Long id) {
        var documento = documentos.findByIdAndInmobiliariaId(id, sesion.inmobiliariaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento", id));
        documentos.delete(documento);
    }

    public record SolicitudClasificacion(@NotNull TipoDocumentoSugerido tipoDocumento, LocalDate fechaExpedicion) {
    }
}
