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
