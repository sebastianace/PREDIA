import { useEffect, useState } from 'react'
import { apiBlob } from '../api/cliente'
import type { FotoInmueble as Foto } from '../api/tipos'

export type Tamano = 'miniatura' | 'grande'

// Una foto se descarga una vez por sesión y versión: el hash cambia cuando se sube otra, así que nunca queda una vieja.
// La versión también viaja en la dirección (`v=`): así el caché del navegador, que guarda por dirección, no puede
// devolver una foto anterior aunque haya quedado guardada de antes.
const cache = new Map<string, Promise<string>>()

export function urlDeFoto(inmuebleId: number, version: string, tam: Tamano, base?: string): Promise<string> {
  const ruta = `${base ?? `/inmuebles/${inmuebleId}/foto`}?tam=${tam}`
  const clave = `${ruta}:${version}`
  let url = cache.get(clave)
  if (!url) {
    url = apiBlob(`${ruta}&v=${encodeURIComponent(version)}`).then((b) => URL.createObjectURL(b))
    url.catch(() => cache.delete(clave))
    cache.set(clave, url)
  }
  return url
}

/** La dirección local de la foto, o null mientras carga o si el inmueble no tiene. */
export function useUrlFoto(inmuebleId: number, foto: Foto | null | undefined, tam: Tamano, base?: string): string | null {
  const [url, setUrl] = useState<string | null>(null)
  const version = foto?.version
  useEffect(() => {
    if (!version) { setUrl(null); return }
    let vigente = true
    urlDeFoto(inmuebleId, version, tam, base).then((u) => { if (vigente) setUrl(u) }).catch(() => { if (vigente) setUrl(null) })
    return () => { vigente = false }
  }, [inmuebleId, version, tam, base])
  return url
}

/** Una imagen de la galería por su ruta de API (`.../imagenes/{id}`); se vuelve a pedir solo si cambia su `version`. */
export function useUrlImagen(ruta: string | null, version: string | undefined, tam: Tamano): string | null {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!ruta || !version) { setUrl(null); return }
    let vigente = true
    urlDeFoto(0, version, tam, ruta).then((u) => { if (vigente) setUrl(u) }).catch(() => { if (vigente) setUrl(null) })
    return () => { vigente = false }
  }, [ruta, version, tam])
  return url
}

/**
 * La foto de un inmueble recortada para llenar su contenedor, sea horizontal, vertical, cuadrada o panorámica:
 * `object-fit: cover` con el punto de interés (`foco`) que eligió el asesor. Debe ir dentro de un contenedor
 * `relative isolate overflow-hidden`; si no hay foto (o aún carga) no pinta nada y se ve lo que haya debajo
 * (el terreno). Con `velo`, oscurece un poco la parte de arriba para que se lea el texto blanco encima.
 */
export function FotoDeInmueble({ inmuebleId, foto, tam, velo = false, titulo, base }: {
  inmuebleId: number
  foto: Foto | null | undefined
  tam: Tamano
  velo?: boolean
  titulo: string
  /** Otra ruta para pedir la foto (el portal del propietario usa la suya). */
  base?: string
}) {
  const url = useUrlFoto(inmuebleId, foto, tam, base)
  if (!foto || !url) return null
  return (
    <>
      <img src={url} alt={`Foto de ${titulo}`} loading="lazy" draggable={false}
        className="absolute inset-0 -z-10 size-full animate-[aparecer_400ms_ease-out_both] object-cover"
        style={{ objectPosition: `${foto.focoX}% ${foto.focoY}%` }} />
      {velo && <div className="absolute inset-0 -z-10 bg-gradient-to-b from-black/55 via-black/10 to-transparent" aria-hidden="true" />}
    </>
  )
}
