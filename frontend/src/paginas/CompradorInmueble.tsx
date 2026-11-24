import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, apiBlob, ErrorApi } from '../api/cliente'
import type { FichaComprador, InteresComprador, SimulacionCompra } from '../api/tipos'
import { CajaMensajeAsesor } from '../componentes/CajaMensajeAsesor'
import { CifraAcotada } from '../componentes/Cota'
import { EncabezadoComprador } from '../componentes/EncabezadoComprador'
import { Aviso } from '../componentes/Formulario'
import { FotoDeInmueble } from '../componentes/FotoInmueble'
import { AvisoTope } from '../componentes/AvisoTope'
import { Terreno } from '../componentes/Terreno'
import { formatoPesos, formatoPorcentaje, formatoTasaEa } from '../formato'

/** Pasos del deslizador de cuota inicial: de a un millón, o de a cien mil si el rango es corto. */
const pasoDe = (min: number, max: number) => (max - min >= 10_000_000 ? 1_000_000 : 100_000)

/**
 * La ficha de un inmueble compartido: foto, plano y datos a la izquierda; «Simule su compra» a la derecha.
 * No muestra el código interno, la dirección exacta ni nada del propietario. Las cifras del simulador las calcula
 * el servidor (con una pequeña espera al mover los controles), nunca el navegador.
 */
/** Barras que ocupan el lugar de las cifras mientras llegan del servidor. */
function EsqueletoSimulacion() {
  const barra = 'rounded bg-white/15 motion-safe:animate-pulse'
  return (
    <div role="status" aria-label="Calculando su cuota">
      <div className={`${barra} h-3 w-24`} />
      <div className={`${barra} mt-3 h-8 w-48`} />
      <div className={`${barra} mt-3 h-3 w-3/4`} />
      <div className="mt-5 space-y-3">
        <div className={`${barra} h-3 w-full`} />
        <div className={`${barra} h-3 w-full`} />
        <div className={`${barra} h-3 w-full`} />
      </div>
    </div>
  )
}

export function CompradorInmueble() {
  const { id } = useParams()
  const [ficha, setFicha] = useState<FichaComprador | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pasos, setPasos] = useState(0)
  const [plazo, setPlazo] = useState(20)
  const [sim, setSim] = useState<SimulacionCompra | null>(null)
  const [errorSim, setErrorSim] = useState<string | null>(null)
  const [calculando, setCalculando] = useState(false)
  const [verPlano, setVerPlano] = useState(false)

  useEffect(() => {
    api<FichaComprador>(`/comprador/inmuebles/${id}`).then((f) => {
      setFicha(f)
      if (f.simulador) {
        setPlazo(f.simulador.plazoPorDefecto)
        // Parte de la cuota inicial más alta que su ahorro permite: es la que le da la cuota mensual más baja.
        setPasos(Math.ceil((f.simulador.cuotaInicialMaxima - f.simulador.cuotaInicialMinima) / pasoDe(f.simulador.cuotaInicialMinima, f.simulador.cuotaInicialMaxima)))
      }
    }).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar el inmueble.'))
  }, [id])

  const c = ficha?.simulador
  const paso = c ? pasoDe(c.cuotaInicialMinima, c.cuotaInicialMaxima) : 1
  const totalPasos = c ? Math.ceil((c.cuotaInicialMaxima - c.cuotaInicialMinima) / paso) : 0
  const cuotaInicial = c ? Math.min(c.cuotaInicialMaxima, c.cuotaInicialMinima + pasos * paso) : 0

  // Pide las cifras al servidor al soltar los controles (con una espera corta para no pedir en cada pixel).
  const secuencia = useRef(0)
  useEffect(() => {
    if (!c || !ficha) return
    const mia = ++secuencia.current
    setCalculando(true)
    const t = setTimeout(() => {
      api<SimulacionCompra>(`/comprador/inmuebles/${ficha.id}/simulacion`, {
        method: 'POST', body: JSON.stringify({ cuotaInicial, plazoAnios: plazo }),
      }).then((s) => { if (mia === secuencia.current) { setSim(s); setErrorSim(null) } })
        .catch((e) => { if (mia === secuencia.current) setErrorSim(e instanceof ErrorApi ? e.message : 'No se pudo calcular.') })
        .finally(() => { if (mia === secuencia.current) setCalculando(false) })
    }, 300)
    return () => clearTimeout(t)
  }, [c, ficha, cuotaInicial, plazo])

  const marcar = async (interes: InteresComprador) => {
    if (!ficha) return
    const nuevo = ficha.interes === interes ? null : interes
    try {
      await api(`/comprador/inmuebles/${ficha.id}/interes`, { method: 'PUT', body: JSON.stringify({ interes: nuevo }) })
      setFicha({ ...ficha, interes: nuevo })
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo guardar su opinión.')
    }
  }

  return (
    <div className="min-h-dvh bg-papel">
      <EncabezadoComprador />
      <main className="mx-auto max-w-5xl px-4 pb-16 pt-5 md:px-6 md:pt-8">
        <Link to="/comprador" className="text-sm text-niebla hover:text-tinta">← Sus inmuebles</Link>
        {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}
        {!ficha && !error && <p className="mt-6 text-niebla">Cargando…</p>}

        {ficha && (
          <div className="mt-3 grid grid-cols-1 gap-8 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
            <div className="min-w-0">
              <div className="relative isolate aspect-[4/3] overflow-hidden rounded-panel bg-prusia">
                <Terreno semilla={ficha.id * 31 + 7} trazar niveles={12} className="absolute inset-0 -z-20 size-full text-trazo opacity-40" />
                <FotoDeInmueble inmuebleId={ficha.id} foto={ficha.foto} tam="grande" titulo={ficha.titulo} base={`/comprador/inmuebles/${ficha.id}/foto`} />
              </div>

              <h1 className="ancha mt-5 text-[30px] font-semibold leading-[1.05] md:text-4xl">{ficha.titulo}</h1>
              <p className="mt-1 text-niebla">{ficha.barrio}, {ficha.ciudad}</p>
              <p className="ancha cifra mt-3 text-3xl font-semibold text-tinta">{formatoPesos(ficha.precio)}</p>
              {ficha.disponible && ficha.excedenteSobreTope != null && (
                <AvisoTope excedente={ficha.excedenteSobreTope} conOrientacion />
              )}
              {!ficha.disponible && <div className="mt-3"><Aviso tipo="info">Este inmueble ya no está disponible. Su asesor puede mostrarle otras opciones.</Aviso></div>}

              <dl className="mt-5 grid grid-cols-2 gap-x-6 text-sm sm:grid-cols-3">
                {[
                  ['Área', `${ficha.areaM2} m²`],
                  ['Habitaciones', String(ficha.habitaciones)],
                  ['Baños', String(ficha.banos)],
                  ['Parqueaderos', String(ficha.parqueaderos)],
                  ['Estrato', ficha.estrato != null ? String(ficha.estrato) : '—'],
                  ['Administración', ficha.valorAdministracion > 0 ? `${formatoPesos(ficha.valorAdministracion)} al mes` : 'No aplica'],
                ].map(([k, v]) => (
                  <div key={k} className="border-t border-linea py-2.5">
                    <dt className="rotulo text-niebla">{k}</dt>
                    <dd className="mt-0.5 font-medium text-tinta">{v}</dd>
                  </div>
                ))}
              </dl>
              <p className="mt-3 text-sm text-grafito">
                {[ficha.aceptaMascotas ? 'Acepta mascotas' : 'No acepta mascotas',
                  ficha.propiedadHorizontal ? 'Propiedad horizontal' : null,
                  ficha.esVis ? 'Vivienda de interés social (VIS)' : null].filter(Boolean).join(' · ')}
              </p>
              {ficha.descripcion && <p className="mt-4 whitespace-pre-line text-grafito [overflow-wrap:anywhere]">{ficha.descripcion}</p>}
              {ficha.nota && (
                <div className="mt-5 border-l-2 border-cobalto bg-cobalto-suave px-4 py-3 text-sm text-prusia">
                  <p className="rotulo text-cobalto-hondo">Nota de su asesor</p>
                  <p className="mt-1 [overflow-wrap:anywhere]">{ficha.nota}</p>
                </div>
              )}

              {ficha.planoVersion && (
                <div className="mt-6">
                  <h2 className="ancha text-lg font-semibold">Plano</h2>
                  <MiniaturaPlano inmuebleId={ficha.id} version={ficha.planoVersion} titulo={ficha.titulo} onAbrir={() => setVerPlano(true)} />
                </div>
              )}
            </div>

            <aside className="min-w-0 lg:sticky lg:top-20 lg:self-start" aria-label="Simule su compra">
              <section className="relative isolate overflow-hidden rounded-panel bg-prusia px-5 pb-6 pt-5 text-white md:px-7">
                <Terreno semilla={ficha.id * 17 + 3} niveles={10} className="absolute inset-0 -z-10 size-full text-trazo opacity-30" />
                <h2 className="ancha text-xl font-semibold text-white">Simule su compra</h2>
                {!c && <p className="mt-3 text-sm text-white/75">El simulador no está disponible porque el inmueble ya no se vende.</p>}
                {c && (
                  <>
                    <div className="mt-5">
                      <div className="flex items-baseline justify-between gap-3 text-sm">
                        <label htmlFor="cuota-inicial" className="text-trazo">Cuota inicial</label>
                        <span className="ancha cifra text-lg font-semibold">{formatoPesos(cuotaInicial)}</span>
                      </div>
                      {totalPasos > 0 ? (
                        <input id="cuota-inicial" type="range" min={0} max={totalPasos} step={1} value={pasos}
                          onChange={(e) => setPasos(Number(e.target.value))} className="mt-2 w-full accent-white" />
                      ) : <p className="mt-1 text-xs text-white/70">Es la única cuota inicial posible con su ahorro.</p>}
                      <div className="mt-1 flex justify-between text-xs text-white/60">
                        <span>Mínima {formatoPesos(c.cuotaInicialMinima)}</span>
                        <span>{c.ahorroAlcanza ? `Hasta ${formatoPesos(c.cuotaInicialMaxima)}` : ''}</span>
                      </div>
                      {!c.ahorroAlcanza && (
                        <p className="mt-2 text-xs text-[#ffd28a]">Su ahorro aún no alcanza la cuota inicial mínima: le faltan {formatoPesos(c.faltanteCuotaInicial)}.</p>
                      )}
                    </div>

                    <div className="mt-5">
                      <div className="flex items-baseline justify-between gap-3 text-sm">
                        <label htmlFor="plazo" className="text-trazo">Plazo</label>
                        <span className="ancha cifra text-lg font-semibold">{plazo} años</span>
                      </div>
                      <input id="plazo" type="range" min={c.plazoMinimo} max={c.plazoMaximo} step={1} value={plazo}
                        onChange={(e) => setPlazo(Number(e.target.value))} className="mt-2 w-full accent-white" />
                    </div>

                    <div className="mt-7 min-h-44" aria-live="polite">
                      {errorSim && <Aviso tipo="error">{errorSim}</Aviso>}
                      {!sim && !errorSim && <EsqueletoSimulacion />}
                      {sim && (
                        <div className={calculando ? 'opacity-50 transition-opacity' : 'transition-opacity'} aria-busy={calculando}>
                          <CifraAcotada tono="sobre-azul" etiqueta="Cuota mensual" valor={formatoPesos(sim.cuotaMensual)}
                            razon={sim.porcentajeSobreIngreso != null ? `Es el ${formatoPorcentaje(sim.porcentajeSobreIngreso)} de su ingreso.` : undefined} />
                          <p className={`mt-3 text-sm font-medium ${sim.cumpleCuotaMaxima ? 'text-[#9be7b5]' : 'text-[#ffd28a]'}`}>{sim.veredicto}</p>
                          <dl className="mt-4 text-sm">
                            {[
                              ['Monto a financiar', sim.montoFinanciado],
                              [`Total en intereses (${sim.numeroCuotas} cuotas)`, sim.totalIntereses],
                              ['Total que pagaría al banco', sim.totalAPagar],
                            ].map(([k, v]) => (
                              <div key={String(k)} className="flex justify-between gap-3 border-t border-white/25 py-2">
                                <dt className="text-trazo">{k}</dt>
                                <dd className="cifra font-medium">{formatoPesos(v as number)}</dd>
                              </div>
                            ))}
                          </dl>
                          <p className="mt-3 text-xs text-white/60">
                            Tasa de {sim.tasa.banco}: {formatoTasaEa(sim.tasa.tasaEa)}{sim.tasa.esEjemplo && ' (de ejemplo)'}. {sim.aviso}
                          </p>
                        </div>
                      )}
                    </div>
                  </>
                )}
              </section>

              <div className="mt-4 flex flex-wrap gap-2">
                {(['ME_INTERESA', 'NO_ME_INTERESA'] as const).map((op) => (
                  <button key={op} onClick={() => void marcar(op)} aria-pressed={ficha.interes === op}
                    className={`rounded-full border px-3.5 py-1.5 text-sm ${ficha.interes === op
                      ? (op === 'ME_INTERESA' ? 'border-cobalto bg-cobalto-suave text-cobalto-hondo' : 'border-tinta bg-tinta text-white')
                      : 'border-linea bg-white text-tinta hover:border-niebla'}`}>
                    {op === 'ME_INTERESA' ? 'Me interesa' : 'No me interesa'}
                  </button>
                ))}
              </div>
              <div className="mt-5"><CajaMensajeAsesor inmuebleId={ficha.id} etiqueta="Quiero más información" /></div>
            </aside>
          </div>
        )}
      </main>
      {verPlano && ficha && <VisorPlano inmuebleId={ficha.id} version={ficha.planoVersion ?? ''} titulo={ficha.titulo} onCerrar={() => setVerPlano(false)} />}
    </div>
  )
}

// El plano se descarga con la sesión (un <img src> no envía el JWT) y se reutiliza mientras no cambie su versión.
const cachePlano = new Map<string, Promise<string>>()
function usePlano(inmuebleId: number, version: string): string | null {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    const clave = `${inmuebleId}:${version}`
    let vigente = true
    let p = cachePlano.get(clave)
    if (!p) {
      p = apiBlob(`/comprador/inmuebles/${inmuebleId}/plano`).then((b) => URL.createObjectURL(b))
      p.catch(() => cachePlano.delete(clave))
      cachePlano.set(clave, p)
    }
    p.then((u) => { if (vigente) setUrl(u) }).catch(() => { if (vigente) setUrl(null) })
    return () => { vigente = false }
  }, [inmuebleId, version])
  return url
}

function MiniaturaPlano({ inmuebleId, version, titulo, onAbrir }: { inmuebleId: number; version: string; titulo: string; onAbrir: () => void }) {
  const url = usePlano(inmuebleId, version)
  return (
    <button onClick={onAbrir} disabled={!url} className="mt-3 block w-full overflow-hidden rounded-panel border border-linea bg-white p-2 text-left hover:border-niebla">
      {url ? <img src={url} alt={`Plano de ${titulo}`} className="max-h-72 w-full object-contain" /> : <span className="block py-16 text-center text-sm text-niebla">Cargando plano…</span>}
      <span className="mt-2 block text-center text-sm font-medium text-cobalto">Ver plano completo</span>
    </button>
  )
}

/** El plano a pantalla completa; un toque lo amplía para ver los detalles y otro lo ajusta de nuevo. */
function VisorPlano({ inmuebleId, version, titulo, onCerrar }: { inmuebleId: number; version: string; titulo: string; onCerrar: () => void }) {
  const url = usePlano(inmuebleId, version)
  const [ampliado, setAmpliado] = useState(false)
  useEffect(() => {
    const alTeclear = (e: KeyboardEvent) => { if (e.key === 'Escape') onCerrar() }
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [onCerrar])
  return (
    <div role="dialog" aria-modal="true" aria-label={`Plano de ${titulo}`} className="fixed inset-0 z-50 flex flex-col bg-black/85">
      <div className="flex items-center justify-between gap-3 px-4 py-3 text-white">
        <p className="min-w-0 truncate text-sm">Plano de {titulo}</p>
        <button autoFocus onClick={onCerrar} className="rounded-control border border-white/40 px-3 py-1.5 text-sm hover:bg-white/10">Cerrar</button>
      </div>
      <div className="min-h-0 flex-1 overflow-auto p-3">
        {url && (
          <img src={url} alt={`Plano de ${titulo}`} onClick={() => setAmpliado((a) => !a)}
            className={`mx-auto rounded-sm bg-white ${ampliado ? 'max-w-none cursor-zoom-out' : 'max-h-full max-w-full cursor-zoom-in object-contain'}`}
            style={ampliado ? { width: '220%' } : undefined} />
        )}
      </div>
    </div>
  )
}
