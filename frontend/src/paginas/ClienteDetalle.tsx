import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { Cliente, PresupuestoCliente, RespuestaBusqueda, ResultadoInmueble, Simulacion, Tasa } from '../api/tipos'
import { Aviso, Campo, claseBotonPrimario, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { formatoFechaHora, formatoPesos, formatoPorcentaje, formatoTasaEa } from '../formato'

const plazos = [5, 10, 15, 20, 25, 30]

export function ClienteDetalle() {
  const { id } = useParams()
  const navegar = useNavigate()
  const [cliente, setCliente] = useState<Cliente | null>(null)
  const [tasas, setTasas] = useState<Tasa[]>([])
  const [plazo, setPlazo] = useState<number | null>(null)
  const [tasaId, setTasaId] = useState<number | ''>('')
  const [vis, setVis] = useState(false)
  const [presupuesto, setPresupuesto] = useState<PresupuestoCliente | null>(null)
  const [errorPresupuesto, setErrorPresupuesto] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [busqueda, setBusqueda] = useState<RespuestaBusqueda | null>(null)
  const [buscando, setBuscando] = useState(false)
  const [errorBusqueda, setErrorBusqueda] = useState<string | null>(null)

  useEffect(() => {
    api<Cliente>(`/clientes/${id}`).then(setCliente).catch((e) => setError(e.message))
    api<Tasa[]>('/tasas').then(setTasas).catch(() => setTasas([]))
  }, [id])

  // El presupuesto se recalcula en el backend cada vez que cambia el plazo, la tasa o el tipo de vivienda.
  useEffect(() => {
    if (!cliente) return
    const params = new URLSearchParams()
    if (plazo != null) params.set('plazoAnios', String(plazo))
    if (tasaId !== '') params.set('tasaId', String(tasaId))
    if (vis) params.set('vis', 'true')
    api<PresupuestoCliente>(`/clientes/${cliente.id}/presupuesto?${params}`)
      .then((p) => {
        setPresupuesto(p)
        setErrorPresupuesto(null)
        if (plazo == null) setPlazo(p.plazoAnios)
      })
      .catch((e) => { setPresupuesto(null); setErrorPresupuesto(e.message) })
  }, [cliente, plazo, tasaId, vis])

  const buscar = async () => {
    if (!cliente || !presupuesto) return
    setBuscando(true)
    setErrorBusqueda(null)
    try {
      const r = await api<RespuestaBusqueda>('/inmuebles/busqueda', {
        method: 'POST',
        body: JSON.stringify({
          operacion: 'VENTA',
          precioMax: presupuesto.presupuesto.topeDeCompra,
          habitacionesMin: cliente.habitacionesMin,
          descripcion: [cliente.preferencias, ...cliente.zonas].filter(Boolean).join('. '),
        }),
      })
      setBusqueda(r)
    } catch (e) {
      setErrorBusqueda(e instanceof ErrorApi ? e.message : 'No se pudo buscar.')
    } finally {
      setBuscando(false)
    }
  }

  const eliminar = async () => {
    if (!cliente || !window.confirm(`¿Eliminar a ${cliente.nombre} y todos sus datos? No se puede deshacer.`)) return
    await api(`/clientes/${cliente.id}`, { method: 'DELETE' })
    navegar('/clientes')
  }

  if (error) return <Aviso tipo="error">{error}</Aviso>
  if (!cliente) return <p className="text-niebla">Cargando…</p>

  const p = presupuesto?.presupuesto
  return (
    <div className="mx-auto max-w-3xl">
      <Link to="/clientes" className="text-sm text-niebla hover:text-tinta">← Clientes</Link>
      <div className="mt-2 flex items-start justify-between gap-4">
        <div>
          <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">{cliente.nombre}</h1>
          <p className="mt-1 text-niebla">
            {cliente.intencion === 'COMPRAR' ? 'Quiere comprar' : 'Quiere arrendar'}
            {cliente.zonas.length > 0 && ` en ${cliente.zonas.join(', ')}`}
            {cliente.habitacionesMin > 0 && ` · ${cliente.habitacionesMin}+ habitaciones`}
          </p>
        </div>
        <Link to={`/clientes/${cliente.id}/editar`} className={`${claseBotonSecundario} shrink-0`}>Editar</Link>
      </div>

      <dl className="mt-6 grid grid-cols-3 gap-4 border-y border-linea py-4 text-sm">
        <div><dt className="text-niebla">Ingreso al mes</dt><dd className="ancha cifra mt-0.5 font-semibold text-tinta">{formatoPesos(cliente.ingresoFamiliar)}</dd></div>
        <div><dt className="text-niebla">Ahorro</dt><dd className="ancha cifra mt-0.5 font-semibold text-tinta">{formatoPesos(cliente.ahorro)}</dd></div>
        <div><dt className="text-niebla">Deudas al mes</dt><dd className="ancha cifra mt-0.5 font-semibold text-tinta">{formatoPesos(cliente.deudasMensuales)}</dd></div>
      </dl>
      {cliente.preferencias && <p className="mt-4 text-grafito">{cliente.preferencias}</p>}
      <p className="mt-3 text-xs text-niebla">Autorización de datos registrada el {formatoFechaHora(cliente.autorizacionFecha)}.</p>

      <section className="mt-10" aria-labelledby="titulo-presupuesto">
        <h2 id="titulo-presupuesto" className="text-lg font-semibold">Presupuesto de compra</h2>

        <div className="mt-4 grid gap-4 sm:grid-cols-3">
          <Campo etiqueta="Plazo">
            {(idc) => (
              <select id={idc} value={plazo ?? ''} onChange={(e) => setPlazo(Number(e.target.value))} className={claseInput}>
                {plazos.map((a) => <option key={a} value={a}>{a} años</option>)}
              </select>
            )}
          </Campo>
          <Campo etiqueta="Tasa">
            {(idc) => (
              <select id={idc} value={tasaId} onChange={(e) => setTasaId(e.target.value === '' ? '' : Number(e.target.value))} className={claseInput}>
                <option value="">La vigente más reciente</option>
                {tasas.filter((t) => t.tipo === (vis ? 'VIS' : 'NO_VIS')).map((t) => (
                  <option key={t.id} value={t.id}>{t.banco} · {formatoTasaEa(t.tasaEa)}</option>
                ))}
              </select>
            )}
          </Campo>
          <label className="flex items-center gap-2 self-end pb-2.5 text-sm text-grafito">
            <input type="checkbox" checked={vis} onChange={(e) => { setVis(e.target.checked); setTasaId('') }}
              className="size-4 accent-[var(--color-cobalto)]" />
            Vivienda de interés social (VIS)
          </label>
        </div>

        {errorPresupuesto && <div className="mt-4"><Aviso tipo="error">{errorPresupuesto}</Aviso></div>}

        {presupuesto && p && (
          <div className="mt-5 rounded-panel border border-linea bg-white p-5 md:p-6">
            <div className="text-sm text-niebla">Puede comprar hasta</div>
            <div className="ancha cifra text-[34px] font-semibold leading-tight text-tinta md:text-[40px]">
              {formatoPesos(p.topeDeCompra)}
            </div>
            <p className="mt-1 text-sm text-grafito">
              {p.limitante === 'AHORRO'
                ? `Lo limita el ahorro: ${formatoPesos(cliente.ahorro)} es el ${formatoPorcentaje(presupuesto.pctCuotaInicial, 0)} de la cuota inicial.`
                : 'Lo limita la capacidad de pago: la cuota máxima no alcanza para financiar más.'}
            </p>

            <dl className="mt-5 grid gap-x-6 gap-y-3 border-t border-linea pt-4 text-sm sm:grid-cols-2">
              <div className="flex justify-between gap-3"><dt className="text-niebla">Cuota máxima al mes ({formatoPorcentaje(presupuesto.pctCuotaMaximaIngreso, 0)} del ingreso)</dt><dd className="cifra font-medium text-tinta">{formatoPesos(p.cuotaMaxima)}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-niebla">Lo que soporta esa cuota</dt><dd className="cifra font-medium text-tinta">{formatoPesos(p.montoQueSoportaLaCuota)}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-niebla">Tope por ahorro</dt><dd className="cifra font-medium text-tinta">{formatoPesos(p.topePorAhorro)}</dd></div>
              <div className="flex justify-between gap-3"><dt className="text-niebla">Tope por capacidad de pago</dt><dd className="cifra font-medium text-tinta">{formatoPesos(p.topePorCapacidadDePago)}</dd></div>
            </dl>

            <p className="mt-4 text-xs text-niebla">
              Tasa usada: {presupuesto.tasa.banco}, {formatoTasaEa(presupuesto.tasa.tasaEa)}, vigente desde {presupuesto.tasa.vigenteDesde}
              {presupuesto.tasa.esEjemplo && ' (tasa de ejemplo)'}. Fuente: {presupuesto.tasa.fuente}.
            </p>
            <ul className="mt-3 space-y-2">
              {presupuesto.avisos.map((a) => (
                <li key={a}><Aviso tipo={a.includes('deudas') ? 'error' : 'info'}>{a}</Aviso></li>
              ))}
            </ul>

            <button onClick={() => void buscar()} disabled={buscando} className={`${claseBotonPrimario} mt-5`}>
              {buscando ? 'Buscando…' : 'Buscar inmuebles dentro del presupuesto'}
            </button>
          </div>
        )}
      </section>

      {errorBusqueda && <div className="mt-6"><Aviso tipo="error">{errorBusqueda}</Aviso></div>}
      {busqueda && (
        <section className="mt-8" aria-labelledby="titulo-resultados">
          <h2 id="titulo-resultados" className="text-lg font-semibold">
            {busqueda.resultados.length === 0 ? 'No hay inmuebles disponibles dentro del presupuesto' : 'Inmuebles disponibles'}
          </h2>
          {busqueda.aviso && <div className="mt-3"><Aviso tipo="info">{busqueda.aviso}</Aviso></div>}
          <ul className="mt-4 space-y-4">
            {busqueda.resultados.map((r) => (
              <TarjetaResultado key={r.inmueble.id} resultado={r} cliente={cliente} plazo={plazo} tasaId={tasaId} />
            ))}
          </ul>
        </section>
      )}

      <div className="mt-12 border-t border-linea pt-6">
        <button onClick={() => void eliminar()} className="text-sm text-red-700 hover:underline">
          Eliminar cliente y sus datos
        </button>
      </div>
    </div>
  )
}

function TarjetaResultado({ resultado, cliente, plazo, tasaId }:
  { resultado: ResultadoInmueble; cliente: Cliente; plazo: number | null; tasaId: number | '' }) {
  const { inmueble: i, coincidenciaPct } = resultado
  const [simulacion, setSimulacion] = useState<Simulacion | null>(null)
  const [simulando, setSimulando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const simular = async () => {
    setSimulando(true)
    setError(null)
    try {
      setSimulacion(await api<Simulacion>('/creditos/simular', {
        method: 'POST',
        body: JSON.stringify({ inmuebleId: i.id, clienteId: cliente.id, plazoAnios: plazo ?? undefined,
          tasaId: tasaId === '' ? undefined : tasaId }),
      }))
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo simular el crédito.')
    } finally {
      setSimulando(false)
    }
  }

  return (
    <li className="rounded-panel border border-linea bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-semibold text-tinta">{i.titulo}</h3>
          <p className="text-sm text-niebla">{i.barrio}, {i.ciudad} · {i.habitaciones} hab · {i.areaM2} m²</p>
        </div>
        {coincidenciaPct != null && (
          <span className="shrink-0 rounded-full bg-cobalto-suave px-2.5 py-1 text-xs font-semibold text-cobalto-hondo">
            {coincidenciaPct} % de coincidencia
          </span>
        )}
      </div>
      <p className="ancha cifra mt-3 text-2xl font-semibold text-tinta">{formatoPesos(i.precioVenta)}</p>

      <button onClick={() => void simular()} disabled={simulando} className={`${claseBotonSecundario} mt-4`}>
        {simulando ? 'Calculando…' : 'Simular crédito'}
      </button>
      {error && <div className="mt-3"><Aviso tipo="error">{error}</Aviso></div>}

      {simulacion && (
        <div className="mt-4 border-t border-linea pt-4 text-sm">
          <div className="text-niebla">Cuota mensual</div>
          <div className="ancha cifra text-2xl font-semibold text-tinta">{formatoPesos(simulacion.cuotaMensual)}</div>
          <p className="mt-1 text-grafito">
            Financiando {formatoPesos(simulacion.montoFinanciado)} a {simulacion.plazoAnios} años
            ({simulacion.numeroCuotas} cuotas).
            {simulacion.porcentajeSobreIngreso != null && <> Es el <strong className="text-tinta">{formatoPorcentaje(simulacion.porcentajeSobreIngreso)}</strong> de su ingreso.</>}
          </p>
          <ul className="mt-3 space-y-1.5">
            {simulacion.cumpleCuotaMaxima != null && (
              <li className={simulacion.cumpleCuotaMaxima ? 'text-emerald-700' : 'text-red-700'}>
                {simulacion.cumpleCuotaMaxima ? '✓ Cumple' : '✗ No cumple'} la cuota máxima de {formatoPesos(simulacion.cuotaMaxima)}.
              </li>
            )}
            {simulacion.cumpleCuotaInicial != null && (
              <li className={simulacion.cumpleCuotaInicial ? 'text-emerald-700' : 'text-red-700'}>
                {simulacion.cumpleCuotaInicial ? '✓ Cumple' : '✗ No cumple'} la cuota inicial mínima de {formatoPesos(simulacion.cuotaInicialMinima)}
                {' '}(su ahorro: {formatoPesos(simulacion.cuotaInicial)}).
              </li>
            )}
          </ul>
          <p className="mt-3 text-xs text-niebla">
            Tasa: {simulacion.tasa.banco}, {formatoTasaEa(simulacion.tasa.tasaEa)}, vigente desde {simulacion.tasa.vigenteDesde}
            {simulacion.tasa.esEjemplo && ' (de ejemplo)'}. {simulacion.aviso}
          </p>
        </div>
      )}
    </li>
  )
}
