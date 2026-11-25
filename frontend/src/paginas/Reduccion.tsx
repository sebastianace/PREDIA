import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { Cliente, EscenarioReduccion, FrecuenciaAbono, ModoReduccion, SimulacionReduccion } from '../api/tipos'
import { Aviso, Campo, CampoPesos, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { CifraAcotada } from '../componentes/Cota'
import { Terreno } from '../componentes/Terreno'
import { formatoMeses, formatoPesos, formatoPorcentaje, formatoTasaEa } from '../formato'
import { useSesion } from '../sesion/Sesion'

const FILAS_POR_PAGINA = 24

const modos: { valor: ModoReduccion; texto: string; ayuda: string }[] = [
  { valor: 'REDUCIR_PLAZO', texto: 'Reducir el plazo', ayuda: 'La cuota se mantiene y se termina de pagar antes.' },
  { valor: 'REDUCIR_CUOTA', texto: 'Reducir la cuota', ayuda: 'El plazo se mantiene y la cuota baja.' },
]
const frecuencias: { valor: FrecuenciaAbono; texto: string }[] = [
  { valor: 'MENSUAL', texto: 'Cada mes' },
  { valor: 'ANUAL', texto: 'Una vez al año' },
  { valor: 'UNICO', texto: 'Un solo pago' },
]

function Opciones<T extends string>({ opciones, valor, onChange, etiqueta }: {
  opciones: { valor: T; texto: string }[]; valor: T; onChange: (v: T) => void; etiqueta: string
}) {
  return (
    <div role="group" aria-label={etiqueta} className="flex flex-wrap gap-2">
      {opciones.map((o) => (
        <button key={o.valor} type="button" aria-pressed={valor === o.valor} onClick={() => onChange(o.valor)}
          className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors ${
            valor === o.valor ? 'border-tinta bg-tinta text-white' : 'border-linea bg-white text-tinta hover:border-niebla'}`}>
          {o.texto}
        </button>
      ))}
    </div>
  )
}

export function Reduccion() {
  const { usuario } = useSesion()
  const [params] = useSearchParams()
  const [clientes, setClientes] = useState<Cliente[]>([])
  const [clienteId, setClienteId] = useState(params.get('cliente') ?? '')
  const [banco, setBanco] = useState('')
  const [saldo, setSaldo] = useState<number | null>(150_000_000)
  const [tasaTexto, setTasaTexto] = useState('11')
  const [cuotasTexto, setCuotasTexto] = useState('240')
  const [seguro, setSeguro] = useState<number | null>(null)
  const [modo, setModo] = useState<ModoReduccion>('REDUCIR_PLAZO')
  const [frecuencia, setFrecuencia] = useState<FrecuenciaAbono>('MENSUAL')
  const [abonos, setAbonos] = useState<(number | null)[]>([500_000, null, null])
  const [escenarioTabla, setEscenarioTabla] = useState(1)
  const [tablaAbierta, setTablaAbierta] = useState(false)
  const [pagina, setPagina] = useState(0)

  const [sim, setSim] = useState<SimulacionReduccion | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [calculando, setCalculando] = useState(false)
  const turno = useRef(0)

  useEffect(() => { api<Cliente[]>('/clientes').then(setClientes).catch(() => setClientes([])) }, [])

  // Lo que se escribe en pantalla se valida aquí solo para no pedir cálculos vacíos; las cifras las da el backend.
  const solicitud = useMemo(() => {
    const tasa = parseFloat(tasaTexto.replace(',', '.'))
    const cuotas = parseInt(cuotasTexto, 10)
    if (!saldo || !(tasa >= 0 && tasa <= 100) || !(cuotas >= 1 && cuotas <= 360)) return null
    const lista = abonos.filter((a): a is number => !!a && a > 0)
    return {
      clienteId: clienteId ? Number(clienteId) : null,
      banco: banco.trim() || null,
      saldo,
      tasaEa: Number((tasa / 100).toFixed(6)),
      cuotasPendientes: cuotas,
      seguroMensual: seguro ?? 0,
      modo,
      frecuencia,
      abonos: lista,
      escenarioDeLaTabla: Math.min(escenarioTabla, lista.length),
    }
  }, [clienteId, banco, saldo, tasaTexto, cuotasTexto, seguro, modo, frecuencia, abonos, escenarioTabla])

  useEffect(() => {
    if (!solicitud) { setSim(null); setError(null); return }
    const mio = ++turno.current
    setCalculando(true)
    const espera = setTimeout(() => {
      api<SimulacionReduccion>('/reducciones/simular', { method: 'POST', body: JSON.stringify(solicitud) })
        .then((r) => { if (mio === turno.current) { setSim(r); setError(null) } })
        .catch((e) => { if (mio === turno.current) setError(e instanceof ErrorApi ? e.message : 'No se pudo simular.') })
        .finally(() => { if (mio === turno.current) setCalculando(false) })
    }, 350)
    return () => clearTimeout(espera)
  }, [solicitud])

  const cambiarAbono = (i: number, v: number | null) => setAbonos((a) => a.map((x, j) => (j === i ? v : x)))
  const columnas: { titulo: string; e: EscenarioReduccion }[] = sim
    ? [{ titulo: 'Actual', e: sim.actual }, ...sim.escenarios.map((e, i) => ({ titulo: `Ahorro ${i + 1}`, e }))]
    : []
  const conAbonos = (sim?.escenarios.length ?? 0) > 0
  const totalPaginas = sim ? Math.ceil(sim.tabla.length / FILAS_POR_PAGINA) : 0
  const filasVisibles = sim?.tabla.slice(pagina * FILAS_POR_PAGINA, (pagina + 1) * FILAS_POR_PAGINA) ?? []

  const filas: { nombre: string; valor: (e: EscenarioReduccion) => string | null; destacar?: boolean }[] = sim ? [
    { nombre: 'Cuotas pendientes', valor: (e) => `${e.cuotas} (${formatoMeses(e.cuotas)})` },
    { nombre: sim.modo === 'REDUCIR_CUOTA' ? 'Cuota al final' : 'Cuota', valor: (e) => formatoPesos(sim.modo === 'REDUCIR_CUOTA' ? e.cuotaFinal : e.cuotaInicial) },
    { nombre: 'Desembolso mensual', valor: (e) => formatoPesos(e.desembolsoMensual) },
    { nombre: 'Total de intereses', valor: (e) => formatoPesos(e.totalIntereses) },
    { nombre: 'Total a pagar', valor: (e) => formatoPesos(e.totalPagado) },
    { nombre: 'Ahorro en intereses', valor: (e) => (e.ahorroIntereses == null ? null : formatoPesos(e.ahorroIntereses)), destacar: true },
    { nombre: 'Ahorro en seguros', valor: (e) => (e.ahorroSeguro == null ? null : formatoPesos(e.ahorroSeguro)), destacar: true },
    { nombre: 'Ahorro total', valor: (e) => (e.ahorroTotal == null ? null : formatoPesos(e.ahorroTotal)), destacar: true },
    { nombre: 'Cuotas reducidas', valor: (e) => (e.cuotasReducidas == null ? null : `${e.cuotasReducidas} (${formatoMeses(e.cuotasReducidas)})`) },
    { nombre: `Ingreso necesario (${formatoPorcentaje(sim.pctCuotaMaxIngreso, 0)} máx.)`, valor: (e) => (e.ingresoRequerido == null ? null : formatoPesos(e.ingresoRequerido)) },
  ] : []

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="ancha text-[32px] font-semibold leading-[1.05] md:text-5xl">Reducción de crédito</h1>
      <p className="mt-1 max-w-2xl text-niebla">
        Cuánto ahorra un cliente en intereses si abona a capital a su crédito hipotecario. La Ley 546 de 1999
        permite los abonos extraordinarios sin penalidad.
      </p>

      <section className="mt-6 rounded-panel border border-linea bg-white p-5 md:p-6" aria-label="Datos del crédito">
        <div className="grid gap-4 md:grid-cols-2">
          <Campo etiqueta="Cliente (opcional)" ayuda="Si eliges uno, se compara con su ingreso.">
            {(id) => (
              <select id={id} value={clienteId} onChange={(e) => setClienteId(e.target.value)} className={claseInput}>
                <option value="">Sin cliente</option>
                {clientes.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
              </select>
            )}
          </Campo>
          <Campo etiqueta="Banco">
            {(id) => <input id={id} value={banco} maxLength={120} onChange={(e) => setBanco(e.target.value)} className={claseInput} placeholder="Ej. Banco Ejemplo" />}
          </Campo>
          <CampoPesos etiqueta="Saldo actual del crédito" valor={saldo} onChange={setSaldo} />
          <Campo etiqueta="Tasa efectiva anual (E.A.)" ayuda="En porcentaje, por ejemplo 11 o 11,5.">
            {(id) => <input id={id} inputMode="decimal" value={tasaTexto} onChange={(e) => setTasaTexto(e.target.value)} className={claseInput} />}
          </Campo>
          <Campo etiqueta="Cuotas pendientes" ayuda={solicitud ? formatoMeses(solicitud.cuotasPendientes) : 'Entre 1 y 360.'}>
            {(id) => <input id={id} inputMode="numeric" value={cuotasTexto} onChange={(e) => setCuotasTexto(e.target.value.replace(/\D/g, ''))} className={claseInput} />}
          </Campo>
          <CampoPesos etiqueta="Seguro mensual" valor={seguro} onChange={setSeguro} ayuda="Opcional. Se suma a lo que paga cada mes." />
        </div>

        <div className="mt-6 grid gap-5 border-t border-linea pt-5 md:grid-cols-2">
          <div>
            <div className="mb-1.5 text-sm font-medium text-tinta">¿Qué reduce el abono?</div>
            <Opciones etiqueta="Qué reduce el abono" opciones={modos} valor={modo} onChange={setModo} />
            <p className="mt-1.5 text-xs text-niebla">{modos.find((m) => m.valor === modo)?.ayuda}</p>
          </div>
          <div>
            <div className="mb-1.5 text-sm font-medium text-tinta">Frecuencia del abono</div>
            <Opciones etiqueta="Frecuencia del abono" opciones={frecuencias} valor={frecuencia} onChange={setFrecuencia} />
          </div>
        </div>

        <div className="mt-6 border-t border-linea pt-5">
          <div className="mb-3 text-sm font-medium text-tinta">Abonos a comparar (hasta tres)</div>
          <div className="grid gap-4 md:grid-cols-3">
            {abonos.map((a, i) => (
              <CampoPesos key={i} etiqueta={`Ahorro ${i + 1}`} valor={a} onChange={(v) => cambiarAbono(i, v)} />
            ))}
          </div>
        </div>
      </section>

      {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}
      {!solicitud && <div className="mt-4"><Aviso tipo="info">Completa el saldo, la tasa (0 a 100) y las cuotas pendientes (1 a 360).</Aviso></div>}

      {sim && (
        <div className={calculando ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
          <section className="relative isolate mt-6 overflow-hidden rounded-panel bg-prusia p-5 text-white md:p-7" aria-label="Crédito actual">
            <Terreno semilla={sim.cuotasPendientes + 5} niveles={12} className="absolute inset-0 -z-10 size-full text-trazo opacity-40" />
            <div className="grid gap-6 md:grid-cols-2 md:gap-x-10">
              <div className="md:col-span-2">
                <CifraAcotada tono="sobre-azul" grande etiqueta="Cuota actual" valor={formatoPesos(sim.actual.cuotaInicial)}
                  razon={`${formatoTasaEa(sim.tasaEa)} durante ${formatoMeses(sim.actual.cuotas)}`} />
              </div>
              <CifraAcotada tono="sobre-azul" etiqueta="Total a pagar" valor={formatoPesos(sim.actual.totalPagado)} razon="Sin abonos" />
              <CifraAcotada tono="sobre-azul" etiqueta="De ese total, intereses" valor={formatoPesos(sim.actual.totalIntereses)} />
            </div>
          </section>

          {!conAbonos && <div className="mt-4"><Aviso tipo="info">Escribe un abono extraordinario para ver cuánto se ahorra.</Aviso></div>}

          {conAbonos && (
            <section className="mt-6" aria-label="Comparación de escenarios">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="ancha text-xl font-semibold">Comparación</h2>
                <button onClick={() => window.print()} className={claseBotonSecundario}>Imprimir estudio</button>
              </div>
              <div className="mt-3 overflow-x-auto rounded-panel border border-linea bg-white">
                <table className="w-full min-w-[34rem] text-sm">
                  <thead>
                    <tr className="border-b border-linea text-left text-niebla">
                      <th className="px-4 py-3 font-medium"></th>
                      {columnas.map((c) => (
                        <th key={c.titulo} className="px-4 py-3 text-right font-medium">
                          {c.titulo}
                          {c.e.abono != null && <div className="cifra text-xs font-normal text-grafito">{formatoPesos(c.e.abono)}</div>}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {filas.map((f) => (
                      <tr key={f.nombre} className={`border-b border-linea last:border-0 ${f.destacar ? 'bg-cobalto-suave/50' : ''}`}>
                        <th scope="row" className="px-4 py-3 text-left font-normal text-grafito">{f.nombre}</th>
                        {columnas.map((c) => {
                          const v = f.valor(c.e)
                          return <td key={c.titulo} className={`cifra px-4 py-3 text-right ${f.destacar ? 'font-semibold text-cobalto-hondo' : 'text-tinta'}`}>{v ?? '—'}</td>
                        })}
                      </tr>
                    ))}
                    {sim.clienteId != null && (
                      <tr>
                        <th scope="row" className="px-4 py-3 text-left font-normal text-grafito">¿Alcanza el ingreso de {sim.clienteNombre}?</th>
                        {columnas.map((c) => (
                          <td key={c.titulo} className="px-4 py-3 text-right">
                            {c.e.cumpleIngreso == null ? '—' : c.e.cumpleIngreso
                              ? <span className="text-emerald-700">Sí</span> : <span className="text-red-700">No</span>}
                          </td>
                        ))}
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          <section className="mt-6" aria-label="Tabla de amortización">
            <button onClick={() => setTablaAbierta((v) => !v)} aria-expanded={tablaAbierta} className={claseBotonSecundario}>
              {tablaAbierta ? 'Ocultar' : 'Ver'} la tabla de amortización
            </button>
            {tablaAbierta && (
              <div className="mt-3">
                {conAbonos && (
                  <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
                    <span className="text-niebla">Escenario:</span>
                    <Opciones etiqueta="Escenario de la tabla" valor={String(sim.escenarioDeLaTabla)}
                      onChange={(v) => { setEscenarioTabla(Number(v)); setPagina(0) }}
                      opciones={[{ valor: '0', texto: 'Actual' }, ...sim.escenarios.map((_, i) => ({ valor: String(i + 1), texto: `Ahorro ${i + 1}` }))]} />
                  </div>
                )}
                <div className="overflow-x-auto rounded-panel border border-linea bg-white">
                  <table className="w-full min-w-[40rem] text-sm">
                    <thead>
                      <tr className="border-b border-linea text-right text-niebla">
                        {['Cuota', 'Pago', 'Intereses', 'Capital', 'Abono', 'Saldo'].map((t, i) => (
                          <th key={t} className={`px-3 py-2.5 font-medium ${i === 0 ? 'text-left' : ''}`}>{t}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {filasVisibles.map((f) => (
                        <tr key={f.mes} className={`cifra border-b border-linea text-right last:border-0 ${f.abono > 0 ? 'bg-cobalto-suave/50' : ''}`}>
                          <td className="px-3 py-2 text-left text-grafito">{f.mes}</td>
                          <td className="px-3 py-2">{formatoPesos(f.cuota)}</td>
                          <td className="px-3 py-2 text-niebla">{formatoPesos(f.intereses)}</td>
                          <td className="px-3 py-2">{formatoPesos(f.capital)}</td>
                          <td className="px-3 py-2 text-cobalto-hondo">{f.abono > 0 ? formatoPesos(f.abono) : '—'}</td>
                          <td className="px-3 py-2 font-medium text-tinta">{formatoPesos(f.saldo)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="mt-3 flex items-center justify-between text-sm text-niebla">
                  <span>Cuotas {pagina * FILAS_POR_PAGINA + 1}–{Math.min((pagina + 1) * FILAS_POR_PAGINA, sim.tabla.length)} de {sim.tabla.length}</span>
                  <div className="flex gap-2">
                    <button disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)} className={claseBotonSecundario}>Anterior</button>
                    <button disabled={pagina >= totalPaginas - 1} onClick={() => setPagina((p) => p + 1)} className={claseBotonSecundario}>Siguiente</button>
                  </div>
                </div>
              </div>
            )}
          </section>

          <p className="mt-6 text-xs text-niebla">{sim.aviso}</p>
          {conAbonos && usuario && <Estudio sim={sim} columnas={columnas} inmobiliaria={usuario.inmobiliaria} asesor={usuario.nombre} />}
        </div>
      )}
    </div>
  )
}

/** Versión para imprimir: solo visible al imprimir (ver index.css). Sin marca ajena: la de la inmobiliaria del asesor. */
type EstiloFila = 'banda' | 'ahorro' | 'total'
interface FilaHoja { nombre: string; valores: string[]; estilo?: EstiloFila }

const claseFila: Record<EstiloFila | 'normal', string> = {
  normal: 'border-b border-linea text-grafito',
  banda: 'bg-cobalto-suave font-medium text-prusia',
  ahorro: 'border-b border-linea text-grafito',
  total: 'bg-prusia font-medium text-white',
}

/** Un bloque de la hoja impresa: franja azul con el título y los escenarios, y debajo sus filas. */
function BloqueHoja({ titulo, encabezados, filas }: { titulo: string; encabezados: string[]; filas: FilaHoja[] }) {
  return (
    <table className="w-full table-fixed border-collapse text-[11px] leading-tight">
      <colgroup><col className="w-[34%]" />{encabezados.map((_, i) => <col key={i} />)}</colgroup>
      <thead>
        <tr className="bg-prusia text-white">
          <th scope="col" className="px-2 py-1.5 text-left font-medium">{titulo}</th>
          {encabezados.map((h, i) => <th key={i} scope="col" className="px-2 py-1.5 text-right font-medium">{h}</th>)}
        </tr>
      </thead>
      <tbody>
        {filas.map((f) => (
          <tr key={f.nombre} className={claseFila[f.estilo ?? 'normal']}>
            <th scope="row" className="px-2 py-1 text-left font-normal">{f.nombre}</th>
            {f.valores.map((v, i) => (
              <td key={i} className={`px-2 py-1 text-right ${f.estilo === 'ahorro' ? 'font-medium text-prusia-claro' : ''}`}>{v}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/**
 * La hoja que se imprime (solo se ve al imprimir): un estudio de una página con las condiciones actuales y los
 * escenarios de abono lado a lado, lo ahorrado y las firmas. Todas las cifras vienen de la simulación del backend.
 */
function Estudio({ sim, columnas, inmobiliaria, asesor }: {
  sim: SimulacionReduccion
  columnas: { titulo: string; e: EscenarioReduccion }[]
  inmobiliaria: string
  asesor: string
}) {
  const hoy = new Date().toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' })
  const todos = (v: string) => columnas.map(() => v)
  const porEscenario = (f: (e: EscenarioReduccion) => string) => columnas.map((c) => f(c.e))
  const solo = (f: (e: EscenarioReduccion) => string | null) => columnas.map((c, i) => (i === 0 ? '' : f(c.e) ?? ''))
  const mensual = sim.frecuencia === 'MENSUAL'
  const cuando = { MENSUAL: 'cada mes', ANUAL: 'una vez al año', UNICO: 'en un solo pago' }[sim.frecuencia]

  const condiciones: FilaHoja[] = [
    { nombre: 'Saldo del crédito', valores: todos(formatoPesos(sim.saldo)) },
    { nombre: 'Cuotas pendientes', valores: porEscenario((e) => String(e.cuotas)) },
    { nombre: 'Tiempo pendiente', valores: porEscenario((e) => formatoMeses(e.cuotas)) },
    { nombre: 'Tasa de interés cobrada', valores: todos(formatoTasaEa(sim.tasaEa)) },
    { nombre: `Abono adicional a la cuota (${cuando})`, estilo: 'banda', valores: porEscenario((e) => (e.abono == null ? '—' : formatoPesos(e.abono))) },
    { nombre: 'Valor de la cuota', valores: porEscenario((e) => formatoPesos(e.cuotaInicial)) },
    ...(sim.modo === 'REDUCIR_CUOTA'
      ? [{ nombre: 'Cuota al final del crédito', valores: porEscenario((e) => formatoPesos(e.cuotaFinal)) }]
      : []),
    { nombre: 'Valor del seguro', valores: todos(formatoPesos(sim.seguroMensual)) },
    {
      nombre: mensual ? `Nueva cuota aproximada${sim.seguroMensual > 0 ? ' (con seguro)' : ''}` : 'Cuota mensual (sin el abono)',
      estilo: 'banda',
      valores: porEscenario((e) => formatoPesos(e.desembolsoMensual)),
    },
    { nombre: 'Total deuda aproximada', valores: porEscenario((e) => formatoPesos(e.totalPagado)) },
    { nombre: 'Abono a capital (1.ª cuota)', valores: todos(formatoPesos(sim.primeraCuotaCapital)) },
    { nombre: 'Abono a intereses (1.ª cuota)', valores: todos(formatoPesos(sim.primeraCuotaIntereses)) },
  ]
  const ahorro: FilaHoja[] = [
    { nombre: 'Dinero ahorrado en intereses', estilo: 'ahorro', valores: solo((e) => (e.ahorroIntereses == null ? null : formatoPesos(e.ahorroIntereses))) },
    { nombre: 'Dinero ahorrado en seguros', valores: solo((e) => (e.ahorroSeguro == null ? null : formatoPesos(e.ahorroSeguro))) },
    { nombre: 'Total dinero ahorrado', estilo: 'total', valores: solo((e) => (e.ahorroTotal == null ? null : formatoPesos(e.ahorroTotal))) },
    { nombre: 'Cuotas reducidas', valores: solo((e) => (e.cuotasReducidas == null ? null : String(e.cuotasReducidas))) },
    { nombre: 'Tiempo ahorrado', estilo: 'ahorro', valores: solo((e) => (e.cuotasReducidas == null ? null : formatoMeses(e.cuotasReducidas))) },
    {
      nombre: `Ingreso requerido (cuota ≤ ${formatoPorcentaje(sim.pctCuotaMaxIngreso, 0)})`,
      valores: solo((e) => (e.ingresoRequerido == null ? null : formatoPesos(e.ingresoRequerido))),
    },
    // Solo si la simulación se hizo para un cliente: compara su ingreso con el requerido.
    ...(columnas.some((c) => c.e.cumpleIngreso != null)
      ? [{ nombre: 'El ingreso del cliente alcanza', valores: solo((e) => (e.cumpleIngreso == null ? null : e.cumpleIngreso ? 'Sí' : 'No')) }]
      : []),
  ]

  return (
    <section id="estudio" className="hidden bg-white p-2 text-black print:block">
      <header className="flex items-start justify-between border-b-2 border-prusia pb-3">
        <div>
          <div className="ancha text-xl font-semibold text-prusia">{inmobiliaria}</div>
          <div className="text-[11px] text-niebla">Estudio hecho con Predia</div>
          <h1 className="ancha mt-2 text-xl font-semibold">Estudio de reducción de crédito</h1>
        </div>
        <div className="text-right text-[11px] leading-snug text-grafito">
          <svg width="120" height="34" viewBox="0 0 120 34" aria-hidden="true" className="ml-auto block">
            <g fill="none" stroke="#a9c2f2" strokeWidth="1">
              <ellipse cx="86" cy="17" rx="32" ry="14" /><ellipse cx="88" cy="17" rx="22" ry="9" /><ellipse cx="90" cy="17" rx="12" ry="4.5" />
            </g>
          </svg>
          <div>{hoy}</div>
          <div>Ley 546 de 1999 · Colombia</div>
        </div>
      </header>
      <dl className="mb-3.5 mt-3 grid grid-cols-3 gap-2.5 text-[11px]">
        <div><dt className="text-niebla">Cliente</dt><dd className="font-medium">{sim.clienteNombre ?? '—'}</dd></div>
        <div><dt className="text-niebla">Banco</dt><dd className="font-medium">{sim.banco ?? '—'}</dd></div>
        <div><dt className="text-niebla">El abono reduce</dt><dd className="font-medium">{sim.modo === 'REDUCIR_PLAZO' ? 'El plazo' : 'La cuota'}, {cuando}</dd></div>
      </dl>
      <BloqueHoja titulo="Condiciones actuales con el banco" encabezados={columnas.map((c) => c.titulo)} filas={condiciones} />
      <div className="mt-3">
        <BloqueHoja titulo="Dinero y tiempo ahorrado" encabezados={columnas.map((c, i) => (i === 0 ? '' : c.titulo))} filas={ahorro} />
      </div>
      <p className="mt-3 text-[10.5px] leading-snug text-grafito">
        {sim.aviso} El ingreso requerido se calcula con una cuota máxima del {formatoPorcentaje(sim.pctCuotaMaxIngreso, 0)} del ingreso.
      </p>
      <div className="mt-9 grid grid-cols-2 gap-9 text-[11px]">
        <div className="border-t border-black pt-1">Firma del cliente</div>
        <div className="border-t border-black pt-1">Asesor: {asesor} · {inmobiliaria}</div>
      </div>
    </section>
  )
}
