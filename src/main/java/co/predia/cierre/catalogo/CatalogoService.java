package co.predia.cierre.catalogo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Garantiza que cada inmobiliaria tenga su catálogo: si no lo tiene, se siembra con la lista base. */
@Service
public class CatalogoService {

    private final RequisitoCatalogoRepository catalogo;

    public CatalogoService(RequisitoCatalogoRepository catalogo) {
        this.catalogo = catalogo;
    }

    @Transactional
    public List<RequisitoCatalogo> asegurar(Long inmobiliariaId) {
        if (catalogo.countByInmobiliariaId(inmobiliariaId) == 0) {
            catalogo.saveAll(CatalogoBase.para(inmobiliariaId));
        }
        return catalogo.findByInmobiliariaIdOrderByOrdenAscIdAsc(inmobiliariaId);
    }
}
