import type { ReactNode } from 'react'
import type { EstadoRequisito, ItemDocumental } from '../api/tipos'

type Tono = 'sobre-azul' | 'sobre-papel'

/** Línea de medida de un plano: tope en cada extremo y la medida al centro. */
function LineaDeMedida({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <span className="h-3 w-px bg-current opacity-70" aria-hidden="true" />
      <span className="h-px flex-1 bg-current opacity-50" aria-hidden="true" />
      <span className="rotulo shrink-0 text-sm">{children}</span>
      <span className="h-px flex-1 bg-current opacity-50" aria-hidden="true" />
      <span className="h-3 w-px bg-current opacity-70" aria-hidden="true" />
    </div>
  )
}

/**
 * Una cifra «acotada»: la medida (con su nombre) arriba, entre dos topes, y debajo de qué regla sale. Es la forma de
 * mostrar números en Predia: nunca sueltos, siempre con la razón que los decidió.
 */
export function CifraAcotada({ etiqueta, valor, razon, tono = 'sobre-papel', grande = false }: {
  etiqueta: string
  valor: string
  razon?: ReactNode
  tono?: Tono
  grande?: boolean
}) {
  const azul = tono === 'sobre-azul'
  return (
    <div className={azul ? 'text-trazo' : 'text-niebla'}>
      <LineaDeMedida>{etiqueta}</LineaDeMedida>
      <div className={`ancha cifra mt-2 font-semibold ${grande ? 'text-4xl md:text-6xl' : 'text-3xl md:text-4xl'} ${azul ? 'text-white' : 'text-tinta'}`}>
        {valor}
      </div>
      {razon && <p className={`mt-1.5 text-sm ${azul ? 'text-white/70' : 'text-grafito'}`}>{razon}</p>}
    </div>
  )
}

const estiloTramo: Record<EstadoRequisito, { azul: string; papel: string; nombre: string }> = {
  RECIBIDO: { azul: 'tramo bg-white', papel: 'tramo bg-prusia', nombre: 'recibido' },
  POR_VENCER: { azul: 'tramo tramo-punteado text-[#ffd28a]', papel: 'tramo tramo-punteado text-ambar', nombre: 'por vencer' },
  VENCIDO: { azul: 'tramo tramo-punteado text-[#ff9b9b]', papel: 'tramo tramo-punteado text-sello', nombre: 'vencido' },
  SUGERIDO: { azul: 'tramo tramo-punteado text-white', papel: 'tramo tramo-punteado text-cobalto', nombre: 'sugerido por la IA' },
  PENDIENTE: { azul: 'tramo tramo-punteado text-trazo/60', papel: 'tramo tramo-punteado text-niebla/60', nombre: 'pendiente' },
  NO_APLICA: { azul: 'tramo', papel: 'tramo', nombre: 'no aplica' },
}

/**
 * El avance del cierre como una cota: una medida con un tramo por cada documento obligatorio. Lo recibido va en trazo
 * macizo; lo que falta, punteado, y lo vencido, en rojo. Se lee de un vistazo cuántos faltan y cuáles.
 */
export function CotaAvance({ items, porcentaje, tono = 'sobre-azul' }: {
  items: ItemDocumental[]
  porcentaje: number
  tono?: Tono
}) {
  const azul = tono === 'sobre-azul'
  const tramos = items.filter((i) => i.obligatorio && i.estado !== 'NO_APLICA')
  const resumen = Object.entries(
    tramos.reduce<Record<string, number>>((acc, i) => ({ ...acc, [i.estado]: (acc[i.estado] ?? 0) + 1 }), {}),
  ).map(([e, n]) => `${n} ${estiloTramo[e as EstadoRequisito].nombre}`).join(', ')

  return (
    <div className={azul ? 'text-trazo' : 'text-niebla'} role="img" aria-label={`Avance de documentos: ${porcentaje} %. ${resumen}`}>
      <LineaDeMedida><span className={`cifra font-semibold ${azul ? 'text-white' : 'text-tinta'}`}>{porcentaje} %</span></LineaDeMedida>
      <div className="mt-3 flex">
        {tramos.map((t) => (
          <div key={t.id} className="relative flex-1 border-l border-current pt-2 first:border-l-2" title={`${t.nombre}: ${estiloTramo[t.estado].nombre}`}>
            <div className={`mr-0.5 h-[5px] ${estiloTramo[t.estado][azul ? 'azul' : 'papel']}`} />
          </div>
        ))}
        <span className="h-3 w-px self-start bg-current" aria-hidden="true" />
      </div>
      <p className="rotulo mt-2 text-sm">{resumen}</p>
    </div>
  )
}
