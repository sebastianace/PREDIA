import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { Cliente, Intencion } from '../api/tipos'
import { Avatar } from '../componentes/Avatar'
import { claseBotonPrimario, claseInput } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { PanelAzul } from '../componentes/PanelAzul'
import { formatoFecha, formatoPesos } from '../formato'

const etiquetaBlanca = 'inline-flex items-center gap-1.5 rounded-full bg-white/15 px-2.5 py-0.5 text-xs text-white'
type Filtro = 'TODOS' | Intencion

function Cifra({ nombre, valor, acento = false }: { nombre: string; valor: string; acento?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="rotulo whitespace-nowrap text-sm text-niebla">{nombre}</dt>
      <dd className={`ancha cifra whitespace-nowrap text-lg font-semibold ${acento ? 'text-prusia' : 'text-tinta'}`}>{valor}</dd>
    </div>
  )
}

function TarjetaCliente({ c }: { c: Cliente }) {
  const compra = c.intencion === 'COMPRAR'
  return (
    <li className="overflow-hidden rounded-panel border border-linea bg-white">
      <Link to={`/clientes/${c.id}`} className="block transition-colors hover:bg-papel/40">
        <PanelAzul semilla={c.id * 37 + 11} ancho={1000} alto={160} niveles={9} className="px-4 py-4 md:px-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3.5">
              <Avatar nombre={c.nombre} tono="blanco" tamano="enorme" foto={{ ruta: `/clientes/${c.id}/foto`, version: c.fotoVersion }} />
              <div className="min-w-0">
                <div className="ancha truncate text-xl font-semibold">{c.nombre}</div>
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  <span className="inline-flex items-center rounded-full bg-cobalto-suave px-2.5 py-0.5 text-xs font-medium text-cobalto-hondo">
                    {compra ? 'Quiere comprar' : 'Quiere arrendar'}
                  </span>
                  {c.zonas.length > 0 && <span className={etiquetaBlanca}>{c.zonas.join(', ')}</span>}
                  {c.habitacionesMin > 0 && (
                    <span className={etiquetaBlanca}><Icono nombre="cama" className="size-3.5" />{c.habitacionesMin}+ habitaciones</span>
                  )}
                </div>
              </div>
            </div>
            {c.autorizacionDatos && (
              <span className={`${etiquetaBlanca} bg-white/10`}>
                <Icono nombre="escudo" className="size-3.5" />Datos autorizados el {formatoFecha(c.autorizacionFecha)}
              </span>
            )}
          </div>
        </PanelAzul>
        <div className="px-4 pb-4 pt-4 md:px-5">
          <dl className={`grid grid-cols-1 gap-4 sm:gap-5 ${compra ? 'sm:grid-cols-3' : 'sm:grid-cols-2'}`}>
            <Cifra nombre="Ingreso al mes" valor={formatoPesos(c.ingresoFamiliar)} />
            <Cifra nombre="Ahorro" valor={formatoPesos(c.ahorro)} />
            {compra && (
              <div className="min-w-0 border-prusia sm:border-l-2 sm:pl-4">
                {c.topeDeCompra != null ? (
                  <>
                    <Cifra nombre="Puede comprar hasta" valor={formatoPesos(c.topeDeCompra)} acento />
                    <p className="mt-0.5 text-xs text-niebla">
                      {c.limitadoPor === 'AHORRO' ? 'Lo limita el ahorro (cuota inicial)' : 'Lo limita su capacidad de pago'}
                    </p>
                  </>
                ) : (
                  <Cifra nombre="Puede comprar hasta" valor="—" />
                )}
              </div>
            )}
          </dl>
        </div>
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t border-linea px-4 py-2.5 text-sm md:px-5">
        <span className="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-1 text-grafito">
          {c.email && <span className="inline-flex min-w-0 items-center gap-1.5"><Icono nombre="recordatorios" className="size-4 shrink-0 text-niebla" /><span className="truncate">{c.email}</span></span>}
          {c.telefono && <span className="inline-flex items-center gap-1.5"><Icono nombre="telefono" className="size-4 shrink-0 text-niebla" />{c.telefono}</span>}
          {!c.email && !c.telefono && <span className="text-niebla">Sin datos de contacto</span>}
        </span>
        <span className="flex items-center gap-4 font-medium text-cobalto">
          <Link to={`/clientes/${c.id}`} className="hover:text-cobalto-hondo">{compra ? 'Ver presupuesto' : 'Ver ficha'}</Link>
          <Link to={`/reduccion?cliente=${c.id}`} className="hover:text-cobalto-hondo">Reducir su crédito</Link>
        </span>
      </div>
    </li>
  )
}

export function Clientes() {
  const [clientes, setClientes] = useState<Cliente[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busqueda, setBusqueda] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('TODOS')

  useEffect(() => {
    api<Cliente[]>('/clientes').then(setClientes).catch((e) => setError(e.message))
  }, [])

  const conteo = useMemo(() => ({
    TODOS: clientes?.length ?? 0,
    COMPRAR: clientes?.filter((c) => c.intencion === 'COMPRAR').length ?? 0,
    ARRENDAR: clientes?.filter((c) => c.intencion === 'ARRENDAR').length ?? 0,
  }), [clientes])

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase()
    return (clientes ?? []).filter((c) =>
      (filtro === 'TODOS' || c.intencion === filtro)
      && (q === '' || [c.nombre, c.email ?? '', c.zonas.join(' ')].some((t) => t.toLowerCase().includes(q))))
  }, [clientes, busqueda, filtro])

  const filtros: { valor: Filtro; texto: string }[] = [
    { valor: 'TODOS', texto: 'Todos' }, { valor: 'COMPRAR', texto: 'Comprar' }, { valor: 'ARRENDAR', texto: 'Arrendar' },
  ]

  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="ancha text-[32px] font-semibold leading-[1.05] md:text-5xl">Clientes</h1>
          <p className="mt-1 text-niebla">Compradores con su presupuesto y su autorización de datos.</p>
        </div>
        <Link to="/clientes/nuevo" className={`${claseBotonPrimario} shrink-0`}>Nuevo cliente</Link>
      </div>

      {error && <p role="alert" className="mt-6 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
      {!clientes && !error && <p className="mt-6 text-niebla">Cargando…</p>}

      {clientes && clientes.length > 0 && (
        <div className="mt-5 flex flex-wrap items-center gap-2">
          <div className="relative min-w-[14rem] flex-1">
            <Icono nombre="buscar" className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-niebla" />
            <input type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} aria-label="Buscar clientes"
              placeholder="Buscar por nombre, zona o correo" className={`${claseInput} pl-9`} />
          </div>
          <div role="group" aria-label="Filtrar por intención" className="flex gap-2">
            {filtros.map((f) => (
              <button key={f.valor} type="button" aria-pressed={filtro === f.valor} onClick={() => setFiltro(f.valor)}
                className={`rounded-full border px-3.5 py-2 text-sm transition-colors ${
                  filtro === f.valor ? 'border-tinta bg-tinta text-white' : 'border-linea bg-white text-tinta hover:border-niebla'}`}>
                {f.texto} <span className="opacity-60">{conteo[f.valor]}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <ul className="mt-5 space-y-4">
        {visibles.map((c) => <TarjetaCliente key={c.id} c={c} />)}
        {clientes && clientes.length > 0 && visibles.length === 0 && (
          <li className="rounded-panel border border-dashed border-linea bg-white px-5 py-8 text-sm text-niebla">
            Ningún cliente coincide con esa búsqueda.
          </li>
        )}
        {clientes && (
          <li>
            <Link to="/clientes/nuevo"
              className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 rounded-panel border-[1.5px] border-dashed border-trazo px-5 py-4 text-prusia-claro transition-colors hover:border-cobalto hover:bg-cobalto-suave/40">
              <Icono nombre="usuarioMas" className="size-5" />
              <span className="font-semibold">Nuevo cliente</span>
              <span className="text-sm text-niebla">
                {clientes.length === 0 ? 'Todavía no hay clientes. Créalo con lo que te dijo en la visita.' : 'Créalo con lo que te dijo en la visita.'}
              </span>
            </Link>
          </li>
        )}
      </ul>
    </div>
  )
}
