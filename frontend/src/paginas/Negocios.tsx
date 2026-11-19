import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Negocio } from '../api/tipos'
import { claseBotonPrimario } from '../componentes/Formulario'
import { BarraAvance, Semaforo } from '../componentes/Semaforo'
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
          <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">Negocios</h1>
          <p className="mt-1 text-niebla">Qué le falta a cada vendedor para poder firmar.</p>
        </div>
        <Link to="/negocios/nuevo" className={`${claseBotonPrimario} shrink-0`}>Nuevo negocio</Link>
      </div>

      {error && <p role="alert" className="mt-6 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
      {!negocios && !error && <p className="mt-6 text-niebla">Cargando…</p>}

      <ul className="mt-6 space-y-3">
        {negocios?.map((n) => (
          <li key={n.id}>
            <Link to={`/negocios/${n.id}`} className="block rounded-panel border border-linea bg-white p-5 hover:border-niebla">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="truncate font-semibold text-tinta">{n.inmuebleTitulo}</h2>
                  <p className="truncate text-sm text-niebla">Vende {n.propietarioNombre}
                    {n.clienteCompradorNombre && ` · compra ${n.clienteCompradorNombre}`}</p>
                </div>
                {n.estado === 'ABIERTO'
                  ? <Semaforo listo={n.listoParaFirmar} texto={n.semaforo} />
                  : <span className="rounded-full bg-papel px-2.5 py-1 text-xs font-medium text-grafito">{n.estado === 'FIRMADO' ? 'Firmado' : 'Cancelado'}</span>}
              </div>
              <div className="mt-4 flex items-center gap-3">
                <div className="flex-1"><BarraAvance porcentaje={n.porcentajeAvance} /></div>
                <span className="cifra text-sm font-medium text-tinta">{n.porcentajeAvance} %</span>
              </div>
              <p className="mt-2 text-xs text-niebla">
                {n.fechaFirmaTentativa ? `Firma tentativa: ${formatoFecha(n.fechaFirmaTentativa)}` : 'Sin fecha de firma'}
              </p>
            </Link>
          </li>
        ))}
        {negocios && negocios.length === 0 && (
          <li className="rounded-panel border border-dashed border-linea p-6 text-sm text-niebla">
            Todavía no hay negocios. Abre uno cuando un propietario te encargue vender su inmueble.
          </li>
        )}
      </ul>
    </div>
  )
}
