import { useId, type ReactNode } from 'react'

export const claseInput =
  'w-full rounded-control border border-linea bg-white px-3.5 py-2.5 text-base text-tinta outline-none transition-colors placeholder:text-niebla focus:border-cobalto focus:ring-4 focus:ring-cobalto-suave disabled:bg-papel disabled:text-niebla'

export const claseBotonPrimario =
  'whitespace-nowrap rounded-control bg-tinta px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-prusia disabled:cursor-not-allowed disabled:bg-niebla'

export const claseBotonSecundario =
  'whitespace-nowrap rounded-control border border-linea bg-white px-4 py-2.5 text-sm font-medium text-tinta transition-colors hover:border-niebla disabled:cursor-not-allowed disabled:text-niebla'

interface PropsCampo {
  etiqueta: string
  ayuda?: string
  children: (id: string) => ReactNode
}

/** Etiqueta + control + ayuda, con el `for`/`id` correctamente enlazados. */
export function Campo({ etiqueta, ayuda, children }: PropsCampo) {
  const id = useId()
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-tinta">{etiqueta}</label>
      {children(id)}
      {ayuda && <p className="mt-1 text-xs text-niebla">{ayuda}</p>}
    </div>
  )
}

interface PropsPesos {
  etiqueta: string
  valor: number | null
  onChange: (valor: number | null) => void
  ayuda?: string
}

const miles = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 })

/** Campo de dinero en pesos: se escribe con o sin puntos, se muestra con separador de miles. */
export function CampoPesos({ etiqueta, valor, onChange, ayuda }: PropsPesos) {
  return (
    <Campo etiqueta={etiqueta} ayuda={ayuda}>
      {(id) => (
        <div className="relative">
          <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-niebla">$</span>
          <input id={id} inputMode="numeric" autoComplete="off" value={valor == null ? '' : miles.format(valor)}
            onChange={(e) => {
              const soloDigitos = e.target.value.replace(/\D/g, '')
              onChange(soloDigitos === '' ? null : Number(soloDigitos))
            }}
            className={`${claseInput} pl-7`} />
        </div>
      )}
    </Campo>
  )
}

export function Aviso({ tipo, children }: { tipo: 'error' | 'info'; children: ReactNode }) {
  const estilo = tipo === 'error' ? 'border-red-600 bg-red-50 text-red-800' : 'border-cobalto bg-cobalto-suave text-prusia'
  return <p role={tipo === 'error' ? 'alert' : 'status'} className={`border-l-2 px-3 py-2 text-sm ${estilo}`}>{children}</p>
}
