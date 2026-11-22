import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useSesion } from '../sesion/Sesion'
import { AvisoIa } from './AvisoIa'
import { Icono, type NombreIcono } from './Iconos'

// En escritorio van todas en la barra lateral; en celular, las principales abajo y el resto en "Más".
const secciones: { ruta: string; nombre: string; icono: NombreIcono; principal: boolean }[] = [
  { ruta: '/', nombre: 'Inicio', icono: 'inicio', principal: true },
  { ruta: '/negocios', nombre: 'Negocios', icono: 'negocios', principal: true },
  { ruta: '/inventario', nombre: 'Inventario', icono: 'inventario', principal: true },
  { ruta: '/asistente', nombre: 'Asistente', icono: 'asistente', principal: true },
  { ruta: '/clientes', nombre: 'Clientes', icono: 'clientes', principal: false },
  { ruta: '/reduccion', nombre: 'Reducción', icono: 'reduccion', principal: false },
  { ruta: '/recordatorios', nombre: 'Recordatorios', icono: 'recordatorios', principal: false },
  { ruta: '/expedientes', nombre: 'Expedientes', icono: 'expediente', principal: false },
  { ruta: '/tasas', nombre: 'Tasas', icono: 'tasas', principal: false },
  { ruta: '/requisitos', nombre: 'Requisitos', icono: 'requisitos', principal: false },
]

const iniciales = (nombre = '') =>
  nombre.split(' ').filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase()

export function Plantilla() {
  const { usuario, cerrar } = useSesion()
  const [menuAbierto, setMenuAbierto] = useState(false)
  const [masAbierto, setMasAbierto] = useState(false)
  const { pathname } = useLocation()
  const secundariaActiva = secciones.some((x) => !x.principal && pathname.startsWith(x.ruta))
  useEffect(() => { setMasAbierto(false) }, [pathname])

  return (
    <div className="min-h-dvh md:flex">
      {/* Barra lateral en escritorio */}
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col bg-prusia text-white md:flex">
        <div className="ancha px-7 pb-8 pt-8 text-2xl font-bold tracking-tight">Predia</div>
        <nav className="flex flex-1 flex-col gap-0.5 px-4" aria-label="Secciones">
          {secciones.map((s) => (
            <NavLink key={s.ruta} to={s.ruta} end={s.ruta === '/'}
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
      {masAbierto && (
        <div className="fixed inset-x-0 bottom-[calc(3.9rem+env(safe-area-inset-bottom))] z-20 border-t border-linea bg-white p-2 shadow-[0_-12px_32px_-16px_rgb(11_37_69/0.35)] md:hidden">
          <ul className="grid grid-cols-2 gap-1">
            {secciones.filter((x) => !x.principal).map((x) => (
              <li key={x.ruta}>
                <NavLink to={x.ruta} className={({ isActive }) =>
                  `flex items-center gap-3 rounded-control px-3 py-3 text-sm ${isActive ? 'bg-papel font-semibold text-tinta' : 'text-grafito'}`}>
                  <Icono nombre={x.icono} className="size-5 text-niebla" />
                  {x.nombre}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      )}
      <nav aria-label="Secciones"
        className="fixed inset-x-0 bottom-0 z-30 flex border-t border-linea bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden">
        {secciones.filter((x) => x.principal).map((x) => (
          <NavLink key={x.ruta} to={x.ruta} end={x.ruta === '/'}
            className={({ isActive }) =>
              `relative flex flex-1 flex-col items-center gap-1 pb-2 pt-2.5 text-xs ${
                isActive ? 'font-semibold text-tinta before:absolute before:inset-x-8 before:top-0 before:h-[3px] before:rounded-b before:bg-cobalto'
                  : 'text-niebla'}`}>
            <Icono nombre={x.icono} className="size-6" />
            {x.nombre}
          </NavLink>
        ))}
        <button onClick={() => setMasAbierto((v) => !v)} aria-expanded={masAbierto}
          className={`relative flex flex-1 flex-col items-center gap-1 pb-2 pt-2.5 text-xs ${masAbierto || secundariaActiva ? 'font-semibold text-tinta' : 'text-niebla'}`}>
          <Icono nombre="mas" className="size-6" />
          Más
        </button>
      </nav>
    </div>
  )
}
