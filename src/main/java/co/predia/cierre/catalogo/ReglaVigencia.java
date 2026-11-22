package co.predia.cierre.catalogo;

/** Cómo se calcula hasta cuándo es vigente un documento ya recibido. */
public enum ReglaVigencia {
    NINGUNA,
    /** Vigente N días desde su expedición (por ejemplo, 30 días para el certificado de tradición). */
    DIAS_DESDE_EXPEDICION,
    /** Vigente hasta el 31 de diciembre del año en que se expidió (paz y salvo predial). */
    ANIO_EN_CURSO,
    /** Vigente hasta el último día del mes en que se expidió (paz y salvo de administración). */
    MES_EN_CURSO
}
