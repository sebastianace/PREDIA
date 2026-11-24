import { Link } from 'react-router-dom'
import { Icono } from './Iconos'
import { useSesion } from '../sesion/Sesion'

/** La barra superior del portal del comprador: la marca, su inmobiliaria y la salida. */
export function EncabezadoComprador() {
  const { usuario, cerrar } = useSesion()
  return (
    <header className="sticky top-0 z-20 flex items-center justify-between gap-3 bg-prusia px-4 pb-3 pt-[max(0.75rem,env(safe-area-inset-top))] text-white md:px-8">
      <Link to="/comprador" className="flex min-w-0 items-baseline">
        <span className="ancha shrink-0 text-xl font-bold tracking-tight">Predia</span>
        <span className="ml-2 truncate text-sm text-trazo sm:ml-3">{usuario?.inmobiliaria}</span>
      </Link>
      <div className="flex items-center gap-3 text-sm">
        <span className="hidden truncate text-white/80 sm:inline">{usuario?.nombre}</span>
        <button onClick={cerrar} className="flex items-center gap-1.5 rounded-control px-2 py-1.5 text-white/80 hover:bg-white/10 hover:text-white">
          <Icono nombre="salir" className="size-4" /> Salir
        </button>
      </div>
    </header>
  )
}
