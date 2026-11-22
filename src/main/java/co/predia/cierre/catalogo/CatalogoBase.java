package co.predia.cierre.catalogo;

import co.predia.expedientes.indexacion.TipoDocumentoSugerido;

import java.util.ArrayList;
import java.util.List;

import static co.predia.cierre.catalogo.ReglaAplica.SIEMPRE;
import static co.predia.cierre.catalogo.ReglaAplica.SI_APODERADO;
import static co.predia.cierre.catalogo.ReglaAplica.SI_CASADO_O_UNION;
import static co.predia.cierre.catalogo.ReglaAplica.SI_HIPOTECA;
import static co.predia.cierre.catalogo.ReglaAplica.SI_PERSONA_JURIDICA;
import static co.predia.cierre.catalogo.ReglaAplica.SI_PROPIEDAD_HORIZONTAL;
import static co.predia.cierre.catalogo.ReglaAplica.SI_VALORIZACION;
import static co.predia.cierre.catalogo.ReglaVigencia.ANIO_EN_CURSO;
import static co.predia.cierre.catalogo.ReglaVigencia.DIAS_DESDE_EXPEDICION;
import static co.predia.cierre.catalogo.ReglaVigencia.MES_EN_CURSO;
import static co.predia.cierre.catalogo.ReglaVigencia.NINGUNA;

/**
 * Lista base de documentos que se le piden al vendedor para firmar la venta de un inmueble en Colombia.
 * Es una lista de REFERENCIA: Predia no da asesoría legal; los requisitos se confirman con la notaría.
 * Cada inmobiliaria puede ajustarla (activar, desactivar, vigencias, obligatoriedad).
 *
 * Las palabras clave sirven para validar los recordatorios: son expresiones específicas (no palabras sueltas
 * como "escritura" o "poder") para que un mensaje cordial no se marque por error como si pidiera otro documento.
 */
public final class CatalogoBase {

    private CatalogoBase() {
    }

    public static List<RequisitoCatalogo> para(Long inmobiliariaId) {
        List<RequisitoCatalogo> lista = new ArrayList<>();
        int orden = 0;
        lista.add(new RequisitoCatalogo(inmobiliariaId, "CEDULA_VENDEDOR", "Cédula del vendedor",
                "Copia legible de la cédula de ciudadanía del vendedor.", ++orden, SIEMPRE, true, NINGUNA, null,
                TipoDocumentoSugerido.CEDULA, List.of("cédula", "cedula")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "CERTIFICADO_TRADICION", "Certificado de tradición y libertad",
                "Expedido por la Oficina de Registro de Instrumentos Públicos; no debe tener más de 30 días.", ++orden,
                SIEMPRE, true, DIAS_DESDE_EXPEDICION, 30, TipoDocumentoSugerido.CERTIFICADO_TRADICION_LIBERTAD,
                List.of("tradición y libertad", "tradicion y libertad", "certificado de tradición",
                        "certificado de tradicion")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "ESCRITURA_ADQUISICION",
                "Escritura pública con la que el vendedor adquirió el inmueble",
                "La escritura anterior, con la que el vendedor compró o recibió el inmueble.", ++orden, SIEMPRE, true,
                NINGUNA, null, TipoDocumentoSugerido.ESCRITURA_PUBLICA,
                List.of("escritura de adquisición", "escritura de adquisicion", "escritura anterior",
                        "escritura con la que", "escritura de compra")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "PREDIAL", "Paz y salvo o recibo pagado del impuesto predial",
                "Del año en curso.", ++orden, SIEMPRE, true, ANIO_EN_CURSO, null,
                TipoDocumentoSugerido.PAZ_Y_SALVO_PREDIAL, List.of("predial")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "VALORIZACION", "Paz y salvo de valorización",
                "Solo si el municipio cobra valorización.", ++orden, SI_VALORIZACION, true, NINGUNA, null,
                TipoDocumentoSugerido.PAZ_Y_SALVO_VALORIZACION, List.of("valorización", "valorizacion")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "ADMINISTRACION", "Paz y salvo de administración",
                "Del mes en curso; solo si el inmueble es propiedad horizontal.", ++orden, SI_PROPIEDAD_HORIZONTAL,
                true, MES_EN_CURSO, null, TipoDocumentoSugerido.PAZ_Y_SALVO_ADMINISTRACION,
                List.of("paz y salvo de administración", "paz y salvo de administracion", "cuota de administración",
                        "cuota de administracion", "administración del mes", "administracion del mes")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "PROMESA_COMPRAVENTA", "Promesa de compraventa firmada",
                "Firmada por las dos partes.", ++orden, SIEMPRE, true, NINGUNA, null,
                TipoDocumentoSugerido.PROMESA_COMPRAVENTA, List.of("promesa de compraventa", "promesa")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "LEVANTAMIENTO_HIPOTECA",
                "Paz y salvo o carta del banco para levantar la hipoteca",
                "Solo si el certificado de tradición muestra una hipoteca vigente.", ++orden, SI_HIPOTECA, true,
                NINGUNA, null, TipoDocumentoSugerido.CARTA_LEVANTAMIENTO_HIPOTECA, List.of("hipoteca")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "CONYUGE",
                "Firma o declaración del cónyuge, o afectación a vivienda familiar",
                "Solo si el vendedor está casado o en unión marital.", ++orden, SI_CASADO_O_UNION, true, NINGUNA, null,
                TipoDocumentoSugerido.DECLARACION_CONYUGE,
                List.of("cónyuge", "conyuge", "vivienda familiar", "afectación", "afectacion")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "PODER_ESPECIAL", "Poder especial autenticado",
                "Solo si el vendedor actúa por medio de apoderado.", ++orden, SI_APODERADO, true, NINGUNA, null,
                TipoDocumentoSugerido.PODER_ESPECIAL, List.of("poder especial", "apoderado")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "EXISTENCIA_REPRESENTACION",
                "Certificado de existencia y representación legal",
                "Solo si el vendedor es persona jurídica; no debe tener más de 30 días.", ++orden,
                SI_PERSONA_JURIDICA, true, DIAS_DESDE_EXPEDICION, 30,
                TipoDocumentoSugerido.CERTIFICADO_EXISTENCIA_REPRESENTACION,
                List.of("existencia y representación", "existencia y representacion", "representación legal",
                        "representacion legal", "cámara de comercio", "camara de comercio")));
        lista.add(new RequisitoCatalogo(inmobiliariaId, "SERVICIOS_PUBLICOS", "Recibos de servicios públicos al día",
                "Práctica recomendada: no bloquea la firma.", ++orden, SIEMPRE, false, NINGUNA, null,
                TipoDocumentoSugerido.RECIBO_SERVICIOS_PUBLICOS, List.of("servicios públicos", "servicios publicos")));
        return lista;
    }
}
