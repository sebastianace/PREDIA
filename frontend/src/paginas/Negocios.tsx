import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Negocio } from '../api/tipos'
import { claseBotonPrimario } from '../componentes/Formulario'
import { CotaPorcentaje } from '../componentes/Cota'
import { formatoFecha } from '../formato'

export function Negocios() {
  const [negocios, setNegocios] = useState<Negocio[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Negocio[]>('/negocios').then(setNegocios).catch((e) => setError(e.message))
  }, [])

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

      <ul className="mt-8 divide-y divide-linea border-y border-linea">
        {negocios?.map((n) => (
          <li key={n.id}>
            <Link to={`/negocios/${n.id}`} className="group block py-5 hover:bg-white/60">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <h2 className="ancha truncate text-xl font-semibold text-tinta">{n.inmuebleTitulo}</h2>
                  <p className="mt-0.5 truncate text-sm text-niebla">Vende {n.propietarioNombre}
                    {n.clienteCompradorNombre && ` · compra ${n.clienteCompradorNombre}`}</p>
                </div>
                <div className="shrink-0 text-right">
                  {n.estado === 'ABIERTO'
                    ? <span className={`ancha text-base font-semibold ${n.listoParaFirmar ? 'text-emerald-700' : 'text-tinta'}`}>{n.semaforo}</span>
                    : <span className="text-sm font-medium text-grafito">{n.estado === 'FIRMADO' ? 'Firmado' : 'Cancelado'}</span>}
                  <div className="cifra text-sm text-niebla">{n.porcentajeAvance} %</div>
                </div>
              </div>
              <CotaPorcentaje porcentaje={n.porcentajeAvance} className="mt-4" />
              <p className="mt-2 text-xs text-niebla">
                {n.fechaFirmaTentativa ? `Firma tentativa: ${formatoFecha(n.fechaFirmaTentativa)}` : 'Sin fecha de firma'}
              </p>
            </Link>
          </li>
        ))}
        {negocios && negocios.length === 0 && (
          <li className="py-6 text-sm text-niebla">
            Todavía no hay negocios. Abre uno cuando un propietario te encargue vender su inmueble.
          </li>
        )}
      </ul>
    </div>
  )
}
