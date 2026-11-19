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

export const formatoFecha = (valor: string | null | undefined) => (valor ? fecha.format(new Date(valor)) : '—')
export const formatoFechaHora = (valor: string | null | undefined) => (valor ? horaFecha.format(new Date(valor)) : '—')

/** 0,2964 → "29,6 %". El número ya viene calculado por el backend; aquí solo se muestra. */
export const formatoPorcentaje = (fraccion: number | null | undefined, decimales = 1) =>
  fraccion == null ? '—' : `${(fraccion * 100).toLocaleString('es-CO', { maximumFractionDigits: decimales })} %`

/** 0,12 → "12 % E.A." */
export const formatoTasaEa = (fraccion: number) => `${formatoPorcentaje(fraccion, 2)} E.A.`
