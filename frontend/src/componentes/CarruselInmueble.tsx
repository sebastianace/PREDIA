import { useCallback, useEffect, useRef, useState, type TouchEvent } from 'react'
import type { ImagenInmueble } from '../api/tipos'
import { ImagenDeGaleria } from './GaleriaInmueble'
import { useUrlImagen } from './FotoInmueble'
import { Icono } from './Iconos'
import { Terreno } from './Terreno'

const nombreDe = (img: ImagenInmueble, n: number) => img.etiqueta ?? (img.tipo === 'PLANO' ? 'Plano' : `Foto ${n + 1}`)

/**
 * Las fotos y planos de un inmueble para el comprador: una imagen grande que pasa con las flechas, con el teclado o
 * deslizando el dedo, y una tira de miniaturas. Las fotos se recortan para llenar el marco; los planos se ven
 * completos y, al tocarlos, se abren a pantalla completa para leer las medidas.
 */
export function CarruselInmueble({ ruta, imagenes, titulo, semilla }: { ruta: string; imagenes: ImagenInmueble[]; titulo: string; semilla: number }) {
  const [actual, setActual] = useState(0)
  const [abierta, setAbierta] = useState(false)
  const inicioToque = useRef<number | null>(null)
  const tira = useRef<HTMLDivElement>(null)
  const total = imagenes.length
  const ir = useCallback((delta: number) => setActual((a) => (a + delta + total) % total), [total])

  useEffect(() => { if (actual >= total) setActual(0) }, [actual, total])
  // Mantiene a la vista la miniatura elegida cuando la tira se desborda.
  useEffect(() => {
    const boton = tira.current?.children[actual] as HTMLElement | undefined
    boton?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' })
  }, [actual])

  if (total === 0) {
    return (
      <div className="relative isolate aspect-[4/3] overflow-hidden rounded-panel bg-prusia">
        <Terreno semilla={semilla} trazar niveles={12} className="absolute inset-0 -z-20 size-full text-trazo opacity-40" />
      </div>
    )
  }

  const img = imagenes[actual]
  const alTocar = (e: TouchEvent) => {
    const inicio = inicioToque.current
    inicioToque.current = null
    if (inicio == null || total < 2) return
    const dx = e.changedTouches[0].clientX - inicio
    if (Math.abs(dx) > 40) ir(dx < 0 ? 1 : -1)
  }

  return (
    <div>
      <div role="group" aria-roledescription="carrusel" aria-label={`Fotos y planos de ${titulo}`} tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'ArrowLeft') { e.preventDefault(); ir(-1) } else if (e.key === 'ArrowRight') { e.preventDefault(); ir(1) } }}
        onTouchStart={(e) => { inicioToque.current = e.touches[0].clientX }} onTouchEnd={alTocar}
        className="relative aspect-[4/3] overflow-hidden rounded-panel bg-prusia outline-offset-2">
        <button type="button" onClick={() => setAbierta(true)} className="absolute inset-0 block size-full cursor-zoom-in"
          aria-label={`Ampliar: ${nombreDe(img, actual)}`}>
          <ImagenDeGaleria ruta={ruta} imagen={img} tam="grande" titulo={`${nombreDe(img, actual)} de ${titulo}`} />
        </button>
        <span className="pointer-events-none absolute bottom-3 left-3 inline-flex items-center gap-1.5 rounded-full bg-white/92 px-3 py-1 text-xs font-medium text-prusia">
          {img.tipo === 'PLANO' && <Icono nombre="regla" className="size-3.5" />}{nombreDe(img, actual)}
        </span>
        <span className="pointer-events-none absolute bottom-3 right-3 rounded-full bg-black/60 px-3 py-1 text-xs text-white" aria-live="polite">{actual + 1} / {total}</span>
        {total > 1 && (
          <>
            <BotonFlecha lado="izquierda" onClick={() => ir(-1)} />
            <BotonFlecha lado="derecha" onClick={() => ir(1)} />
          </>
        )}
      </div>
      {total > 1 && <Precarga ruta={ruta} imagen={imagenes[(actual + 1) % total]} />}

      {total > 1 && (
        <div ref={tira} className="mt-3 flex gap-2 overflow-x-auto pb-1" role="list" aria-label="Miniaturas">
          {imagenes.map((m, n) => (
            <button key={m.id} type="button" role="listitem" onClick={() => setActual(n)} aria-label={`Ver ${nombreDe(m, n)}`} aria-current={n === actual}
              className={`relative h-14 w-[76px] shrink-0 overflow-hidden rounded-control border-2 ${n === actual ? 'border-cobalto' : 'border-transparent opacity-80 hover:opacity-100'}`}>
              <ImagenDeGaleria ruta={ruta} imagen={m} tam="miniatura" titulo="" />
              {m.tipo === 'PLANO' && <span className="absolute bottom-0.5 left-0.5 rounded-full bg-prusia px-1.5 text-[10px] leading-4 text-white">Plano</span>}
            </button>
          ))}
        </div>
      )}

      {abierta && <VisorImagen ruta={ruta} imagenes={imagenes} indice={actual} titulo={titulo} onIr={ir} onCerrar={() => setAbierta(false)} />}
    </div>
  )
}

function BotonFlecha({ lado, onClick }: { lado: 'izquierda' | 'derecha'; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} aria-label={lado === 'izquierda' ? 'Imagen anterior' : 'Imagen siguiente'}
      className={`absolute top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-full bg-white/92 text-prusia shadow-sm hover:bg-white ${lado === 'izquierda' ? 'left-3' : 'right-3'}`}>
      <Icono nombre={lado} className="size-5" />
    </button>
  )
}

/** Baja la imagen siguiente en silencio para que el paso sea inmediato. */
function Precarga({ ruta, imagen }: { ruta: string; imagen: ImagenInmueble }) {
  useUrlImagen(`${ruta}/${imagen.id}`, imagen.version, 'grande')
  return null
}

/** La imagen a pantalla completa: un toque la amplía (para leer las medidas de un plano) y otro la ajusta de nuevo. */
function VisorImagen({ ruta, imagenes, indice, titulo, onIr, onCerrar }: {
  ruta: string; imagenes: ImagenInmueble[]; indice: number; titulo: string; onIr: (d: number) => void; onCerrar: () => void
}) {
  const img = imagenes[indice]
  const url = useUrlImagen(`${ruta}/${img.id}`, img.version, 'grande')
  const [ampliado, setAmpliado] = useState(false)
  useEffect(() => { setAmpliado(false) }, [indice])
  useEffect(() => {
    const alTeclear = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar()
      else if (e.key === 'ArrowLeft' && imagenes.length > 1) onIr(-1)
      else if (e.key === 'ArrowRight' && imagenes.length > 1) onIr(1)
    }
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [onCerrar, onIr, imagenes.length])

  return (
    <div role="dialog" aria-modal="true" aria-label={`${nombreDe(img, indice)} de ${titulo}`} className="fixed inset-0 z-50 flex flex-col bg-black/90">
      <div className="flex items-center justify-between gap-3 px-4 py-3 text-white">
        <p className="min-w-0 truncate text-sm">{nombreDe(img, indice)} · {indice + 1} de {imagenes.length}</p>
        <button autoFocus onClick={onCerrar} className="rounded-control border border-white/40 px-3 py-1.5 text-sm hover:bg-white/10">Cerrar</button>
      </div>
      <div className="relative min-h-0 flex-1 overflow-auto p-3">
        {url && (
          <img src={url} alt={`${nombreDe(img, indice)} de ${titulo}`} onClick={() => setAmpliado((a) => !a)}
            className={`mx-auto rounded-sm ${img.tipo === 'PLANO' ? 'bg-white' : ''} ${ampliado ? 'max-w-none cursor-zoom-out' : 'max-h-full max-w-full cursor-zoom-in object-contain'}`}
            style={ampliado ? { width: '220%' } : undefined} />
        )}
        {imagenes.length > 1 && !ampliado && (
          <>
            <BotonFlecha lado="izquierda" onClick={() => onIr(-1)} />
            <BotonFlecha lado="derecha" onClick={() => onIr(1)} />
          </>
        )}
      </div>
    </div>
  )
}
