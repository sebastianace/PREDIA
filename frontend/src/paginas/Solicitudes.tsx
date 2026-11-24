import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { SolicitudPendiente } from '../api/tipos'
import { Aviso, claseBotonSecundario } from '../componentes/Formulario'
import { formatoFechaHora } from '../formato'

/**
 * Los mensajes que los compradores dejan desde su portal («Quiero más información»). Predia no envía ni responde nada
 * por su cuenta: el asesor contacta a la persona y marca el mensaje como atendido.
 */
export function Solicitudes() {
  const [lista, setLista] = useState<SolicitudPendiente[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const cargar = useCallback(() => {
    api<SolicitudPendiente[]>('/solicitudes-comprador').then(setLista).catch((e) => setError(e.message))
  }, [])
  useEffect(() => { cargar() }, [cargar])

  const atender = async (id: number) => {
    setError(null)
    try {
      await api(`/solicitudes-comprador/${id}/atender`, { method: 'POST' })
      cargar()
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo marcar como atendido.')
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <Link to="/" className="text-sm text-niebla hover:text-tinta">← Inicio</Link>
      <h1 className="ancha mt-2 text-[32px] font-semibold leading-[1.05] md:text-5xl">Mensajes de compradores</h1>
      <p className="mt-1 text-niebla">Lo que tus clientes te escribieron desde su portal. Contáctalos y márcalos como atendidos.</p>
      {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}
      {lista && lista.length === 0 && <p className="mt-8 border-y border-linea py-8 text-center text-niebla">No hay mensajes por atender.</p>}
      <ul className="mt-6 divide-y divide-linea border-y border-linea">
        {lista?.map((s) => (
          <li key={s.id} className="py-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <Link to={`/clientes/${s.clienteId}`} className="font-medium text-tinta hover:text-cobalto">{s.clienteNombre}</Link>
                <p className="text-xs text-niebla">{formatoFechaHora(s.creadaEn)}{s.inmuebleTitulo && ` · sobre ${s.inmuebleTitulo}`}</p>
              </div>
              <button onClick={() => void atender(s.id)} className={claseBotonSecundario}>Marcar como atendido</button>
            </div>
            <p className="mt-2 whitespace-pre-line text-grafito [overflow-wrap:anywhere]">{s.mensaje}</p>
          </li>
        ))}
      </ul>
    </div>
  )
}
