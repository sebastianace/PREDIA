import { useId, useState, type ReactNode } from 'react'

export const claseInput =
  'w-full rounded-control border border-linea bg-white px-3.5 py-2.5 text-base text-tinta outline-none transition-colors placeholder:text-niebla focus:border-cobalto focus:ring-4 focus:ring-cobalto-suave disabled:bg-papel disabled:text-niebla'

export const claseBotonPrimario =
  'whitespace-nowrap rounded-control bg-tinta px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-prusia disabled:cursor-not-allowed disabled:bg-niebla'

export const claseBotonSecundario =
  'whitespace-nowrap rounded-control border border-linea bg-white px-4 py-2.5 text-sm font-medium text-tinta transition-colors hover:border-niebla disabled:cursor-not-allowed disabled:text-niebla'

interface PropsCampo {
  etiqueta: string
  ayuda?: string
  /** Lo propuso la IA y el asesor todavía no lo tocó: se marca para que lo revise. */
  sugerido?: boolean
  children: (id: string) => ReactNode
}

/** La marca de lo que propuso la IA: solo una sugerencia, hasta que el asesor edita el campo. */
export function MarcaIa() {
  return <span className="ml-1.5 rounded-full bg-cobalto-suave px-2 py-px text-[11px] font-normal text-cobalto-hondo">Sugerido por IA</span>
}

/** Etiqueta + control + ayuda, con el `for`/`id` correctamente enlazados. */
export function Campo({ etiqueta, ayuda, sugerido, children }: PropsCampo) {
  const id = useId()
  return (
    <div className="min-w-0">
      <label htmlFor={id} className="mb-1.5 flex flex-wrap items-center text-sm font-medium text-tinta">{etiqueta}{sugerido && <MarcaIa />}</label>
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
  sugerido?: boolean
}

const miles = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 })

/** Campo de dinero en pesos: se escribe con o sin puntos, se muestra con separador de miles. */
export function CampoPesos({ etiqueta, valor, onChange, ayuda, sugerido }: PropsPesos) {
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
            className={`${claseInput} pl-7 ${sugerido ? 'pr-12' : ''}`} />
          {/* En los campos angostos la marca va dentro del campo: en la etiqueta la partiría en dos líneas. */}
          {sugerido && (
            <span title="Sugerido por IA" className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full bg-cobalto-suave px-2 py-px text-[11px] text-cobalto-hondo">
              IA<span className="sr-only"> (sugerido por IA)</span>
            </span>
          )}
        </div>
      )}
    </Campo>
  )
}

export function Aviso({ tipo, children }: { tipo: 'error' | 'info'; children: ReactNode }) {
  const estilo = tipo === 'error' ? 'border-red-600 bg-red-50 text-red-800' : 'border-cobalto bg-cobalto-suave text-prusia'
  return <p role={tipo === 'error' ? 'alert' : 'status'} className={`border-l-2 px-3 py-2 text-sm ${estilo}`}>{children}</p>
}

/** Una sección numerada de un formulario, en su propia tarjeta. */
export function SeccionNumerada({ numero, titulo, children }: { numero: number; titulo: string; children: ReactNode }) {
  return (
    <section className="rounded-panel border border-linea bg-white p-4 md:p-5">
      <h2 className="mb-4 flex items-center gap-2.5 text-base font-semibold text-tinta">
        <span className="flex size-[22px] shrink-0 items-center justify-center rounded-full bg-prusia text-xs font-semibold text-white" aria-hidden="true">{numero}</span>
        {titulo}
      </h2>
      {children}
    </section>
  )
}

/** Un grupo de opciones excluyentes (Comprar/Arrendar, persona natural/jurídica). */
export function Segmentado<T extends string>({ etiqueta, opciones, valor, onChange, ocultarEtiqueta = false }: {
  etiqueta: string
  /** La etiqueta sigue accesible para lectores de pantalla pero no se ve (para un selector de periodo en un encabezado). */
  ocultarEtiqueta?: boolean
  opciones: { valor: T; texto: string }[]
  valor: T
  onChange: (v: T) => void
}) {
  return (
    <div>
      <span className={ocultarEtiqueta ? 'sr-only' : 'mb-1.5 block text-sm font-medium text-tinta'}>{etiqueta}</span>
      <div role="radiogroup" aria-label={etiqueta} className="inline-flex overflow-hidden rounded-control border border-linea bg-white">
        {opciones.map((o) => (
          <button key={o.valor} type="button" role="radio" aria-checked={valor === o.valor} onClick={() => onChange(o.valor)}
            className={`px-3.5 py-2 text-sm transition-colors ${valor === o.valor ? 'bg-prusia font-medium text-white' : 'text-grafito hover:bg-papel'}`}>
            {o.texto}
          </button>
        ))}
      </div>
    </div>
  )
}

/** Un número entero pequeño con botones de menos y más (habitaciones). */
export function Contador({ etiqueta, valor, onChange, min = 0, max = 10 }: {
  etiqueta: string; valor: number; onChange: (v: number) => void; min?: number; max?: number
}) {
  const boton = 'flex size-9 items-center justify-center text-lg text-grafito transition-colors hover:bg-papel disabled:text-linea disabled:hover:bg-white'
  return (
    <div>
      <span className="mb-1.5 block text-sm font-medium text-tinta">{etiqueta}</span>
      <div className="inline-flex items-center overflow-hidden rounded-control border border-linea bg-white" role="group" aria-label={etiqueta}>
        <button type="button" aria-label="Una menos" disabled={valor <= min} onClick={() => onChange(Math.max(min, valor - 1))} className={boton}>−</button>
        <output aria-live="polite" className="min-w-9 text-center text-sm font-medium text-tinta">{valor}</output>
        <button type="button" aria-label="Una más" disabled={valor >= max} onClick={() => onChange(Math.min(max, valor + 1))} className={boton}>+</button>
      </div>
    </div>
  )
}

/** Varias palabras como etiquetas: se agregan con Enter o coma y se quitan con la × o con Retroceso. */
export function CampoEtiquetas({ etiqueta, ayuda, valores, onChange, placeholder, sugerido }: {
  etiqueta: string; ayuda?: string; valores: string[]; onChange: (v: string[]) => void; placeholder?: string; sugerido?: boolean
}) {
  const [texto, setTexto] = useState('')
  const agregar = (t: string) => {
    const v = t.trim()
    if (v && !valores.some((x) => x.toLowerCase() === v.toLowerCase())) onChange([...valores, v])
    setTexto('')
  }
  return (
    <Campo etiqueta={etiqueta} ayuda={ayuda} sugerido={sugerido}>
      {(id) => (
        <div className="flex w-full flex-wrap items-center gap-1.5 rounded-control border border-linea bg-white px-2.5 py-1.5 transition-colors focus-within:border-cobalto focus-within:ring-4 focus-within:ring-cobalto-suave">
          {valores.map((v) => (
            <span key={v} className="inline-flex items-center gap-1 rounded-full bg-cobalto-suave py-0.5 pl-2.5 pr-1.5 text-xs font-medium text-cobalto-hondo">
              {v}
              <button type="button" aria-label={`Quitar ${v}`} onClick={() => onChange(valores.filter((x) => x !== v))}
                className="flex size-4 items-center justify-center rounded-full text-cobalto-hondo hover:bg-white">×</button>
            </span>
          ))}
          <input id={id} value={texto} onChange={(e) => setTexto(e.target.value)} onBlur={() => agregar(texto)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); agregar(texto) }
              else if (e.key === 'Backspace' && texto === '' && valores.length > 0) onChange(valores.slice(0, -1))
            }}
            placeholder={valores.length === 0 ? placeholder : 'Otra zona…'}
            className="min-w-[8rem] flex-1 bg-transparent py-1 text-base text-tinta outline-none placeholder:text-niebla" />
        </div>
      )}
    </Campo>
  )
}

/** La autorización de tratamiento de datos (Ley 1581 de 2012): sin ella no se guarda el perfil. */
export function Autorizacion({ quien, marcado, onChange }: { quien: string; marcado: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-start gap-3 text-sm text-grafito">
      <input type="checkbox" checked={marcado} onChange={(e) => onChange(e.target.checked)} className="mt-0.5 size-[18px] shrink-0 accent-[var(--color-cobalto)]" />
      <span>
        {quien} <strong className="font-medium text-tinta">autorizó el tratamiento de sus datos personales</strong> (Ley 1581 de
        2012). Sin esta autorización Predia no guarda el perfil.
      </span>
    </label>
  )
}
