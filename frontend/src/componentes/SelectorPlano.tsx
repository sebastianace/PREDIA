import { useEffect, useRef, useState } from 'react'
import { apiBlob } from '../api/cliente'
import { claseBotonSecundario } from './Formulario'
import { prepararImagen } from './SelectorFoto'

export interface CambioPlano {
  /** El plano nuevo ya reducido (JPEG de hasta 2.000 px), o null si no se eligió otro. */
  blob: Blob | null
  quitar: boolean
}

/**
 * Subir el plano de un inmueble. A diferencia de la foto, el plano se guarda a mayor resolución (2.000 px, calidad
 * alta) y sin recortes: el comprador debe poder acercarse y leer las medidas.
 */
export function SelectorPlano({ inmuebleId, version, onCambio }: {
  inmuebleId: number | null
  version: string | null
  onCambio: (c: CambioPlano) => void
}) {
  const [blob, setBlob] = useState<Blob | null>(null)
  const [urlNueva, setUrlNueva] = useState<string | null>(null)
  const [urlActual, setUrlActual] = useState<string | null>(null)
  const [quitar, setQuitar] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [procesando, setProcesando] = useState(false)
  const entrada = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (inmuebleId == null || !version) { setUrlActual(null); return }
    let vigente = true
    let url: string | null = null
    apiBlob(`/inmuebles/${inmuebleId}/plano`).then((b) => {
      url = URL.createObjectURL(b)
      if (vigente) setUrlActual(url)
    }).catch(() => { if (vigente) setUrlActual(null) })
    return () => { vigente = false; if (url) URL.revokeObjectURL(url) }
  }, [inmuebleId, version])

  useEffect(() => { onCambio({ blob, quitar }) }, [blob, quitar, onCambio])
  useEffect(() => () => { if (urlNueva) URL.revokeObjectURL(urlNueva) }, [urlNueva])

  const elegir = async (archivo: File | undefined) => {
    if (!archivo) return
    setError(null); setProcesando(true)
    try {
      const reducida = await prepararImagen(archivo, 2000, 0.92)
      setBlob(reducida); setUrlNueva(URL.createObjectURL(reducida)); setQuitar(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo usar esa imagen.')
    } finally {
      setProcesando(false)
      if (entrada.current) entrada.current.value = ''
    }
  }

  const url = blob ? urlNueva : quitar ? null : urlActual
  const hayPlano = blob != null || (!quitar && version != null)

  return (
    <section aria-labelledby="titulo-plano">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="titulo-plano" className="text-sm font-medium text-tinta">Plano del inmueble</h2>
        <div className="flex gap-2">
          <button type="button" onClick={() => entrada.current?.click()} disabled={procesando} className={claseBotonSecundario}>
            {procesando ? 'Preparando…' : hayPlano ? 'Cambiar plano' : 'Agregar plano'}
          </button>
          {hayPlano && (
            <button type="button" onClick={() => { setBlob(null); setUrlNueva(null); setQuitar(true) }} className={claseBotonSecundario}>Quitar</button>
          )}
        </div>
      </div>
      <input ref={entrada} type="file" accept="image/*" className="sr-only" aria-label="Elegir la imagen del plano"
        onChange={(e) => void elegir(e.target.files?.[0])} />
      <p className="mt-1 text-xs text-niebla">Una imagen del plano (JPG o PNG). Solo la ven los compradores a quienes les compartas este inmueble.</p>
      {url && <img src={url} alt="Plano del inmueble" className="mt-3 max-h-64 w-full rounded-control border border-linea bg-white object-contain" />}
      {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
    </section>
  )
}
