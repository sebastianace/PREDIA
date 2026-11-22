import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { EstadoRecordatorio, Recordatorio } from '../api/tipos'
import { Aviso, Campo, claseBotonPrimario, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { formatoFechaHora } from '../formato'

const pestanas: { estado: EstadoRecordatorio; nombre: string }[] = [
  { estado: 'SUGERIDO', nombre: 'Por aprobar' },
  { estado: 'ENVIADO', nombre: 'Enviados' },
  { estado: 'ERROR_ENVIO', nombre: 'Con error' },
  { estado: 'DESCARTADO', nombre: 'Descartados' },
]

/** Bandeja del asesor: la IA redacta, tú revisas y aprueban el envío. Sin aprobación no sale ningún correo. */
export function Recordatorios() {
  const [estado, setEstado] = useState<EstadoRecordatorio>('SUGERIDO')
  const [lista, setLista] = useState<Recordatorio[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const cargar = useCallback(() => {
    setLista(null)
    api<Recordatorio[]>(`/recordatorios?estado=${estado}`).then(setLista).catch((e) => setError(e.message))
  }, [estado])
  useEffect(() => { cargar() }, [cargar])

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">Recordatorios</h1>
      <p className="mt-1 text-niebla">
        Mensajes para los propietarios con lo que les falta. Revisa el texto, edítalo si quieres y apruébalo para enviarlo.
      </p>

      <div role="tablist" aria-label="Estado" className="-mx-4 mt-6 flex gap-2 overflow-x-auto px-4 pb-1 md:mx-0 md:px-0">
        {pestanas.map((p) => (
          <button key={p.estado} role="tab" aria-selected={p.estado === estado} onClick={() => setEstado(p.estado)}
            className={`shrink-0 rounded-full border px-3.5 py-1.5 text-sm ${p.estado === estado ? 'border-tinta bg-tinta font-medium text-white' : 'border-linea bg-white text-grafito hover:border-niebla'}`}>
            {p.nombre}
          </button>
        ))}
      </div>

      {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}
      {!lista && !error && <p className="mt-6 text-niebla">Cargando…</p>}

      <ul className="mt-6 space-y-4">
        {lista?.map((r) => <Tarjeta key={r.id} recordatorio={r} onCambio={cargar} />)}
        {lista && lista.length === 0 && (
          <li className="rounded-panel border border-dashed border-linea p-6 text-sm text-niebla">
            {estado === 'SUGERIDO'
              ? 'No hay recordatorios por aprobar. Cada mañana Predia deja aquí un borrador por cada negocio con documentos pendientes.'
              : 'No hay recordatorios en este estado.'}
          </li>
        )}
      </ul>
    </div>
  )
}

function Tarjeta({ recordatorio: r, onCambio }: { recordatorio: Recordatorio; onCambio: () => void }) {
  const [asunto, setAsunto] = useState(r.asunto)
  const [cuerpo, setCuerpo] = useState(r.cuerpo)
  const [advertencias, setAdvertencias] = useState<string[]>(r.advertencias)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const editable = r.estado === 'SUGERIDO'
  const modificado = asunto !== r.asunto || cuerpo !== r.cuerpo

  const ejecutar = async (accion: () => Promise<void>) => {
    setOcupado(true); setError(null); setAviso(null)
    try { await accion() } catch (e) { setError(e instanceof ErrorApi ? e.message : 'No se pudo completar la acción.') }
    finally { setOcupado(false) }
  }

  const guardar = () => ejecutar(async () => {
    const guardado = await api<Recordatorio>(`/recordatorios/${r.id}`, { method: 'PUT', body: JSON.stringify({ asunto, cuerpo }) })
    setAdvertencias(guardado.advertencias)
    setAviso('Cambios guardados.')
  })

  const aprobar = () => ejecutar(async () => {
    if (modificado) await api(`/recordatorios/${r.id}`, { method: 'PUT', body: JSON.stringify({ asunto, cuerpo }) })
    if (!window.confirm(`Se enviará este correo a ${r.destinatarioEmail ?? 'el propietario'}. ¿Aprobar y enviar?`)) return
    const enviado = await api<Recordatorio>(`/recordatorios/${r.id}/aprobar`, { method: 'POST' })
    if (enviado.estado === 'ERROR_ENVIO') setError(enviado.error ?? 'No se pudo enviar el correo.')
    else onCambio()
  })

  const descartar = () => ejecutar(async () => {
    await api(`/recordatorios/${r.id}/descartar`, { method: 'POST' })
    onCambio()
  })

  return (
    <li className="rounded-panel border border-linea bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <Link to={`/negocios/${r.negocioId}`} className="font-semibold text-tinta hover:text-cobalto">{r.inmuebleTitulo}</Link>
          <p className="text-sm text-niebla">
            Para {r.propietarioNombre}
            {r.destinatarioEmail ? ` · ${r.destinatarioEmail}` : ' · sin correo registrado'}
          </p>
        </div>
        <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${r.origenTexto === 'IA' ? 'bg-cobalto-suave text-cobalto-hondo' : 'bg-papel text-grafito'}`}>
          {r.origenTexto === 'IA' ? 'Redactado por la IA' : 'Plantilla'}
        </span>
      </div>

      <p className="mt-3 text-xs text-niebla">
        Pide: {r.items.map((i) => i.nombre).join(' · ')}
      </p>

      {editable ? (
        <div className="mt-4 space-y-3">
          <Campo etiqueta="Asunto">{(idc) => <input id={idc} value={asunto} onChange={(e) => setAsunto(e.target.value)} className={claseInput} />}</Campo>
          <Campo etiqueta="Mensaje">
            {(idc) => <textarea id={idc} rows={10} value={cuerpo} onChange={(e) => setCuerpo(e.target.value)} className={claseInput} />}
          </Campo>
        </div>
      ) : (
        <div className="mt-4 rounded-control bg-papel p-4 text-sm">
          <p className="font-medium text-tinta">{r.asunto}</p>
          <p className="mt-2 whitespace-pre-line text-grafito">{r.cuerpo}</p>
        </div>
      )}

      {advertencias.length > 0 && (
        <ul className="mt-3 space-y-2">
          {advertencias.map((a) => <li key={a}><Aviso tipo="error">{a} Revísalo antes de enviar.</Aviso></li>)}
        </ul>
      )}
      {r.estado === 'ENVIADO' && (
        <p className="mt-3 text-xs text-niebla">Enviado el {formatoFechaHora(r.enviadoEn)} · aprobó {r.aprobadoPor ?? '—'}</p>
      )}
      {r.estado === 'ERROR_ENVIO' && <div className="mt-3"><Aviso tipo="error">{r.error}</Aviso></div>}
      {error && <div className="mt-3"><Aviso tipo="error">{error}</Aviso></div>}
      {aviso && <div className="mt-3"><Aviso tipo="info">{aviso}</Aviso></div>}

      {(editable || r.estado === 'ERROR_ENVIO') && (
        <div className="mt-4 flex flex-wrap gap-2">
          <button onClick={() => void aprobar()} disabled={ocupado || !r.destinatarioEmail} className={claseBotonPrimario}>
            {r.estado === 'ERROR_ENVIO' ? 'Reintentar el envío' : 'Aprobar y enviar'}
          </button>
          {editable && (
            <>
              <button onClick={() => void guardar()} disabled={ocupado || !modificado} className={claseBotonSecundario}>Guardar cambios</button>
              <button onClick={() => void descartar()} disabled={ocupado} className={claseBotonSecundario}>Descartar</button>
            </>
          )}
        </div>
      )}
      {!r.destinatarioEmail && editable && (
        <p className="mt-2 text-xs text-ambar">Para enviarlo, agrega el correo del propietario.</p>
      )}
    </li>
  )
}
