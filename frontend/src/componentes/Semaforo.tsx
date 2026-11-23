import type { EstadoRequisito } from '../api/tipos'

/** El estado del negocio de un vistazo: verde si está listo para firmar; ámbar si faltan documentos. */
export function Semaforo({ listo, texto, tamano = 'normal' }: { listo: boolean; texto: string; tamano?: 'normal' | 'grande' }) {
  const estilo = listo ? 'bg-emerald-50 text-emerald-800' : 'bg-ambar-suave text-ambar'
  const punto = listo ? 'bg-emerald-600' : 'bg-ambar'
  const medida = tamano === 'grande' ? 'px-4 py-2 text-base' : 'px-2.5 py-1 text-xs'
  return (
    <span className={`inline-flex items-center gap-2 rounded-full font-semibold ${estilo} ${medida}`}>
      <span className={`size-2 rounded-full ${punto}`} aria-hidden="true" />
      {texto}
    </span>
  )
}

export const estiloRequisito: Record<EstadoRequisito, { nombre: string; clase: string }> = {
  PENDIENTE: { nombre: 'Pendiente', clase: 'bg-stone-100 text-stone-700' },
  SUGERIDO: { nombre: 'Sugerido por la IA', clase: 'bg-cobalto-suave text-cobalto-hondo' },
  RECIBIDO: { nombre: 'Recibido', clase: 'bg-emerald-50 text-emerald-800' },
  POR_VENCER: { nombre: 'Por vencer', clase: 'bg-ambar-suave text-ambar' },
  VENCIDO: { nombre: 'Vencido', clase: 'bg-red-50 text-red-700' },
  NO_APLICA: { nombre: 'No aplica', clase: 'bg-papel text-niebla' },
}

/** El estado de un documento: texto con un punto, y «Vencido» como un sello (lo único que de verdad urge). */
export function EstadoRequisitoChip({ estado }: { estado: EstadoRequisito }) {
  const e = estiloRequisito[estado]
  if (estado === 'VENCIDO') {
    return (
      <span className="shrink-0 -rotate-2 rounded-sm border-[1.5px] border-sello px-2 py-0.5 text-sm font-semibold text-sello">
        {e.nombre}
      </span>
    )
  }
  const punto: Record<EstadoRequisito, string> = {
    PENDIENTE: 'bg-niebla', SUGERIDO: 'bg-cobalto', RECIBIDO: 'bg-emerald-600', POR_VENCER: 'bg-ambar', VENCIDO: 'bg-sello', NO_APLICA: 'bg-linea',
  }
  return (
    <span className="inline-flex shrink-0 items-center gap-2 text-sm text-grafito">
      <span className={`size-2 rounded-full ${punto[estado]}`} aria-hidden="true" />
      {e.nombre}
    </span>
  )
}

export function BarraAvance({ porcentaje }: { porcentaje: number }) {
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-linea" role="progressbar" aria-valuenow={porcentaje}
      aria-valuemin={0} aria-valuemax={100} aria-label="Avance de documentos">
      <div className="h-full rounded-full bg-cobalto transition-[width]" style={{ width: `${porcentaje}%` }} />
    </div>
  )
}
