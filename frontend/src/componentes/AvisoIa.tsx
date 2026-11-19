import { useSesion } from '../sesion/Sesion'
import { Icono } from './Iconos'

/** Aviso visible cuando no hay IA: Predia sigue funcionando con formularios y búsqueda por texto. */
export function AvisoIa() {
  const { sistema } = useSesion()
  if (!sistema || sistema.iaDisponible) return null
  return (
    <div role="status" className="flex items-start gap-2.5 border-b border-ambar/20 bg-ambar-suave px-4 py-2.5 text-sm text-grafito md:px-10">
      <Icono nombre="alerta" className="mt-0.5 size-4 shrink-0 text-ambar" />
      <p>
        <span className="font-semibold text-tinta">Modo manual.</span> La IA no está conectada; puedes seguir
        trabajando con formularios y búsqueda por texto.
      </p>
    </div>
  )
}
