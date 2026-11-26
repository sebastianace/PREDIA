import type { CSSProperties } from 'react'

const X0 = 96
const X1 = 216
const Y0 = 84
const Y1 = 292
const PISOS = 7
const ALTO = (Y1 - Y0) / PISOS

/** Una línea del dibujo que se traza una sola vez al entrar (igual que las curvas de nivel). */
const traza = (retraso: number): { className: string; pathLength: number; style: CSSProperties } => ({
  className: 'curva', pathLength: 1, style: { '--retraso': `${retraso}ms` } as CSSProperties,
})

/**
 * La fachada de un edificio de apartamentos dibujada con líneas blancas, como un plano: pisos, ventanas, entrada, el
 * suelo y una cota vertical que marca un apartamento. Hereda el color del texto (`currentColor`) y es decorativa.
 */
export function FachadaEdificio({ marca = 'Apto 1102', className = '' }: { marca?: string; className?: string }) {
  const centroMarca = (Y0 + Y1) / 2
  const ventanas = []
  for (let f = 0; f < PISOS - 1; f++) {
    for (let c = 0; c < 3; c++) {
      const x = X0 + 10 + c * 37
      const y = Y0 + f * ALTO + 9
      const h = ALTO - 17
      const destacada = f === 0 && c === 2
      ventanas.push(
        <g key={`${f}-${c}`}>
          <path d={`M${x} ${y}h24v${h}h-24Z`} strokeWidth={1.2} fill={destacada ? 'currentColor' : 'none'} {...traza(900 + (f * 3 + c) * 35)} />
          {!destacada && <path d={`M${x + 12} ${y}v${h}M${x} ${y + h / 2}h24`} strokeWidth={0.7} opacity={0.6} />}
        </g>,
      )
    }
  }
  return (
    <svg viewBox="0 54 300 260" preserveAspectRatio="xMidYMid meet" className={className} fill="none" stroke="currentColor"
      strokeLinejoin="round" strokeLinecap="round" aria-hidden="true">
      <g className="text-trazo" strokeWidth={0.8} strokeDasharray="3 3" opacity={0.7}>
        {Array.from({ length: PISOS + 1 }, (_, i) => <path key={i} d={`M24 ${Y0 + i * ALTO}H${X0}`} />)}
      </g>
      <path d={`M${X0} ${Y0}H${X1}V${Y1}H${X0}Z`} strokeWidth={1.6} {...traza(300)} />
      <path d={`M${X0 + 34} ${Y0}v-14h52v14`} strokeWidth={1.4} {...traza(500)} />
      <path d={`M${X0 - 6} ${Y0}H${X1 + 6}`} strokeWidth={1.8} {...traza(600)} />
      {Array.from({ length: PISOS - 1 }, (_, i) => (
        <path key={i} d={`M${X0 - 4} ${Y0 + (i + 1) * ALTO}H${X1 + 4}`} strokeWidth={1} opacity={0.75} {...traza(700 + i * 90)} />
      ))}
      {ventanas}
      <path d={`M${X0 + 44} ${Y1}v-26h32v26M${X0 + 60} ${Y1 - 26}V${Y1}`} strokeWidth={1.4} {...traza(1800)} />
      <path d={`M${X0 + 38} ${Y1 - 30}h44`} strokeWidth={2} {...traza(1900)} />
      <path d="M14 292H286" strokeWidth={1.6} {...traza(100)} />
      <path className="text-trazo" strokeWidth={0.8} opacity={0.7}
        d="M14 299l7-7M30 299l7-7M46 299l7-7M246 299l7-7M262 299l7-7M278 299l7-7" />
      <g className="text-trazo" strokeWidth={1}>
        <path d={`M${X1 + 4} 99.5H240`} strokeDasharray="3 3" strokeWidth={0.8} />
        <path d={`M240 ${Y0}V${centroMarca - 34}M240 ${centroMarca + 34}V${Y1}M236 ${Y0}h8M236 ${Y1}h8`} />
        <text transform={`translate(244 ${centroMarca}) rotate(-90)`} textAnchor="middle" fill="currentColor" stroke="none" fontSize="11">{marca}</text>
      </g>
    </svg>
  )
}
