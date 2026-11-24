import { useEffect, useRef, useState, type DragEvent, type MouseEvent } from 'react'
import type { FotoInmueble } from '../api/tipos'
import { claseBotonSecundario } from './Formulario'
import { useUrlFoto } from './FotoInmueble'

export interface CambioFoto {
  /** La imagen nueva ya reducida (JPEG), o null si no se eligió otra. */
  blob: Blob | null
  quitar: boolean
  focoX: number
  focoY: number
  focoCambiado: boolean
}

const LADO_MAXIMO = 1600
const MAX_ARCHIVO = 30 * 1024 * 1024

/**
 * Lee la imagen que eligió el asesor y la deja como un JPEG de máximo 1600 px. Se hace aquí, en el navegador, para que
 * sirva cualquier formato que él sepa abrir (JPG, PNG, WebP, AVIF, HEIC en Safari…), se respete la orientación de las
 * fotos de celular y se suba un archivo liviano en vez de los 8 MB de una foto sin reducir.
 */
export async function prepararImagen(archivo: File, ladoMaximo = LADO_MAXIMO, calidad = 0.86): Promise<Blob> {
  if (!archivo.type.startsWith('image/') && archivo.type !== '') throw new Error('Ese archivo no es una imagen.')
  if (archivo.size > MAX_ARCHIVO) throw new Error('La imagen pesa más de 30 MB. Elige una más liviana.')
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(archivo, { imageOrientation: 'from-image' })
  } catch {
    throw new Error('No se pudo leer esa imagen. Prueba con una foto JPG o PNG.')
  }
  const escala = Math.min(1, ladoMaximo / Math.max(bitmap.width, bitmap.height))
  const lienzo = document.createElement('canvas')
  lienzo.width = Math.max(1, Math.round(bitmap.width * escala))
  lienzo.height = Math.max(1, Math.round(bitmap.height * escala))
  const contexto = lienzo.getContext('2d')
  if (!contexto) throw new Error('Tu navegador no pudo procesar la imagen.')
  contexto.fillStyle = '#ffffff'           // lo transparente (PNG) queda sobre blanco
  contexto.fillRect(0, 0, lienzo.width, lienzo.height)
  contexto.drawImage(bitmap, 0, 0, lienzo.width, lienzo.height)
  bitmap.close()
  const blob = await new Promise<Blob | null>((ok) => lienzo.toBlob(ok, 'image/jpeg', calidad))
  if (!blob) throw new Error('No se pudo preparar la imagen. Intenta con otra.')
  return blob
}

/**
 * Elegir la foto de un inmueble y el punto que debe quedar visible. Muestra la imagen completa (para tocar lo que
 * importa) y, al lado, cómo se verá recortada en el inventario y en el inicio: así una foto vertical, una panorámica
 * o una cuadrada se ven bien en los dos sitios.
 */
export function SelectorFoto({ inmuebleId, foto, titulo, onCambio }: {
  inmuebleId: number | null
  foto: FotoInmueble | null
  titulo: string
  onCambio: (c: CambioFoto) => void
}) {
  const [blob, setBlob] = useState<Blob | null>(null)
  const [urlNueva, setUrlNueva] = useState<string | null>(null)
  const [quitar, setQuitar] = useState(false)
  const [focoX, setFocoX] = useState(50)
  const [focoY, setFocoY] = useState(50)
  const [focoCambiado, setFocoCambiado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [procesando, setProcesando] = useState(false)
  const [encima, setEncima] = useState(false)
  const entrada = useRef<HTMLInputElement>(null)

  const urlActual = useUrlFoto(inmuebleId ?? 0, quitar ? null : foto, 'grande')
  const url = blob ? urlNueva : quitar ? null : urlActual

  // El encuadre guardado llega junto con el inmueble: se toma una sola vez, mientras el asesor no haya tocado nada.
  const version = foto?.version
  useEffect(() => {
    if (foto && !focoCambiado && !blob) { setFocoX(foto.focoX); setFocoY(foto.focoY) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version])

  useEffect(() => { onCambio({ blob, quitar, focoX, focoY, focoCambiado }) }, [blob, quitar, focoX, focoY, focoCambiado, onCambio])
  useEffect(() => () => { if (urlNueva) URL.revokeObjectURL(urlNueva) }, [urlNueva])

  const elegir = async (archivo: File | undefined) => {
    if (!archivo) return
    setError(null)
    setProcesando(true)
    try {
      const reducida = await prepararImagen(archivo)
      setBlob(reducida)
      setUrlNueva(URL.createObjectURL(reducida))
      setQuitar(false)
      setFocoX(50); setFocoY(50); setFocoCambiado(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo usar esa imagen.')
    } finally {
      setProcesando(false)
      if (entrada.current) entrada.current.value = ''
    }
  }

  const alTocar = (e: MouseEvent<HTMLElement>) => {
    const caja = e.currentTarget.getBoundingClientRect()
    setFocoX(Math.round(Math.min(100, Math.max(0, ((e.clientX - caja.left) / caja.width) * 100))))
    setFocoY(Math.round(Math.min(100, Math.max(0, ((e.clientY - caja.top) / caja.height) * 100))))
    setFocoCambiado(true)
  }

  const alSoltar = (e: DragEvent) => {
    e.preventDefault()
    setEncima(false)
    void elegir(e.dataTransfer.files?.[0])
  }

  const posicion = { objectPosition: `${focoX}% ${focoY}%` }

  return (
    <section aria-labelledby="titulo-foto">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="titulo-foto" className="text-sm font-medium text-tinta">Foto del inmueble</h2>
        <div className="flex gap-2">
          <button type="button" onClick={() => entrada.current?.click()} disabled={procesando} className={claseBotonSecundario}>
            {procesando ? 'Preparando…' : url ? 'Cambiar foto' : 'Elegir foto'}
          </button>
          {(url || (foto && !quitar)) && (
            <button type="button" onClick={() => { setBlob(null); setUrlNueva(null); setQuitar(true); setFocoCambiado(false) }}
              className={claseBotonSecundario}>Quitar</button>
          )}
        </div>
      </div>
      <input ref={entrada} type="file" accept="image/*" className="sr-only" tabIndex={-1} aria-label="Elegir la foto del inmueble"
        onChange={(e) => void elegir(e.target.files?.[0])} />

      {!url && (
        <div onDragOver={(e) => { e.preventDefault(); setEncima(true) }} onDragLeave={() => setEncima(false)} onDrop={alSoltar}
          className={`mt-3 grid min-h-32 place-items-center rounded-panel border border-dashed px-4 py-8 text-center text-sm ${
            encima ? 'border-cobalto bg-cobalto-suave text-prusia' : 'border-niebla text-niebla'}`}>
          {quitar && foto ? 'La foto se quitará al guardar.' : 'Arrastra una foto aquí, o elige una del inmueble o de la zona. Funciona con cualquier formato y tamaño.'}
        </div>
      )}

      {url && (
        <div className="mt-3 grid gap-5 md:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
          <div>
            <p className="mb-1.5 text-xs text-niebla">Toca lo más importante de la foto para que siempre se vea.</p>
            <div className="relative inline-block max-w-full cursor-crosshair overflow-hidden rounded-panel bg-papel" onClick={alTocar}>
              <img src={url} alt={`Foto de ${titulo || 'el inmueble'}, completa`} draggable={false}
                className="block max-h-72 max-w-full object-contain" />
              <span className="pointer-events-none absolute size-7 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_0_1.5px_rgb(12_47_115)]"
                style={{ left: `${focoX}%`, top: `${focoY}%` }} aria-hidden="true" />
            </div>
            <div className="mt-3 grid grid-cols-2 gap-4">
              {([['Horizontal', focoX, setFocoX], ['Vertical', focoY, setFocoY]] as const).map(([nombre, valor, set]) => (
                <label key={nombre} className="text-xs text-niebla">
                  {nombre}
                  <input type="range" min={0} max={100} value={valor} className="mt-1 w-full accent-[var(--color-cobalto)]"
                    onChange={(e) => { set(Number(e.target.value)); setFocoCambiado(true) }} />
                </label>
              ))}
            </div>
          </div>
          <div className="space-y-4">
            <div>
              <p className="mb-1.5 text-xs text-niebla">Así se verá en el inventario</p>
              <div className="aspect-[5/2] overflow-hidden rounded-panel bg-prusia">
                <img src={url} alt="" draggable={false} className="size-full object-cover" style={posicion} />
              </div>
            </div>
            <div>
              <p className="mb-1.5 text-xs text-niebla">Así se verá en el inicio</p>
              <div className="size-14 overflow-hidden rounded-sm bg-prusia">
                <img src={url} alt="" draggable={false} className="size-full object-cover" style={posicion} />
              </div>
            </div>
          </div>
        </div>
      )}
      {error && <p role="alert" className="mt-3 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
    </section>
  )
}
