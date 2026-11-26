import { useState } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { SolicitudDelComprador } from '../api/tipos'
import { Aviso, claseBotonPrimario, claseBotonSecundario, claseInput } from './Formulario'

/**
 * «Quiero más información»: un mensaje para el asesor. No envía ningún correo: queda como aviso en el inicio del asesor,
 * que decide cómo responder.
 */
export function CajaMensajeAsesor({ inmuebleId, etiqueta, onEnviado }: {
  inmuebleId: number | null
  etiqueta: string
  onEnviado?: (s: SolicitudDelComprador) => void
}) {
  const [abierta, setAbierta] = useState(false)
  const [mensaje, setMensaje] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [listo, setListo] = useState(false)

  const enviar = async () => {
    setEnviando(true); setError(null)
    try {
      const s = await api<SolicitudDelComprador>('/comprador/solicitudes', {
        method: 'POST', body: JSON.stringify({ inmuebleId, mensaje }),
      })
      setMensaje(''); setAbierta(false); setListo(true)
      onEnviado?.(s)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo enviar su mensaje. Intente de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  if (!abierta) {
    return (
      <div>
        <button onClick={() => { setAbierta(true); setListo(false) }} className={claseBotonPrimario}>{etiqueta}</button>
        {listo && <div className="mt-3"><Aviso tipo="info">Listo: su asesor recibió su mensaje y le responderá.</Aviso></div>}
      </div>
    )
  }
  return (
    <div>
      <label htmlFor={`mensaje-${inmuebleId ?? 'general'}`} className="mb-1.5 block text-sm font-medium text-tinta">Su mensaje para el asesor</label>
      <textarea id={`mensaje-${inmuebleId ?? 'general'}`} value={mensaje} onChange={(e) => setMensaje(e.target.value)} rows={3} maxLength={1000}
        placeholder="Por ejemplo: quiero visitarlo el sábado en la mañana." className={claseInput} />
      {error && <div className="mt-2"><Aviso tipo="error">{error}</Aviso></div>}
      <div className="mt-3 flex flex-wrap gap-2">
        <button onClick={() => void enviar()} disabled={enviando || mensaje.trim() === ''} className={claseBotonPrimario}>
          {enviando ? 'Enviando…' : 'Enviar mensaje'}
        </button>
        <button onClick={() => setAbierta(false)} disabled={enviando} className={claseBotonSecundario}>Cancelar</button>
      </div>
    </div>
  )
}
