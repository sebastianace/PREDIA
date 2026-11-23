import { Fragment, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { CitaAsistente, RespuestaAsistente } from '../api/tipos'
import { Aviso, claseBotonSecundario } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { useSesion } from '../sesion/Sesion'

interface Mensaje {
  rol: 'asesor' | 'asistente' | 'error'
  texto: string
  detalle?: RespuestaAsistente
}

const CLAVE = 'predia.asistente'

const ejemplos = [
  '¿Qué le falta al dueño del apto 1102 de Cañaveral para firmar?',
  '¿Qué les alcanza a Paola y Juan en Floridablanca?',
  '¿La venta del 1102 incluye parqueadero?',
]

// Lo que consultó el asistente, en palabras del asesor (así ve de dónde salió cada dato).
const nombreHerramienta: Record<string, string> = {
  consultarExpedientes: 'Expedientes',
  buscarInmuebles: 'Inventario',
  calcularPresupuesto: 'Presupuesto',
  simularCredito: 'Simulador de crédito',
  simularReduccionCredito: 'Reducción de crédito',
  estadoDocumentalNegocio: 'Estado del negocio',
  redactarRecordatorioPropietario: 'Recordatorio (borrador)',
}

function leerGuardado(): { conversacionId: string | null; mensajes: Mensaje[] } {
  try {
    const crudo = sessionStorage.getItem(CLAVE)
    if (crudo) return JSON.parse(crudo)
  } catch { /* sin almacenamiento: el chat funciona igual */ }
  return { conversacionId: null, mensajes: [] }
}

/** Chat con el asistente: el modelo elige herramientas y las cifras salen de las reglas de Predia, no del modelo. */
export function Asistente() {
  const { sistema } = useSesion()
  const inicial = useRef(leerGuardado()).current
  const [mensajes, setMensajes] = useState<Mensaje[]>(inicial.mensajes)
  const [conversacionId, setConversacionId] = useState<string | null>(inicial.conversacionId)
  const [texto, setTexto] = useState('')
  const [pensando, setPensando] = useState(false)
  const final = useRef<HTMLDivElement>(null)
  const sinIa = sistema !== null && !sistema.iaDisponible

  useEffect(() => {
    try { sessionStorage.setItem(CLAVE, JSON.stringify({ conversacionId, mensajes })) } catch { /* ignorar */ }
    final.current?.scrollIntoView({ block: 'end', behavior: 'smooth' })
  }, [mensajes, conversacionId, pensando])

  const enviar = async (pregunta: string) => {
    const limpia = pregunta.trim()
    if (!limpia || pensando || sinIa) return
    setTexto('')
    setMensajes((m) => [...m, { rol: 'asesor', texto: limpia }])
    setPensando(true)
    try {
      const r = await api<RespuestaAsistente>('/asistente/mensajes', {
        method: 'POST',
        body: JSON.stringify({ mensaje: limpia, conversacionId }),
      })
      setConversacionId(r.conversacionId)
      setMensajes((m) => [...m, { rol: 'asistente', texto: r.respuesta, detalle: r }])
    } catch (e) {
      setMensajes((m) => [...m, { rol: 'error', texto: e instanceof ErrorApi ? e.message : 'No se pudo contactar al asistente.' }])
    } finally {
      setPensando(false)
    }
  }

  const alEnviar = (e: FormEvent) => { e.preventDefault(); void enviar(texto) }
  const alTeclear = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void enviar(texto) }
  }
  const nueva = () => { setMensajes([]); setConversacionId(null); setTexto('') }

  return (
    <div className="mx-auto flex w-full min-w-0 max-w-3xl flex-col">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
        <div>
          <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">Asistente</h1>
          <p className="mt-1 text-niebla">
            Pregunta por expedientes, inmuebles, presupuestos, créditos y el cierre de un negocio. Las cifras salen de las
            reglas de Predia y todo se limita a tu inmobiliaria.
          </p>
        </div>
        {mensajes.length > 0 && (
          <button onClick={nueva} className={`${claseBotonSecundario} shrink-0 self-start`}>Nueva conversación</button>
        )}
      </div>

      {sinIa && (
        <div className="mt-6">
          <Aviso tipo="info">
            El asistente está deshabilitado porque la IA no está conectada. El resto de Predia funciona con normalidad:
            usa el inventario, los clientes, los negocios y los expedientes.
          </Aviso>
        </div>
      )}

      <div className="mt-6 space-y-5" aria-live="polite">
        {mensajes.length === 0 && !sinIa && (
          <div className="rounded-panel border border-dashed border-linea bg-white p-5">
            <p className="text-sm text-niebla">Prueba con una de estas preguntas:</p>
            <ul className="mt-3 space-y-2">
              {ejemplos.map((e) => (
                <li key={e}>
                  <button onClick={() => void enviar(e)}
                    className="w-full rounded-control border border-linea px-3.5 py-2.5 text-left text-sm text-grafito hover:border-cobalto hover:bg-cobalto-suave">
                    {e}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {mensajes.map((m, i) => <Burbuja key={i} mensaje={m} />)}

        {pensando && (
          <div className="flex items-center gap-2 text-sm text-niebla" role="status">
            <span className="flex gap-1" aria-hidden="true">
              {[0, 1, 2].map((n) => (
                <span key={n} className="size-1.5 animate-pulse rounded-full bg-niebla" style={{ animationDelay: `${n * 180}ms` }} />
              ))}
            </span>
            Consultando las herramientas de Predia…
          </div>
        )}
        {/* El margen evita que el último mensaje quede tapado por el campo fijo y la barra inferior. */}
        <div ref={final} className="scroll-mb-48 md:scroll-mb-32" />
      </div>

      <form onSubmit={alEnviar}
        className="sticky bottom-[calc(4.2rem+env(safe-area-inset-bottom))] z-10 mt-6 flex items-end gap-2 bg-papel pb-2 pt-3 md:bottom-0">
        <label htmlFor="mensaje" className="sr-only">Mensaje para el asistente</label>
        <textarea id="mensaje" value={texto} onChange={(e) => setTexto(e.target.value)} onKeyDown={alTeclear}
          rows={2} maxLength={2000} disabled={sinIa}
          placeholder={sinIa ? 'El asistente no está disponible sin IA' : 'Escribe tu pregunta…'}
          className="min-h-[3.25rem] flex-1 resize-none rounded-control border border-linea bg-white px-3.5 py-2.5 text-base text-tinta placeholder:text-niebla focus:border-cobalto disabled:bg-papel" />
        <button type="submit" disabled={pensando || sinIa || !texto.trim()} aria-label="Enviar"
          className="grid size-[3.25rem] shrink-0 place-items-center rounded-control bg-cobalto text-white hover:bg-cobalto-hondo disabled:cursor-not-allowed disabled:opacity-40">
          <Icono nombre="enviar" />
        </button>
      </form>
    </div>
  )
}

function Burbuja({ mensaje }: { mensaje: Mensaje }) {
  if (mensaje.rol === 'asesor') {
    return (
      <div className="flex justify-end">
        <p className="max-w-[85%] whitespace-pre-wrap rounded-panel rounded-br-sm [overflow-wrap:anywhere] bg-prusia px-4 py-2.5 text-[15px] text-white">
          {mensaje.texto}
        </p>
      </div>
    )
  }
  if (mensaje.rol === 'error') return <Aviso tipo="error">{mensaje.texto}</Aviso>

  const d = mensaje.detalle
  const usadas = d ? [...new Set(d.herramientas)] : []
  return (
    <div className="min-w-0 max-w-[92%] space-y-3">
      <div className="rounded-panel rounded-bl-sm border border-linea bg-white px-4 py-3 text-[15px] leading-relaxed text-grafito [overflow-wrap:anywhere]">
        <TextoConNegritas texto={mensaje.texto} />
      </div>

      {d && d.recordatoriosCreados.length > 0 && (
        <div className="border-l-2 border-cobalto bg-cobalto-suave px-3 py-2 text-sm text-prusia">
          <span className="font-semibold">Quedó como borrador; todavía no se envió nada.</span>{' '}
          <Link to="/recordatorios" className="font-semibold underline">Revísalo y apruébalo en Recordatorios</Link>.
        </div>
      )}

      {d && d.cifrasSinRespaldo.length > 0 && (
        <div role="alert" className="flex items-start gap-2 border-l-2 border-ambar bg-ambar-suave px-3 py-2 text-sm text-grafito">
          <Icono nombre="alerta" className="mt-0.5 size-4 shrink-0 text-ambar" />
          <p>
            <span className="font-semibold text-tinta">Verifica estas cifras: </span>
            {d.cifrasSinRespaldo.join(', ')}. {d.aviso}
          </p>
        </div>
      )}

      {d && d.citas.length > 0 && <Citas citas={d.citas} />}

      {usadas.length > 0 && (
        <p className="flex flex-wrap items-center gap-1.5 text-xs text-niebla">
          Consultó:
          {usadas.map((h) => (
            <span key={h} className="rounded-full border border-linea bg-white px-2.5 py-0.5 text-grafito">
              {nombreHerramienta[h] ?? h}
            </span>
          ))}
        </p>
      )}
    </div>
  )
}

function Citas({ citas }: { citas: CitaAsistente[] }) {
  return (
    <ul className="space-y-1 rounded-control border border-linea bg-papel px-3 py-2 text-xs text-grafito">
      {citas.map((c) => (
        <li key={c.numero}>
          <span className="font-semibold text-tinta">[{c.numero}]</span> {c.nombreArchivo},{' '}
          {c.paginaInicio === c.paginaFin ? `pág. ${c.paginaInicio}` : `págs. ${c.paginaInicio}-${c.paginaFin}`}
          {c.seccion ? ` · ${c.seccion}` : ''}
        </li>
      ))}
    </ul>
  )
}

/** Respeta los saltos de línea y las **negritas** que usa el modelo, sin interpretar nada más (no hay HTML del modelo). */
function TextoConNegritas({ texto }: { texto: string }) {
  return (
    <p className="whitespace-pre-wrap">
      {texto.split(/(\*\*[^*]+\*\*)/g).map((trozo, i) => (
        <Fragment key={i}>
          {trozo.startsWith('**') && trozo.endsWith('**') ? <strong className="text-tinta">{trozo.slice(2, -2)}</strong> : trozo}
        </Fragment>
      ))}
    </p>
  )
}
