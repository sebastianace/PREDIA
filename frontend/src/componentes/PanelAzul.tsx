import type { ReactNode } from 'react'
import { Terreno } from './Terreno'

/**
 * Un panel de azul de plano con curvas de nivel blancas que se trazan una sola vez al entrar (el mismo recurso del
 * inicio y de los negocios). La semilla fija el terreno: el mismo cliente, el mismo dibujo. El contenido va encima.
 */
export function PanelAzul({ semilla, className = '', children }: { semilla: number; className?: string; children: ReactNode }) {
  return (
    <div className={`relative isolate overflow-hidden bg-prusia text-white ${className}`}>
      <Terreno semilla={semilla} trazar niveles={12} className="absolute inset-0 -z-10 size-full text-cobalto-suave opacity-50" />
      {children}
    </div>
  )
}
