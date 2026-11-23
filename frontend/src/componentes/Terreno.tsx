import { useMemo, type CSSProperties } from 'react'
import { curvasDeNivel } from './curvasDeNivel'

interface Props {
  /** La misma semilla da el mismo terreno: un inmueble, un terreno. */
  semilla?: number
  ancho?: number
  alto?: number
  niveles?: number
  /** Multiplica el grosor del trazo: las miniaturas necesitan líneas más gruesas para no verse como ruido. */
  grosor?: number
  /** Si es true, las curvas se trazan una vez al entrar (solo para el momento principal de una pantalla). */
  trazar?: boolean
  className?: string
}

/**
 * Curvas de nivel como textura: hereda el color del texto (`currentColor`), así que sirve igual sobre el azul de
 * plano (líneas blancas) que sobre el papel (líneas azules muy tenues). Es decorativo: no se anuncia.
 */
export function Terreno({ semilla = 7, ancho = 1000, alto = 700, niveles = 14, grosor = 1, trazar = false, className = '' }: Props) {
  const curvas = useMemo(() => curvasDeNivel(semilla, ancho, alto, niveles), [semilla, ancho, alto, niveles])
  return (
    <svg viewBox={`0 0 ${ancho} ${alto}`} preserveAspectRatio="xMidYMid slice" className={className} fill="none"
      stroke="currentColor" strokeLinejoin="round" strokeLinecap="round" aria-hidden="true">
      {curvas.map((c) => (
        <path key={c.nivel} d={c.d} pathLength={1} strokeWidth={(c.maestra ? 1.6 : 0.7) * grosor} opacity={c.maestra ? 0.9 : 0.55}
          className={trazar ? 'curva' : undefined} style={trazar ? ({ '--retraso': `${c.nivel * 90}ms` } as CSSProperties) : undefined} />
      ))}
    </svg>
  )
}
