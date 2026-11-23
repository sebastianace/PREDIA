import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { EstadoInmueble as Estado, Inmueble } from '../api/tipos'
import { estilosEstado } from '../componentes/EstadoInmueble'
import { Terreno } from '../componentes/Terreno'
import { claseBotonPrimario } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { formatoPesos } from '../formato'

type Filtro = 'TODOS' | Estado
const filtros: Filtro[] = ['TODOS', 'DISPONIBLE', 'SEPARADO', 'ARRENDADO', 'VENDIDO']
const fondoEstado: Record<Estado, string> = {
  DISPONIBLE: 'bg-prusia', SEPARADO: 'bg-prusia-claro', ARRENDADO: 'bg-prusia-claro', VENDIDO: 'bg-grafito',
}
const nombreFiltro = (f: Filtro) => (f === 'TODOS' ? 'Todos' : `${estilosEstado[f].nombre}s`)

export function Inventario() {
  const [inmuebles, setInmuebles] = useState<Inmueble[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<Filtro>('TODOS')

  useEffect(() => {
    api<Inmueble[]>('/inmuebles').then(setInmuebles).catch((e) => setError(e.message))
  }, [])

  const visibles = (inmuebles ?? []).filter((i) => filtro === 'TODOS' || i.estado === filtro)
  const cuantos = (f: Filtro) => (inmuebles ?? []).filter((i) => f === 'TODOS' || i.estado === f).length

  return (
    <div className="mx-auto max-w-5xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">Inventario</h1>
          <p className="mt-1 text-niebla">
            {inmuebles ? `${inmuebles.length} inmuebles de tu inmobiliaria` : 'Cargando inmuebles…'}
          </p>
        </div>
        <Link to="/inventario/nuevo" className={`${claseBotonPrimario} shrink-0`}>Nuevo inmueble</Link>
      </div>

      <div role="tablist" aria-label="Filtrar por estado"
        className="-mx-4 mt-6 flex gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] md:mx-0 md:px-0">
        {filtros.map((f) => {
          const activo = f === filtro
          return (
            <button key={f} role="tab" aria-selected={activo} onClick={() => setFiltro(f)}
              className={`flex shrink-0 items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm transition-colors ${
                activo ? 'border-tinta bg-tinta font-medium text-white' : 'border-linea bg-white text-grafito hover:border-niebla'}`}>
              {nombreFiltro(f)}
              <span className={`cifra text-xs ${activo ? 'text-white/70' : 'text-niebla'}`}>{cuantos(f)}</span>
            </button>
          )
        })}
      </div>

      {error && <p role="alert" className="mt-6 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}

      <ul className="mt-6 grid gap-4 lg:grid-cols-2">
        {visibles.map((i) => {
          const fueraDeOferta = i.estado === 'VENDIDO' || i.estado === 'ARRENDADO'
          return (
            <li key={i.id} className="flex flex-col overflow-hidden rounded-panel border border-linea bg-white">
              <div className={`relative isolate flex h-28 items-start justify-between gap-3 p-4 text-white ${fondoEstado[i.estado]}`}>
                <Terreno semilla={i.id * 31 + 7} niveles={10} className="absolute inset-0 -z-10 size-full text-trazo opacity-70" />
                <span className="inline-flex items-center gap-2 text-sm font-medium">
                  <span className="size-2 rounded-full bg-white" aria-hidden="true" />
                  {estilosEstado[i.estado].nombre}
                </span>
                <span className="flex items-center gap-3 text-xs text-trazo">
                  {i.codigo}
                  <Link to={`/inventario/${i.id}/editar`} className="font-medium text-white hover:underline">Editar</Link>
                </span>
              </div>
              <div className="flex grow flex-col p-5">
              <h2 className="ancha text-lg font-semibold leading-snug">{i.titulo}</h2>
              <p className="text-sm text-niebla">{i.barrio}, {i.ciudad}{i.estrato ? `, estrato ${i.estrato}` : ''}</p>

              <p className={`ancha cifra mt-4 text-[26px] font-semibold leading-none ${fueraDeOferta ? 'text-niebla' : 'text-tinta'}`}>
                {formatoPesos(i.operacion === 'VENTA' ? i.precioVenta : i.canonArriendo)}
                {i.operacion === 'ARRIENDO' && <span className="ml-1 text-sm font-normal text-niebla">al mes</span>}
              </p>
              {i.valorAdministracion > 0 && (
                <p className="mt-1.5 text-xs text-niebla">Administración {formatoPesos(i.valorAdministracion)}</p>
              )}

              <div className="min-h-5 grow" aria-hidden="true" />
              <ul className="flex flex-wrap gap-x-5 gap-y-2 border-t border-linea pt-4 text-sm text-grafito">
                <li className="flex items-center gap-1.5"><Icono nombre="cama" className="size-4 text-niebla" />{i.habitaciones} habitaciones</li>
                <li className="flex items-center gap-1.5"><Icono nombre="bano" className="size-4 text-niebla" />{i.banos} baños</li>
                <li className="flex items-center gap-1.5"><Icono nombre="area" className="size-4 text-niebla" />{i.areaM2} m²</li>
                <li className="flex items-center gap-1.5"><Icono nombre="carro" className="size-4 text-niebla" />
                  {i.parqueaderos === 0 ? 'Sin parqueadero' : i.parqueaderos === 1 ? '1 parqueadero' : `${i.parqueaderos} parqueaderos`}
                </li>
              </ul>
              </div>
            </li>
          )
        })}
      </ul>

      {inmuebles && visibles.length === 0 && (
        <p className="mt-6 text-niebla">No hay inmuebles {nombreFiltro(filtro).toLowerCase()} en este momento.</p>
      )}
    </div>
  )
}
