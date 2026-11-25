import { useUrlImagen } from './FotoInmueble'

/** Las iniciales de un nombre: «Jorge Ramírez Pardo» → «JR», «Paola y Juan» → «PJ». */
export function iniciales(nombre: string): string {
  const palabras = nombre.split(/\s+/).filter((p) => p.length > 1 || /\p{L}/u.test(p))
  const significativas = palabras.filter((p) => !['y', 'e', 'de', 'del', 'la'].includes(p.toLowerCase()))
  return (significativas.length > 0 ? significativas : palabras).slice(0, 2).map((p) => p[0].toUpperCase()).join('')
}

/** De dónde se pide la foto de perfil y su versión (el hash): con ella no se queda una foto vieja guardada. */
export interface FotoPerfil {
  ruta: string
  version?: string | null
}

/**
 * Un círculo con la foto de la persona o, si no tiene, sus iniciales: azul de Prusia lleno para el cliente, suave para
 * el propietario y blanco para usarlo sobre un panel azul.
 */
export function Avatar({ nombre, tono = 'prusia', tamano = 'normal', foto }: {
  nombre: string
  tono?: 'prusia' | 'suave' | 'blanco'
  tamano?: 'normal' | 'grande' | 'enorme' | 'gigante'
  foto?: FotoPerfil
}) {
  const color = tono === 'prusia' ? 'bg-prusia text-white' : tono === 'blanco' ? 'bg-white text-prusia' : 'bg-cobalto-suave text-cobalto-hondo'
  const medida = { normal: 'size-8 text-xs', grande: 'size-11 text-base', enorme: 'size-12 text-base', gigante: 'size-[4.75rem] text-2xl' }[tamano]
  // Hasta 48 px alcanza la miniatura (128 px); la foto grande es solo para el perfil.
  const url = useUrlImagen(foto?.version ? foto.ruta : null, foto?.version ?? undefined, tamano === 'gigante' ? 'grande' : 'miniatura')
  return (
    <span className={`flex shrink-0 items-center justify-center overflow-hidden rounded-full font-semibold ${color} ${medida}`} aria-hidden="true">
      {url ? <img src={url} alt="" draggable={false} className="size-full object-cover" /> : iniciales(nombre)}
    </span>
  )
}
