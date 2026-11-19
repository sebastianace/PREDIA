import { useEffect, useState } from 'react'
import { api } from '../api/cliente'
import type { EstadoInmueble, Inmueble } from '../api/tipos'
import { formatoPesos } from '../formato'

const colorEstado: Record<EstadoInmueble, string> = {
  DISPONIBLE: 'bg-emerald-50 text-emerald-700',
  SEPARADO: 'bg-amber-50 text-amber-700',
  ARRENDADO: 'bg-sky-50 text-sky-700',
  VENDIDO: 'bg-stone-100 text-stone-600',
}

export function Inventario() {
  const [inmuebles, setInmuebles] = useState<Inmueble[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Inmueble[]>('/inmuebles').then(setInmuebles).catch((e) => setError(e.message))
  }, [])

  return (
    <section className="mx-auto max-w-4xl">
      <h1 className="mb-4 text-2xl font-semibold">Inventario</h1>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {!inmuebles && !error && <p className="text-stone-500">Cargando…</p>}

      <ul className="grid gap-3 sm:grid-cols-2">
        {inmuebles?.map((i) => (
          <li key={i.id} className="rounded-xl border border-stone-200 bg-white p-4">
            <div className="flex items-start justify-between gap-2">
              <div>
                <h2 className="font-semibold">{i.titulo}</h2>
                <p className="text-sm text-stone-500">{i.barrio}, {i.ciudad}</p>
              </div>
              <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${colorEstado[i.estado]}`}>
                {i.estado.toLowerCase()}
              </span>
            </div>
            <p className="mt-3 text-lg font-semibold text-marca-700">
              {formatoPesos(i.operacion === 'VENTA' ? i.precioVenta : i.canonArriendo)}
              {i.operacion === 'ARRIENDO' && <span className="text-sm font-normal text-stone-500"> / mes</span>}
            </p>
            <p className="mt-1 text-sm text-stone-600">
              {i.habitaciones} hab · {i.banos} baños · {i.areaM2} m² · {i.parqueaderos > 0 ? `${i.parqueaderos} parq.` : 'sin parqueadero'}
            </p>
          </li>
        ))}
      </ul>
    </section>
  )
}
