import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useSesion } from '../sesion/Sesion'
import { AvisoIa } from './AvisoIa'
import { Icono, type NombreIcono } from './Iconos'

// Las secciones se agregan a medida que cada fase queda lista.
const secciones: { ruta: string; nombre: string; icono: NombreIcono }[] = [
  { ruta: '/', nombre: 'Inicio', icono: 'inicio' },
  { ruta: '/inventario', nombre: 'Inventario', icono: 'inventario' },
  { ruta: '/expedientes', nombre: 'Expedientes', icono: 'expediente' },
]

const iniciales = (nombre = '') =>
  nombre.split(' ').filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase()

export function Plantilla() {
  const { usuario, cerrar } = useSesion()
  const [menuAbierto, setMenuAbierto] = useState(false)

  return (
    <div className="min-h-dvh md:flex">
      {/* Barra lateral en escritorio */}
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col bg-prusia text-white md:flex">
        <div className="ancha px-7 pb-8 pt-8 text-2xl font-bold tracking-tight">Predia</div>
        <nav className="flex flex-1 flex-col gap-0.5 px-4" aria-label="Secciones">
          {secciones.map((s) => (
            <NavLink key={s.ruta} to={s.ruta} end
              className={({ isActive }) =>
                `relative flex items-center gap-3 rounded-control px-3 py-2.5 text-[15px] transition-colors ${
                  isActive ? 'bg-white/10 font-medium text-white before:absolute before:inset-y-2 before:-left-4 before:w-[3px] before:rounded-r before:bg-white'
                    : 'text-white/65 hover:bg-white/5 hover:text-white'}`}>
              <Icono nombre={s.icono} />
              {s.nombre}
            </NavLink>
          ))}
        </nav>
        <div className="flex items-center gap-3 border-t border-white/10 px-6 py-5">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-white text-sm font-semibold text-prusia">
            {iniciales(usuario?.nombre)}
          </span>
          <div className="min-w-0 flex-1 text-sm">
            <div className="truncate font-medium">{usuario?.nombre}</div>
            <div className="truncate text-white/55">{usuario?.inmobiliaria}</div>
          </div>
          <button onClick={cerrar} title="Cerrar sesión" aria-label="Cerrar sesión"
            className="rounded-control p-1.5 text-white/60 hover:bg-white/10 hover:text-white">
            <Icono nombre="salir" />
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Encabezado en celular */}
        <header className="sticky top-0 z-20 flex items-center justify-between bg-prusia px-4 pb-3 pt-[max(0.75rem,env(safe-area-inset-top))] text-white md:hidden">
          <span className="ancha text-xl font-bold tracking-tight">Predia</span>
          <div className="relative">
            <button onClick={() => setMenuAbierto((v) => !v)} aria-expanded={menuAbierto} aria-label="Tu cuenta"
              className="grid size-9 place-items-center rounded-full bg-white text-sm font-semibold text-prusia">
              {iniciales(usuario?.nombre)}
            </button>
            {menuAbierto && (
              <div className="absolute right-0 top-11 w-60 rounded-panel border border-linea bg-white p-4 text-sm text-grafito shadow-[0_12px_32px_-12px_rgb(11_37_69/0.35)]">
                <div className="font-medium text-tinta">{usuario?.nombre}</div>
                <div className="text-niebla">{usuario?.inmobiliaria}</div>
                <button onClick={cerrar} className="mt-3 flex items-center gap-2 font-medium text-cobalto">
                  <Icono nombre="salir" className="size-4" /> Cerrar sesión
                </button>
              </div>
            )}
          </div>
        </header>
        <AvisoIa />
        <main className="flex-1 px-4 pb-28 pt-6 md:px-10 md:pb-12 md:pt-10">
          <Outlet />
        </main>
      </div>

      {/* Navegación inferior en celular: al alcance del pulgar durante las visitas */}
      <nav aria-label="Secciones"
        className="fixed inset-x-0 bottom-0 z-20 flex border-t border-linea bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden">
        {secciones.map((s) => (
          <NavLink key={s.ruta} to={s.ruta} end
            className={({ isActive }) =>
              `relative flex flex-1 flex-col items-center gap-1 pb-2 pt-2.5 text-xs ${
                isActive ? 'font-semibold text-tinta before:absolute before:inset-x-8 before:top-0 before:h-[3px] before:rounded-b before:bg-cobalto'
                  : 'text-niebla'}`}>
            <Icono nombre={s.icono} className="size-6" />
            {s.nombre}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
