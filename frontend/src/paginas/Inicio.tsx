import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Inmueble } from '../api/tipos'
import { useSesion } from '../sesion/Sesion'

export function Inicio() {
  const { usuario } = useSesion()
  const [inmuebles, setInmuebles] = useState<Inmueble[]>([])

  useEffect(() => {
    api<Inmueble[]>('/inmuebles').then(setInmuebles).catch(() => setInmuebles([]))
  }, [])

  const disponibles = inmuebles.filter((i) => i.estado === 'DISPONIBLE').length

  return (
    <section className="mx-auto max-w-4xl">
      <h1 className="text-2xl font-semibold">Hola, {usuario?.nombre.split(' ')[0]}</h1>
      <p className="text-stone-500">{usuario?.inmobiliaria}</p>

      <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Link to="/inventario" className="rounded-xl border border-stone-200 bg-white p-4 hover:border-marca-500">
          <div className="text-3xl font-semibold text-marca-700">{disponibles}</div>
          <div className="text-sm text-stone-500">inmuebles disponibles</div>
        </Link>
        <div className="rounded-xl border border-stone-200 bg-white p-4">
          <div className="text-3xl font-semibold text-stone-700">{inmuebles.length}</div>
          <div className="text-sm text-stone-500">inmuebles en inventario</div>
        </div>
      </div>

      <p className="mt-8 text-sm text-stone-500">
        El resumen diario (negocios bloqueados y documentos por vencer) aparecerá aquí con el módulo de cierre.
      </p>
    </section>
  )
}
