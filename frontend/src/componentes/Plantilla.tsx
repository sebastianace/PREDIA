import { NavLink, Outlet } from 'react-router-dom'
import { useSesion } from '../sesion/Sesion'
import { AvisoIa } from './AvisoIa'

// Las secciones se agregan a medida que cada fase queda lista.
const secciones = [
  { ruta: '/', nombre: 'Inicio', icono: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z' },
  { ruta: '/inventario', nombre: 'Inventario', icono: 'M4 21V8l8-5 8 5v13M9 21v-6h6v6M4 21h16' },
]

function Icono({ d }: { d: string }) {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth={1.8}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={d} />
    </svg>
  )
}

export function Plantilla() {
  const { usuario, cerrar } = useSesion()

  return (
    <div className="min-h-dvh md:flex">
      {/* Barra lateral en escritorio */}
      <aside className="hidden w-60 shrink-0 flex-col bg-marca-900 text-marca-50 md:flex">
        <div className="px-6 py-6 text-xl font-semibold tracking-tight">Predia</div>
        <nav className="flex flex-1 flex-col gap-1 px-3">
          {secciones.map((s) => (
            <NavLink key={s.ruta} to={s.ruta} end
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-marca-700 text-white' : 'text-marca-100 hover:bg-marca-700/60'}`}>
              <Icono d={s.icono} />
              {s.nombre}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-marca-700 px-6 py-4 text-sm">
          <div className="font-medium">{usuario?.nombre}</div>
          <div className="text-marca-100/80">{usuario?.inmobiliaria}</div>
          <button onClick={cerrar} className="mt-2 text-marca-100 underline-offset-2 hover:underline">Cerrar sesión</button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Encabezado en celular */}
        <header className="flex items-center justify-between bg-marca-900 px-4 py-3 text-white md:hidden">
          <span className="text-lg font-semibold">Predia</span>
          <button onClick={cerrar} className="text-sm text-marca-100">Salir</button>
        </header>
        <AvisoIa />
        <main className="flex-1 px-4 pb-24 pt-5 md:px-8 md:pb-8">
          <Outlet />
        </main>
      </div>

      {/* Navegación inferior en celular: el asesor la usa con el pulgar durante las visitas */}
      <nav className="fixed inset-x-0 bottom-0 flex border-t border-stone-200 bg-white pb-[env(safe-area-inset-bottom)] md:hidden">
        {secciones.map((s) => (
          <NavLink key={s.ruta} to={s.ruta} end
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-xs ${isActive ? 'text-marca-700' : 'text-stone-500'}`}>
            <Icono d={s.icono} />
            {s.nombre}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
