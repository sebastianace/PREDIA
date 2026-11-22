import { useEffect, useRef, useState } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { Cita, Documento, EstadoIndexacion, Inmueble, RespuestaConsulta } from '../api/tipos'
import { Icono } from '../componentes/Iconos'
import { formatoFecha, formatoTamano } from '../formato'

const nombreTipo: Record<string, string> = {
  CEDULA: 'Cédula', CERTIFICADO_TRADICION_LIBERTAD: 'Certificado de tradición y libertad',
  ESCRITURA_PUBLICA: 'Escritura pública', PAZ_Y_SALVO_PREDIAL: 'Paz y salvo predial',
  PAZ_Y_SALVO_VALORIZACION: 'Paz y salvo de valorización', PAZ_Y_SALVO_ADMINISTRACION: 'Paz y salvo de administración',
  PROMESA_COMPRAVENTA: 'Promesa de compraventa', CARTA_LEVANTAMIENTO_HIPOTECA: 'Carta de levantamiento de hipoteca',
  DECLARACION_CONYUGE: 'Declaración del cónyuge', PODER_ESPECIAL: 'Poder especial',
  CERTIFICADO_EXISTENCIA_REPRESENTACION: 'Certificado de existencia y representación',
  RECIBO_SERVICIOS_PUBLICOS: 'Recibo de servicios públicos', CONTRATO_ARRENDAMIENTO: 'Contrato de arrendamiento',
  REGLAMENTO_PROPIEDAD_HORIZONTAL: 'Reglamento de propiedad horizontal', OTRO: 'Otro',
}
const tipoLegible = (t: string | null) => (t ? nombreTipo[t] ?? t : null)

const estadoEstilo: Record<EstadoIndexacion, { texto: string; clase: string; icono: 'reloj' | 'chulo' | 'equis' }> = {
  EN_COLA: { texto: 'En cola', clase: 'bg-stone-100 text-stone-600', icono: 'reloj' },
  PROCESANDO: { texto: 'Procesando', clase: 'bg-cobalto-suave text-cobalto-hondo', icono: 'reloj' },
  INDEXADO: { texto: 'Indexado', clase: 'bg-emerald-50 text-emerald-700', icono: 'chulo' },
  ERROR: { texto: 'Error', clase: 'bg-red-50 text-red-700', icono: 'equis' },
}

const sigueEnProceso = (docs: Documento[]) =>
  docs.some((d) => d.estadoIndexacion === 'EN_COLA' || d.estadoIndexacion === 'PROCESANDO')

export function Expedientes() {
  const [inmuebles, setInmuebles] = useState<Inmueble[]>([])
  const [inmuebleId, setInmuebleId] = useState<number | null>(null)
  const [documentos, setDocumentos] = useState<Documento[]>([])
  const [subiendo, setSubiendo] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputArchivo = useRef<HTMLInputElement>(null)

  const [pregunta, setPregunta] = useState('')
  const [preguntando, setPreguntando] = useState(false)
  const [respuesta, setRespuesta] = useState<RespuestaConsulta | null>(null)
  const [errorPregunta, setErrorPregunta] = useState<string | null>(null)

  useEffect(() => {
    api<Inmueble[]>('/inmuebles').then((lista) => {
      setInmuebles(lista)
      if (lista.length > 0) setInmuebleId(lista[0].id)
    })
  }, [])

  const cargarDocumentos = (id: number) =>
    api<Documento[]>(`/documentos?inmuebleId=${id}`).then(setDocumentos).catch(() => setDocumentos([]))

  useEffect(() => {
    if (inmuebleId == null) return
    setRespuesta(null)
    cargarDocumentos(inmuebleId)
  }, [inmuebleId])

  // Mientras algún documento siga EN_COLA o PROCESANDO, se refresca solo cada 3 s.
  useEffect(() => {
    if (inmuebleId == null || !sigueEnProceso(documentos)) return
    const intervalo = setInterval(() => cargarDocumentos(inmuebleId), 3000)
    return () => clearInterval(intervalo)
  }, [inmuebleId, documentos])

  const subir = async (archivo: File) => {
    if (inmuebleId == null) return
    setSubiendo(true)
    setError(null)
    try {
      const datos = new FormData()
      datos.append('archivo', archivo)
      datos.append('inmuebleId', String(inmuebleId))
      await api<Documento>('/documentos', { method: 'POST', body: datos })
      await cargarDocumentos(inmuebleId)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo subir el archivo.')
    } finally {
      setSubiendo(false)
      if (inputArchivo.current) inputArchivo.current.value = ''
    }
  }

  const preguntar = async () => {
    if (inmuebleId == null || !pregunta.trim()) return
    setPreguntando(true)
    setErrorPregunta(null)
    setRespuesta(null)
    try {
      const r = await api<RespuestaConsulta>('/consultas', {
        method: 'POST', body: JSON.stringify({ pregunta: pregunta.trim(), inmuebleId }),
      })
      setRespuesta(r)
    } catch (e) {
      setErrorPregunta(e instanceof ErrorApi ? e.message : 'No se pudo consultar el expediente.')
    } finally {
      setPreguntando(false)
    }
  }

  const inmuebleActual = inmuebles.find((i) => i.id === inmuebleId)

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">Expedientes</h1>
      <p className="mt-1 text-niebla">Sube los documentos de un inmueble y pregúntale al asistente sobre ellos.</p>

      <label className="mb-1.5 mt-6 block text-sm font-medium text-tinta" htmlFor="inmueble">Inmueble</label>
      <select id="inmueble" value={inmuebleId ?? ''} onChange={(e) => setInmuebleId(Number(e.target.value))}
        className="w-full rounded-control border border-linea bg-white px-3.5 py-2.5 text-base outline-none focus:border-cobalto focus:ring-4 focus:ring-cobalto-suave">
        {inmuebles.map((i) => (
          <option key={i.id} value={i.id}>{i.titulo} · {i.codigo}</option>
        ))}
      </select>

      <div className="mt-6 flex items-center justify-between gap-3 rounded-panel border border-dashed border-linea bg-white p-4">
        <div className="flex min-w-0 items-center gap-2.5 text-sm text-grafito">
          <Icono nombre="subir" className="size-5 shrink-0 text-niebla" />
          <span>Solo PDF, hasta 20 MB. El archivo no se conserva: se procesa y se borra.</span>
        </div>
        <label className={`shrink-0 cursor-pointer rounded-control px-3.5 py-2 text-sm font-medium text-white ${subiendo ? 'bg-niebla' : 'bg-tinta hover:bg-prusia'}`}>
          {subiendo ? 'Subiendo…' : 'Subir PDF'}
          <input ref={inputArchivo} type="file" accept="application/pdf" className="hidden" disabled={subiendo}
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void subir(f) }} />
        </label>
      </div>
      {error && <p role="alert" className="mt-3 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}

      <ul className="mt-6 divide-y divide-linea border-y border-linea">
        {documentos.map((d) => {
          const e = estadoEstilo[d.estadoIndexacion]
          const tipo = tipoLegible(d.tipoConfirmado ?? d.tipoSugerido)
          return (
            <li key={d.id} className="py-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="truncate font-medium text-tinta">{d.nombreArchivo}</div>
                  <div className="mt-0.5 text-sm text-niebla">
                    {formatoTamano(d.tamanoBytes)} · subido el {formatoFecha(d.creadoEn)}
                    {d.paginas != null && ` · ${d.paginas} página${d.paginas === 1 ? '' : 's'}`}
                    {d.paginasOcr > 0 && ` (${d.paginasOcr} con OCR)`}
                  </div>
                </div>
                <span className={`flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${e.clase}`}>
                  <Icono nombre={e.icono} className="size-3.5" />
                  {e.texto}
                </span>
              </div>
              {d.estadoIndexacion === 'ERROR' && d.error && (
                <p className="mt-2 text-sm text-red-700">{d.error}</p>
              )}
              {tipo && (
                <p className="mt-2 text-sm text-grafito">
                  <span className={d.clasificacionEstado === 'CONFIRMADA' ? 'font-medium text-tinta' : 'text-ambar'}>
                    {tipo}
                  </span>
                  {d.clasificacionEstado === 'SUGERIDA' && ' (sugerido por la IA, sin confirmar)'}
                </p>
              )}
            </li>
          )
        })}
        {documentos.length === 0 && <li className="py-4 text-sm text-niebla">Todavía no hay documentos para este inmueble.</li>}
      </ul>

      <section className="mt-10 rounded-panel border border-linea bg-white p-5 md:p-6" aria-labelledby="titulo-preguntar">
        <h2 id="titulo-preguntar" className="text-lg font-semibold">Pregúntale al expediente</h2>
        <p className="mt-1 text-sm text-niebla">
          {inmuebleActual ? `Sobre los documentos de ${inmuebleActual.titulo}.` : 'Elige un inmueble primero.'}
        </p>

        <div className="mt-4 flex gap-2">
          <input value={pregunta} onChange={(e) => setPregunta(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !preguntando && void preguntar()}
            placeholder="¿Qué le falta al vendedor? ¿Cuánto es el canon?"
            className="min-w-0 flex-1 rounded-control border border-linea bg-white px-3.5 py-2.5 text-base outline-none focus:border-cobalto focus:ring-4 focus:ring-cobalto-suave" />
          <button onClick={() => void preguntar()} disabled={preguntando || !pregunta.trim()}
            className="flex shrink-0 items-center gap-2 rounded-control bg-tinta px-4 py-2.5 text-sm font-semibold text-white hover:bg-prusia disabled:bg-niebla">
            <Icono nombre="enviar" className="size-4" />
            {preguntando ? 'Buscando…' : 'Preguntar'}
          </button>
        </div>

        {errorPregunta && <p role="alert" className="mt-4 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{errorPregunta}</p>}

        {respuesta && (
          <div className="mt-5 border-t border-linea pt-5">
            {respuesta.sinEvidencia ? (
              <p className="text-grafito">{respuesta.respuesta}</p>
            ) : respuesta.modoManual ? (
              <p className="text-sm text-ambar">
                Modo manual: aquí está la evidencia encontrada, sin una respuesta redactada por la IA.
              </p>
            ) : (
              <p className="whitespace-pre-line text-grafito">{respuesta.respuesta}</p>
            )}

            {respuesta.citas.length > 0 && (
              <ol className="mt-4 space-y-2">
                {respuesta.citas.map((c: Cita) => (
                  <li key={c.numero} className="flex gap-2 text-sm text-niebla">
                    <span className="shrink-0 font-medium text-cobalto">[{c.numero}]</span>
                    <span>
                      {c.nombreArchivo}, página {c.paginaInicio === c.paginaFin ? c.paginaInicio : `${c.paginaInicio}–${c.paginaFin}`}
                      {c.seccion && <> · {c.seccion}</>}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}
      </section>
    </div>
  )
}
