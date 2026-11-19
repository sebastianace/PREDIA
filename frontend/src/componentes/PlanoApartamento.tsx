import type { CSSProperties } from 'react'

const largo = (n: number) => ({ '--largo': n } as CSSProperties)

/**
 * Plano en planta de un apartamento de 3 alcobas, dibujado con trazo blanco.
 * Es el único elemento decorativo de Predia y aparece solo en el ingreso.
 */
export function PlanoApartamento({ className = '' }: { className?: string }) {
  return (
    <svg viewBox="0 -14 430 314" className={className} fill="none" stroke="white" strokeLinecap="square"
      role="img" aria-label="Plano en planta de un apartamento de 78 metros cuadrados">
      {/* Muros exteriores */}
      <path className="trazo" style={largo(1500)} strokeWidth={4}
        d="M20 20h380v260H20zM20 20" />
      {/* Muros interiores */}
      <g strokeWidth={2.2} className="trazo" style={largo(900)}>
        <path d="M150 20v95M150 145v135" />
        <path d="M270 20v70M270 120v40H400" />
        <path d="M20 150h95M150 150h20" />
        <path d="M270 210h130" />
      </g>
      {/* Puertas con su barrido */}
      <g strokeWidth={1.2} opacity={0.75} className="trazo" style={largo(400)}>
        <path d="M150 115a30 30 0 0 1 30 30" />
        <path d="M115 150a35 35 0 0 0 35-35" />
        <path d="M270 90a30 30 0 0 1 30 30" />
      </g>
      {/* Ventanal de la sala */}
      <path d="M190 280h70" strokeWidth={6} stroke="#0b2545" />
      <path d="M190 276h70M190 284h70" strokeWidth={1} opacity={0.8} />
      {/* Cotas */}
      <g strokeWidth={0.8} opacity={0.55}>
        <path d="M20 6h380M20 2v8M400 2v8" />
        <path d="M410 20v260M406 20h8M406 280h8" />
      </g>
      <g fill="white" stroke="none" fontFamily="inherit" style={{ fontStretch: '118%' }}>
        <text x="210" y="-2" fontSize="9" opacity={0.7} textAnchor="middle">12,0 m</text>
        <text x="58" y="85" fontSize="11" opacity={0.85}>Alcoba</text>
        <text x="190" y="70" fontSize="11" opacity={0.85}>Alcoba</text>
        <text x="300" y="60" fontSize="11" opacity={0.85}>Principal</text>
        <text x="300" y="190" fontSize="11" opacity={0.85}>Cocina</text>
        <text x="52" y="220" fontSize="11" opacity={0.85}>Estudio</text>
        <text x="190" y="225" fontSize="13" opacity={0.95}>Sala comedor</text>
        <text x="190" y="243" fontSize="10" opacity={0.6}>78 m² construidos</text>
      </g>
    </svg>
  )
}
