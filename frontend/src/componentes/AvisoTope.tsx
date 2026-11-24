import { formatoPesos } from '../formato'

/**
 * Avisa que un inmueble cuesta más que el tope de compra del cliente. Solo informa: el valor de la diferencia lo
 * calcula el servidor y la decisión de mostrar el inmueble fue del asesor.
 */
export function AvisoTope({ excedente, conOrientacion = false }: { excedente: number; conOrientacion?: boolean }) {
  return (
    <p role="note" className="mt-3 border-l-2 border-ambar bg-ambar-suave px-3 py-2 text-sm text-tinta">
      Supera en <strong className="font-semibold">{formatoPesos(excedente)}</strong> lo que hoy puede comprar.
      {conOrientacion && ' Hable con su asesor para ver opciones.'}
    </p>
  )
}
