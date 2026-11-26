import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { ImagenInmueble, TipoImagen } from '../api/tipos'
import { claseBotonSecundario } from './Formulario'
import { useUrlImagen } from './FotoInmueble'
import { Icono } from './Iconos'
import { prepararImagen } from './prepararImagen'

/** Lo mismo que el servidor: una ficha rara vez necesita más y la galería no crece sin control. */
const MAXIMO = 12

/** La imagen según su tipo: una foto se recorta con su punto de interés; un plano se muestra completo. */
export function ImagenDeGaleria({ ruta, imagen, tam, titulo }: { ruta: string; imagen: ImagenInmueble; tam: 'miniatura' | 'grande'; titulo: string }) {
  const url = useUrlImagen(`${ruta}/${imagen.id}`, imagen.version, tam)
  const plano = imagen.tipo === 'PLANO'
  return (
    <div className={`relative size-full ${plano ? 'bg-cobalto-suave' : 'bg-prusia'}`}>
      {url && (
        <img src={url} alt={titulo} draggable={false} loading="lazy"
          className={`absolute inset-0 size-full animate-[aparecer_400ms_ease-out_both] ${plano ? 'object-contain p-1.5' : 'object-cover'}`}
          style={plano ? undefined : { objectPosition: `${imagen.focoX}% ${imagen.focoY}%` }} />
      )}
    </div>
  )
}

/**
 * Las fotos y planos de un inmueble, en un solo orden. Cada cambio se guarda al instante (subir, quitar, reordenar,
 * cambiar de foto a plano o poner un nombre): el asesor ve lo mismo que verá el comprador. La primera foto es la portada
 * del inventario. Las imágenes se reducen aquí, en el navegador, antes de subirlas.
 */
export function GaleriaInmueble({ inmuebleId }: { inmuebleId: number }) {
  const ruta = `/inmuebles/${inmuebleId}/imagenes`
  const [imagenes, setImagenes] = useState<ImagenInmueble[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [subiendo, setSubiendo] = useState<string | null>(null)
  const [seleccion, setSeleccion] = useState<number | null>(null)
  const [arrastrando, setArrastrando] = useState<number | null>(null)
  const entradaFotos = useRef<HTMLInputElement>(null)
  const entradaPlanos = useRef<HTMLInputElement>(null)

  const cargar = () => api<ImagenInmueble[]>(ruta).then(setImagenes).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar la galería.'))
  useEffect(() => { void cargar() }, [inmuebleId]) // eslint-disable-line react-hooks/exhaustive-deps

  const fallo = (e: unknown, texto: string) => setError(e instanceof ErrorApi || e instanceof Error ? e.message : texto)

  const agregar = async (archivos: FileList | null, tipo: TipoImagen) => {
    if (!archivos || archivos.length === 0 || !imagenes) return
    setError(null)
    const cupo = MAXIMO - imagenes.length
    const elegidos = Array.from(archivos).slice(0, Math.max(0, cupo))
    const avisos: string[] = []
    if (archivos.length > elegidos.length) avisos.push(`Solo caben ${MAXIMO} imágenes: se omitieron ${archivos.length - elegidos.length}.`)
    let actuales = imagenes
    for (const [n, archivo] of elegidos.entries()) {
      setSubiendo(`Subiendo ${n + 1} de ${elegidos.length}…`)
      try {
        const blob = await prepararImagen(archivo, tipo === 'PLANO' ? 2000 : 1600, tipo === 'PLANO' ? 0.92 : 0.86)
        const datos = new FormData()
        datos.append('archivo', blob, 'imagen.jpg')
        datos.append('tipo', tipo)
        if (tipo === 'PLANO') datos.append('etiqueta', 'Plano')
        const nueva = await api<ImagenInmueble>(ruta, { method: 'POST', body: datos })
        actuales = [...actuales, nueva]
        setImagenes(actuales)
      } catch (e) {
        avisos.push(`${archivo.name}: ${e instanceof Error ? e.message : 'no se pudo subir.'}`)
      }
    }
    setSubiendo(null)
    if (avisos.length > 0) setError(avisos.join(' '))
    if (entradaFotos.current) entradaFotos.current.value = ''
    if (entradaPlanos.current) entradaPlanos.current.value = ''
  }

  const reordenar = async (ids: number[]) => {
    if (!imagenes) return
    const previas = imagenes
    setImagenes(ids.map((id, orden) => ({ ...previas.find((i) => i.id === id)!, orden })))
    try {
      setImagenes(await api<ImagenInmueble[]>(`${ruta}/orden`, { method: 'PUT', body: JSON.stringify({ ids }) }))
    } catch (e) {
      setImagenes(previas); fallo(e, 'No se pudo cambiar el orden.')
    }
  }

  const mover = (indice: number, delta: number) => {
    if (!imagenes) return
    const ids = imagenes.map((i) => i.id)
    const destino = indice + delta
    if (destino < 0 || destino >= ids.length) return
    ;[ids[indice], ids[destino]] = [ids[destino], ids[indice]]
    void reordenar(ids)
  }

  const soltarSobre = (destino: number) => {
    if (!imagenes || arrastrando == null) return
    const ids = imagenes.map((i) => i.id)
    const desde = ids.indexOf(arrastrando)
    setArrastrando(null)
    if (desde < 0 || desde === destino) return
    ids.splice(destino, 0, ids.splice(desde, 1)[0])
    void reordenar(ids)
  }

  const editar = async (imagen: ImagenInmueble, cambios: Partial<Pick<ImagenInmueble, 'tipo' | 'etiqueta' | 'focoX' | 'focoY'>>) => {
    setError(null)
    try {
      const nueva = await api<ImagenInmueble>(`${ruta}/${imagen.id}`, { method: 'PATCH', body: JSON.stringify(cambios) })
      setImagenes((lista) => lista?.map((i) => (i.id === nueva.id ? nueva : i)) ?? null)
    } catch (e) {
      fallo(e, 'No se pudo guardar el cambio.')
    }
  }

  const quitar = async (imagen: ImagenInmueble) => {
    if (!window.confirm('¿Quitar esta imagen de la galería?')) return
    setError(null)
    try {
      await api(`${ruta}/${imagen.id}`, { method: 'DELETE' })
      if (seleccion === imagen.id) setSeleccion(null)
      await cargar()
    } catch (e) {
      fallo(e, 'No se pudo quitar la imagen.')
    }
  }

  const portada = imagenes?.find((i) => i.tipo === 'FOTO')?.id
  const elegida = imagenes?.find((i) => i.id === seleccion && i.tipo === 'FOTO') ?? null
  const lleno = (imagenes?.length ?? 0) >= MAXIMO

  return (
    <section aria-labelledby="titulo-galeria" className="rounded-panel border border-linea bg-white p-4 md:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="titulo-galeria" className="ancha text-lg font-semibold">Fotos y planos</h2>
          <p className="mt-0.5 text-sm text-niebla">
            {imagenes ? `${imagenes.length} de ${MAXIMO}. ` : ''}Arrastre o use las flechas para ordenar; la primera foto es la portada.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={lleno || subiendo != null} onClick={() => entradaFotos.current?.click()} className={`${claseBotonSecundario} inline-flex items-center gap-2`}>
            <Icono nombre="foto" className="size-4" />Agregar fotos
          </button>
          <button type="button" disabled={lleno || subiendo != null} onClick={() => entradaPlanos.current?.click()} className={`${claseBotonSecundario} inline-flex items-center gap-2`}>
            <Icono nombre="regla" className="size-4" />Agregar planos
          </button>
          <input ref={entradaFotos} type="file" accept="image/*" multiple hidden onChange={(e) => void agregar(e.target.files, 'FOTO')} aria-label="Elegir fotos" />
          <input ref={entradaPlanos} type="file" accept="image/*" multiple hidden onChange={(e) => void agregar(e.target.files, 'PLANO')} aria-label="Elegir planos" />
        </div>
      </div>

      {subiendo && <p role="status" className="mt-3 text-sm text-cobalto">{subiendo}</p>}
      {error && <p role="alert" className="mt-3 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
      {!imagenes && !error && <p className="mt-4 text-sm text-niebla">Cargando…</p>}
      {imagenes && imagenes.length === 0 && (
        <p className="mt-4 rounded-control border border-dashed border-linea bg-papel px-4 py-8 text-center text-sm text-niebla">
          Todavía no hay imágenes. Agrega las fotos del inmueble y, si los tiene, sus planos.
        </p>
      )}

      {imagenes && imagenes.length > 0 && (
        <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {imagenes.map((img, n) => {
            const plano = img.tipo === 'PLANO'
            const activa = seleccion === img.id
            return (
              <li key={img.id} draggable onDragStart={() => setArrastrando(img.id)} onDragEnd={() => setArrastrando(null)}
                onDragOver={(e) => e.preventDefault()} onDrop={() => soltarSobre(n)}
                className={`overflow-hidden rounded-control border bg-white ${activa ? 'border-cobalto ring-2 ring-cobalto-suave' : 'border-linea'} ${arrastrando === img.id ? 'opacity-40' : ''}`}>
                <div className="relative aspect-[4/3]">
                  <button type="button" onClick={() => setSeleccion(activa ? null : img.id)} disabled={plano}
                    aria-label={plano ? `Plano ${n + 1}` : `Elegir encuadre de la foto ${n + 1}`} className="absolute inset-0 block size-full cursor-pointer disabled:cursor-default">
                    <ImagenDeGaleria ruta={ruta} imagen={img} tam="miniatura" titulo={img.etiqueta ?? `Imagen ${n + 1}`} />
                  </button>
                  {img.id === portada && <span className="pointer-events-none absolute left-2 top-2 rounded-full bg-white px-2 py-0.5 text-xs font-medium text-prusia">Portada</span>}
                  <button type="button" onClick={() => void quitar(img)} aria-label={`Quitar la imagen ${n + 1}`}
                    className="absolute right-2 top-2 flex size-7 items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80">
                    <Icono nombre="equis" className="size-4" />
                  </button>
                  <div className="absolute inset-x-2 bottom-2 flex justify-between">
                    <button type="button" onClick={() => mover(n, -1)} disabled={n === 0} aria-label="Mover antes"
                      className="flex size-7 items-center justify-center rounded-full bg-white/90 text-prusia hover:bg-white disabled:invisible">
                      <Icono nombre="izquierda" className="size-4" />
                    </button>
                    <button type="button" onClick={() => mover(n, 1)} disabled={n === imagenes.length - 1} aria-label="Mover después"
                      className="flex size-7 items-center justify-center rounded-full bg-white/90 text-prusia hover:bg-white disabled:invisible">
                      <Icono nombre="derecha" className="size-4" />
                    </button>
                  </div>
                </div>
                <div className="flex items-center gap-2 p-2">
                  <input defaultValue={img.etiqueta ?? ''} maxLength={60} placeholder={plano ? 'Plano' : 'Nombre'}
                    aria-label={`Nombre de la imagen ${n + 1}`}
                    onBlur={(e) => { if (e.target.value.trim() !== (img.etiqueta ?? '')) void editar(img, { etiqueta: e.target.value }) }}
                    className="min-w-0 flex-1 rounded-control border border-transparent px-1.5 py-1 text-sm text-tinta outline-none placeholder:text-niebla hover:border-linea focus:border-cobalto" />
                  <button type="button" onClick={() => void editar(img, { tipo: plano ? 'FOTO' : 'PLANO' })}
                    title={plano ? 'Es un plano: pulse para tratarlo como foto' : 'Es una foto: pulse para tratarla como plano'}
                    className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${plano ? 'bg-cobalto-suave text-cobalto-hondo' : 'bg-papel text-grafito'}`}>
                    {plano ? 'Plano' : 'Foto'}
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {elegida && (
        <PanelEncuadre ruta={ruta} imagen={elegida} onFoco={(focoX, focoY) => void editar(elegida, { focoX, focoY })} onCerrar={() => setSeleccion(null)} />
      )}
    </section>
  )
}

/** Toque el punto que debe quedar a la vista: así se recorta la foto en el inventario y en el portal del comprador. */
function PanelEncuadre({ ruta, imagen, onFoco, onCerrar }: { ruta: string; imagen: ImagenInmueble; onFoco: (x: number, y: number) => void; onCerrar: () => void }) {
  const url = useUrlImagen(`${ruta}/${imagen.id}`, imagen.version, 'grande')
  const elegir = (e: MouseEvent<HTMLButtonElement>) => {
    const caja = e.currentTarget.getBoundingClientRect()
    onFoco(Math.round(((e.clientX - caja.left) / caja.width) * 100), Math.round(((e.clientY - caja.top) / caja.height) * 100))
  }
  return (
    <div className="mt-4 rounded-control border border-linea bg-papel p-3">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-medium text-tinta">Encuadre de la foto</p>
        <button type="button" onClick={onCerrar} className="text-sm text-niebla hover:text-tinta">Listo</button>
      </div>
      <p className="mt-0.5 text-xs text-niebla">Toque lo más importante de la foto. A la derecha ve cómo queda recortada.</p>
      <div className="mt-3 grid items-start gap-3 sm:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <button type="button" onClick={elegir} aria-label="Elegir el punto que debe quedar visible" className="relative block cursor-crosshair overflow-hidden rounded-control">
          {url ? <img src={url} alt="" draggable={false} className="block max-h-72 w-full object-contain" /> : <span className="block aspect-[4/3] bg-prusia" />}
          <span className="pointer-events-none absolute size-5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-cobalto/60 shadow"
            style={{ left: `${imagen.focoX}%`, top: `${imagen.focoY}%` }} aria-hidden="true" />
        </button>
        <div className="relative aspect-[4/3] overflow-hidden rounded-control bg-prusia">
          {url && <img src={url} alt="" className="absolute inset-0 size-full object-cover" style={{ objectPosition: `${imagen.focoX}% ${imagen.focoY}%` }} />}
        </div>
      </div>
    </div>
  )
}
