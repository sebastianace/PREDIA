import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Cliente } from '../api/tipos'
import { claseBotonPrimario } from '../componentes/Formulario'
import { formatoPesos } from '../formato'

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

      <ul className="mt-6 divide-y divide-linea border-y border-linea">
        {clientes?.map((c) => (
          <li key={c.id}>
            <Link to={`/clientes/${c.id}`} className="flex items-center justify-between gap-4 py-4 hover:bg-white">
              <div className="min-w-0">
                <div className="truncate font-medium text-tinta">{c.nombre}</div>
                <div className="truncate text-sm text-niebla">
                  {c.intencion === 'COMPRAR' ? 'Quiere comprar' : 'Quiere arrendar'}
                  {c.zonas.length > 0 && ` · ${c.zonas.join(', ')}`}
                  {c.habitacionesMin > 0 && ` · ${c.habitacionesMin}+ habitaciones`}
                </div>
              </div>
              <div className="shrink-0 text-right text-sm">
                <div className="ancha cifra font-semibold text-tinta">{formatoPesos(c.ingresoFamiliar)}</div>
                <div className="text-niebla">ingreso al mes</div>
              </div>
            </Link>
          </li>
        ))}
        {clientes && clientes.length === 0 && (
          <li className="py-6 text-sm text-niebla">
            Todavía no hay clientes. Crea el primero con lo que te dijo en la visita.
          </li>
        )}
      </ul>
    </div>
  )
}
