import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'
import { api } from '../api/cliente'
import { Avatar, type FotoPerfil } from './Avatar'
import { claseBotonSecundario } from './Formulario'
import { Icono } from './Iconos'
import { prepararImagen } from './prepararImagen'

export interface FotoElegida { blob: Blob; focoX: number; focoY: number }
/** Lo que el asesor decidió hacer con la foto en un formulario; se aplica al guardar a la persona. */
export type CambioFoto = { accion: 'subir'; foto: FotoElegida } | { accion: 'quitar' } | null

/** Aplica el cambio de foto elegido, una vez guardada la persona (la ruta es la de su foto). */
export async function aplicarCambioFoto(ruta: string, cambio: CambioFoto): Promise<void> {
  if (!cambio) return
  if (cambio.accion === 'quitar') {
    await api(ruta, { method: 'DELETE' })
    return
  }
  const datos = new FormData()
  datos.append('archivo', cambio.foto.blob, 'perfil.jpg')
  datos.append('focoX', String(Math.round(cambio.foto.focoX)))
  datos.append('focoY', String(Math.round(cambio.foto.focoY)))
  await api(ruta, { method: 'PUT', body: datos })
}

const LADO = 76 // px del recorte circular (4.75 rem)
const limitar = (v: number) => Math.max(0, Math.min(100, v))

/** La foto recién elegida en un círculo: se arrastra (o se usan las flechas) para elegir qué parte se ve. */
function RecorteCircular({ url, foto, onFoco }: { url: string; foto: FotoElegida; onFoco: (x: number, y: number) => void }) {
  const [medidas, setMedidas] = useState<{ ancho: number; alto: number } | null>(null)
  const ultimo = useRef<{ x: number; y: number } | null>(null)

  // Cuánto sobra de la imagen en cada eje una vez que el lado corto llena el círculo.
  const sobrante = medidas
    ? (() => { const e = LADO / Math.min(medidas.ancho, medidas.alto); return { x: medidas.ancho * e - LADO, y: medidas.alto * e - LADO } })()
    : { x: 0, y: 0 }

  const mover = (dx: number, dy: number) => {
    onFoco(sobrante.x > 1 ? limitar(foto.focoX - (dx / sobrante.x) * 100) : foto.focoX,
      sobrante.y > 1 ? limitar(foto.focoY - (dy / sobrante.y) * 100) : foto.focoY)
  }
  const alMover = (e: PointerEvent<HTMLDivElement>) => {
    if (!ultimo.current) return
    mover(e.clientX - ultimo.current.x, e.clientY - ultimo.current.y)
    ultimo.current = { x: e.clientX, y: e.clientY }
  }
  const alTeclear = (e: KeyboardEvent<HTMLDivElement>) => {
    const paso = 12
    const flechas: Record<string, [number, number]> = { ArrowLeft: [paso, 0], ArrowRight: [-paso, 0], ArrowUp: [0, paso], ArrowDown: [0, -paso] }
    const f = flechas[e.key]
    if (f) { e.preventDefault(); mover(f[0], f[1]) }
  }

  return (
    <div role="group" tabIndex={0} aria-label="Elige qué parte de la foto se ve: arrástrala o usa las flechas del teclado"
      onPointerDown={(e) => { ultimo.current = { x: e.clientX, y: e.clientY }; e.currentTarget.setPointerCapture(e.pointerId) }}
      onPointerMove={alMover} onPointerUp={() => { ultimo.current = null }} onPointerCancel={() => { ultimo.current = null }}
      onKeyDown={alTeclear}
      style={{ width: LADO, height: LADO }}
      className="shrink-0 cursor-grab touch-none overflow-hidden rounded-full ring-2 ring-cobalto/60 focus-visible:outline-2 active:cursor-grabbing">
      <img src={url} alt="" draggable={false} onLoad={(e) => setMedidas({ ancho: e.currentTarget.naturalWidth, alto: e.currentTarget.naturalHeight })}
        className="size-full select-none object-cover" style={{ objectPosition: `${foto.focoX}% ${foto.focoY}%` }} />
    </div>
  )
}

/**
 * Elegir, cambiar o quitar la foto de una persona. No sube nada por su cuenta: devuelve la decisión (`valor`) y el
 * formulario la aplica con `aplicarCambioFoto` cuando guarda a la persona (que puede no existir todavía).
 */
export function CampoFotoPerfil({ nombre, actual, valor, onChange, ayuda }: {
  nombre: string
  /** La foto que ya tiene (al editar); sin versión, no tiene. */
  actual?: FotoPerfil
  valor: CambioFoto
  onChange: (v: CambioFoto) => void
  ayuda?: ReactNode
}) {
  const entrada = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [url, setUrl] = useState<string | null>(null)
  const nueva = valor?.accion === 'subir' ? valor.foto : null
  const blob = nueva?.blob

  useEffect(() => {
    if (!blob) { setUrl(null); return }
    const u = URL.createObjectURL(blob)
    setUrl(u)
    return () => URL.revokeObjectURL(u)
  }, [blob])

  const elegir = async (archivo?: File) => {
    if (!archivo) return
    setError(null)
    setOcupado(true)
    try {
      onChange({ accion: 'subir', foto: { blob: await prepararImagen(archivo, 1024), focoX: 50, focoY: 50 } })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo leer la imagen.')
    } finally {
      setOcupado(false)
      if (entrada.current) entrada.current.value = ''
    }
  }

  const tieneActual = !!actual?.version
  const seQuita = valor?.accion === 'quitar'

  return (
    <div>
      <span className="mb-1.5 block text-sm font-medium text-tinta">Foto (opcional)</span>
      <div className="flex flex-wrap items-center gap-4">
        {nueva && url ? (
          <RecorteCircular url={url} foto={nueva} onFoco={(x, y) => onChange({ accion: 'subir', foto: { ...nueva, focoX: x, focoY: y } })} />
        ) : (
          <Avatar nombre={nombre || 'Foto'} tamano="gigante" foto={seQuita ? undefined : actual} />
        )}
        <div className="min-w-0">
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={ocupado} onClick={() => entrada.current?.click()} className={claseBotonSecundario}>
              <span className="flex items-center gap-2"><Icono nombre="subir" className="size-4" />
                {ocupado ? 'Preparando…' : nueva || (tieneActual && !seQuita) ? 'Cambiar foto' : 'Subir foto'}</span>
            </button>
            {(nueva || (tieneActual && !seQuita)) && (
              <button type="button" onClick={() => onChange(nueva ? null : { accion: 'quitar' })} className={claseBotonSecundario}>Quitar</button>
            )}
            {seQuita && <button type="button" onClick={() => onChange(null)} className={claseBotonSecundario}>Deshacer</button>}
          </div>
          <p className="mt-1.5 text-xs text-niebla">
            {nueva ? 'Arrastra la foto para elegir qué parte se ve. Se guarda al guardar el formulario.'
              : seQuita ? 'La foto se quitará al guardar.' : ayuda ?? 'Se recorta en círculo; Predia guarda una copia reducida, nunca el archivo original.'}
          </p>
          {error && <p role="alert" className="mt-1.5 text-xs text-red-700">{error}</p>}
        </div>
      </div>
      <input ref={entrada} type="file" accept="image/*" className="sr-only" tabIndex={-1} aria-label="Elegir foto"
        onChange={(e) => void elegir(e.target.files?.[0])} />
    </div>
  )
}
