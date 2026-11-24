import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Cliente } from '../api/tipos'
import { Avatar } from '../componentes/Avatar'
import { claseBotonPrimario } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { formatoPesos } from '../formato'

const etiqueta = 'inline-flex items-center gap-1.5 rounded-full bg-papel px-2.5 py-0.5 text-xs text-grafito'

export function Clientes() {
  const [clientes, setClientes] = useState<Cliente[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Cliente[]>('/clientes').then(setClientes).catch((e) => setError(e.message))
  }, [])

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="ancha text-[32px] font-semibold leading-[1.05] md:text-5xl">Clientes</h1>
          <p className="mt-1 text-niebla">Compradores con su presupuesto y su autorización de datos.</p>
        </div>
        <Link to="/clientes/nuevo" className={`${claseBotonPrimario} shrink-0`}>Nuevo cliente</Link>
      </div>

      {error && <p role="alert" className="mt-6 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
      {!clientes && !error && <p className="mt-6 text-niebla">Cargando…</p>}

      <ul className="mt-6 space-y-3">
        {clientes?.map((c) => (
          <li key={c.id}>
            <Link to={`/clientes/${c.id}`}
              className="grid grid-cols-1 gap-4 rounded-panel border border-l-[3px] border-linea border-l-cobalto bg-white p-4 transition-colors hover:border-niebla hover:border-l-cobalto sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center md:p-5">
              <div className="flex min-w-0 items-center gap-3.5">
                <Avatar nombre={c.nombre} tamano="grande" />
                <div className="min-w-0">
                  <div className="ancha truncate text-lg font-semibold text-tinta">{c.nombre}</div>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    <span className="inline-flex items-center rounded-full bg-cobalto-suave px-2.5 py-0.5 text-xs font-medium text-cobalto-hondo">
                      {c.intencion === 'COMPRAR' ? 'Quiere comprar' : 'Quiere arrendar'}
                    </span>
                    {c.zonas.length > 0 && <span className={etiqueta}>{c.zonas.join(', ')}</span>}
                    {c.habitacionesMin > 0 && (
                      <span className={etiqueta}><Icono nombre="cama" className="size-3.5 text-niebla" />{c.habitacionesMin}+ habitaciones</span>
                    )}
                  </div>
                </div>
              </div>
              <dl className="grid grid-cols-2 gap-x-6 border-t border-linea pt-3 sm:block sm:space-y-2 sm:border-0 sm:pt-0 sm:text-right">
                <div>
                  <dt className="rotulo text-sm text-niebla">Ingreso al mes</dt>
                  <dd className="ancha cifra text-lg font-semibold text-tinta">{formatoPesos(c.ingresoFamiliar)}</dd>
                </div>
                <div>
                  <dt className="rotulo text-sm text-niebla">Ahorro</dt>
                  <dd className="ancha cifra text-base font-semibold text-prusia">{formatoPesos(c.ahorro)}</dd>
                </div>
              </dl>
            </Link>
          </li>
        ))}
        {clientes && clientes.length === 0 && (
          <li className="rounded-panel border border-dashed border-linea bg-white px-5 py-8 text-sm text-niebla">
            Todavía no hay clientes. Crea el primero con lo que te dijo en la visita.
          </li>
        )}
      </ul>
    </div>
  )
}
