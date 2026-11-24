import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Negocio } from '../api/tipos'
import { Avatar } from '../componentes/Avatar'
import { CotaPorcentaje } from '../componentes/Cota'
import { claseBotonPrimario } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { Semaforo } from '../componentes/Semaforo'
import { Terreno } from '../componentes/Terreno'
import { aFecha, formatoFecha } from '../formato'

const nombreEstado = { FIRMADO: 'Firmado', CANCELADO: 'Cancelado' } as const

/** Un resumen de cabecera: una cifra con su nombre; la primera va sobre el azul de plano. */
function Resumen({ etiqueta, valor, tono = 'papel', alerta = false }: { etiqueta: string; valor: string; tono?: 'papel' | 'azul'; alerta?: boolean }) {
  const azul = tono === 'azul'
  return (
    <div className={`relative isolate overflow-hidden rounded-panel px-3 py-3 sm:px-4 sm:py-3.5 ${azul ? 'bg-prusia text-white' : 'border border-linea bg-white'}`}>
      {azul && <Terreno semilla={1102} niveles={8} className="absolute inset-0 -z-10 size-full text-trazo opacity-40" />}
      <div className={`rotulo text-sm ${azul ? 'text-trazo' : 'text-niebla'}`}>{etiqueta}</div>
      <div className={`ancha cifra mt-0.5 whitespace-nowrap text-lg font-semibold sm:text-2xl ${azul ? 'text-white' : alerta ? 'text-sello' : 'text-tinta'}`}>{valor}</div>
    </div>
  )
}

export function Negocios() {
  const [negocios, setNegocios] = useState<Negocio[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Negocio[]>('/negocios').then(setNegocios).catch((e) => setError(e.message))
  }, [])

  const abiertos = negocios?.filter((n) => n.estado === 'ABIERTO') ?? []
  const porResolver = abiertos.reduce((suma, n) => suma + n.faltan, 0)
  const firmas = abiertos.map((n) => n.fechaFirmaTentativa).filter((f): f is string => f != null).sort()

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="ancha text-[32px] font-semibold leading-[1.05] md:text-5xl">Negocios</h1>
          <p className="mt-1 text-niebla">Qué le falta a cada vendedor para poder firmar.</p>
        </div>
        <Link to="/negocios/nuevo" className={`${claseBotonPrimario} shrink-0`}>Nuevo negocio</Link>
      </div>

      {error && <p role="alert" className="mt-6 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
      {!negocios && !error && <p className="mt-6 text-niebla">Cargando…</p>}

      {negocios && negocios.length > 0 && (
        <div className="mt-6 grid grid-cols-3 gap-2 sm:gap-3">
          <Resumen tono="azul" etiqueta="Abiertos" valor={String(abiertos.length)} />
          <Resumen etiqueta="Por resolver" valor={String(porResolver)} alerta={porResolver > 0} />
          <Resumen etiqueta="Próxima firma" valor={firmas.length > 0 ? aFecha(firmas[0]).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' }).replace('.', '') : '—'} />
        </div>
      )}

      <ul className="mt-5 space-y-4">
        {negocios?.map((n) => (
          <li key={n.id}>
            <Link to={`/negocios/${n.id}`} className="group block overflow-hidden rounded-panel border border-linea bg-white transition-colors hover:border-niebla">
              <div className="relative isolate flex flex-col items-start gap-3 overflow-hidden bg-prusia px-5 pb-4 pt-8 text-white sm:flex-row sm:items-end sm:justify-between">
                <Terreno semilla={n.id * 17 + 5} niveles={9} className="absolute inset-0 -z-10 size-full text-trazo opacity-50" />
                <div className="min-w-0">
                  <h2 className="ancha text-xl font-semibold text-white sm:truncate">{n.inmuebleTitulo}</h2>
                </div>
                {n.estado === 'ABIERTO'
                  ? <Semaforo listo={n.listoParaFirmar} texto={n.semaforo} />
                  : <span className="shrink-0 rounded-full bg-white/15 px-2.5 py-1 text-xs font-semibold text-white">{nombreEstado[n.estado]}</span>}
              </div>

              <div className="px-5 pb-5 pt-4">
                <div className="flex flex-wrap gap-x-8 gap-y-3">
                  <div className="flex min-w-0 items-center gap-2.5">
                    <Avatar nombre={n.propietarioNombre} tono="suave" />
                    <div className="min-w-0">
                      <div className="rotulo text-sm text-niebla">Vende</div>
                      <div className="truncate text-sm font-medium text-tinta">{n.propietarioNombre}</div>
                    </div>
                  </div>
                  {n.clienteCompradorNombre && (
                    <div className="flex min-w-0 items-center gap-2.5">
                      <Avatar nombre={n.clienteCompradorNombre} />
                      <div className="min-w-0">
                        <div className="rotulo text-sm text-niebla">Compra</div>
                        <div className="truncate text-sm font-medium text-tinta">{n.clienteCompradorNombre}</div>
                      </div>
                    </div>
                  )}
                </div>

                <div className="mt-5 flex items-center gap-3">
                  <CotaPorcentaje porcentaje={n.porcentajeAvance} className="flex-1" />
                  <span className="cifra text-sm font-semibold text-tinta">{n.porcentajeAvance} %</span>
                </div>
                <p className="mt-3 flex items-center gap-1.5 text-xs text-niebla">
                  <Icono nombre="reloj" className="size-3.5" />
                  {n.fechaFirmaTentativa ? `Firma tentativa: ${formatoFecha(n.fechaFirmaTentativa)}` : 'Sin fecha de firma'}
                </p>
              </div>
            </Link>
          </li>
        ))}
        {negocios && negocios.length === 0 && (
          <li className="rounded-panel border border-dashed border-linea bg-white px-5 py-8 text-sm text-niebla">
            Todavía no hay negocios. Abre uno cuando un propietario te encargue vender su inmueble.
          </li>
        )}
      </ul>
    </div>
  )
}
