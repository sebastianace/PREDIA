const pesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

/** Formatea un valor en pesos colombianos. El cálculo siempre viene del backend. */
export const formatoPesos = (valor: number | null | undefined) => (valor == null ? '—' : pesos.format(valor))
