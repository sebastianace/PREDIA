const pesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

/** Formatea un valor en pesos colombianos. El cálculo siempre viene del backend. */
export const formatoPesos = (valor: number | null | undefined) => (valor == null ? '—' : pesos.format(valor))

/** Tamaño de archivo en KB o MB, como se ve habitualmente. */
export function formatoTamano(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const fecha = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })
const horaFecha = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit',
})

/**
 * Una fecha sin hora ("2026-12-01") se interpreta como fecha LOCAL: con `new Date("2026-12-01")` JavaScript la toma
 * como medianoche UTC y, en Colombia (UTC-5), mostraría el día anterior.
 */
export function aFecha(valor: string): Date {
  const soloFecha = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor)
  return soloFecha ? new Date(Number(soloFecha[1]), Number(soloFecha[2]) - 1, Number(soloFecha[3])) : new Date(valor)
}

export const formatoFecha = (valor: string | null | undefined) => (valor ? fecha.format(aFecha(valor)) : '—')
export const formatoFechaHora = (valor: string | null | undefined) => (valor ? horaFecha.format(aFecha(valor)) : '—')

/** 0,2964 → "29,6 %". El número ya viene calculado por el backend; aquí solo se muestra. */
export const formatoPorcentaje = (fraccion: number | null | undefined, decimales = 1) =>
  fraccion == null ? '—' : `${(fraccion * 100).toLocaleString('es-CO', { maximumFractionDigits: decimales })} %`

/** 0,12 → "12 % E.A." */
export const formatoTasaEa = (fraccion: number) => `${formatoPorcentaje(fraccion, 2)} E.A.`
