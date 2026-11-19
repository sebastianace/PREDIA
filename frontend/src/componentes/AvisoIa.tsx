import { useSesion } from '../sesion/Sesion'

/** Aviso visible cuando no hay IA: Predia sigue funcionando con formularios y búsqueda por texto. */
export function AvisoIa() {
  const { sistema } = useSesion()
  if (!sistema || sistema.iaDisponible) return null
  return (
    <div role="status" className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-900">
      <strong className="font-semibold">Modo manual.</strong> {sistema.aviso}
    </div>
  )
}
