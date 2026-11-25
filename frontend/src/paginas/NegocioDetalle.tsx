import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useLocation, useParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { Documento, EstadoDocumental, HipotecaVigente, ItemDocumental, Negocio, Recordatorio } from '../api/tipos'
import { Aviso, Campo, claseBotonPrimario, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { CotaAvance } from '../componentes/Cota'
import { EstadoRequisitoChip } from '../componentes/Semaforo'
import { AccesoPortalPropietario } from '../componentes/AccesoPortalPropietario'
import { Terreno } from '../componentes/Terreno'
import { formatoFecha, formatoFechaHora } from '../formato'

export function NegocioDetalle() {
  const { id } = useParams()
  // Si la foto del vendedor no se pudo guardar al abrir el negocio, el formulario avisa aquí (el negocio sí quedó).
  const avisoFoto = (useLocation().state as { avisoFoto?: string } | null)?.avisoFoto
  const [negocio, setNegocio] = useState<Negocio | null>(null)
  const [estado, setEstado] = useState<EstadoDocumental | null>(null)
  const [documentos, setDocumentos] = useState<Documento[]>([])
  const [recordatorios, setRecordatorios] = useState<Recordatorio[]>([])
  const [error, setError] = useState<string | null>(null)
  const [abierto, setAbierto] = useState<number | null>(null)
  const [subiendo, setSubiendo] = useState(false)
  const [preparando, setPreparando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const inputArchivo = useRef<HTMLInputElement>(null)

  const [fechaFirma, setFechaFirma] = useState('')
  const [hipoteca, setHipoteca] = useState<HipotecaVigente>('DESCONOCIDO')
  const [apoderado, setApoderado] = useState(false)
  const [guardandoDatos, setGuardandoDatos] = useState(false)

  const cargar = useCallback(async () => {
    try {
      const n = await api<Negocio>(`/negocios/${id}`)
      setNegocio(n)
      setFechaFirma(n.fechaFirmaTentativa ?? '')
      setHipoteca(n.hipotecaVigente)
      setApoderado(n.actuaPorApoderado)
      const [e, d, r] = await Promise.all([
        api<EstadoDocumental>(`/negocios/${id}/estado-documental`),
        api<Documento[]>(`/documentos?inmuebleId=${n.inmuebleId}`),
        api<Recordatorio[]>(`/negocios/${id}/recordatorios`),
      ])
      setEstado(e); setDocumentos(d); setRecordatorios(r)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar el negocio.')
    }
  }, [id])

  useEffect(() => { void cargar() }, [cargar])

  // Mientras un documento se indexa, el estado se refresca solo: la sugerencia de la IA aparece sin recargar.
  const indexando = documentos.some((d) => d.estadoIndexacion === 'EN_COLA' || d.estadoIndexacion === 'PROCESANDO')
  useEffect(() => {
    if (!indexando) return
    const intervalo = setInterval(() => void cargar(), 3000)
    return () => clearInterval(intervalo)
  }, [indexando, cargar])

  const guardarDatos = async () => {
    if (!negocio) return
    setGuardandoDatos(true)
    try {
      await api(`/negocios/${negocio.id}`, { method: 'PUT', body: JSON.stringify({
        propietarioId: negocio.propietarioId, clienteCompradorId: negocio.clienteCompradorId,
        fechaFirmaTentativa: fechaFirma || null, actuaPorApoderado: apoderado, hipotecaVigente: hipoteca,
        estado: negocio.estado }) })
      await cargar()
      setMensaje('Datos del negocio actualizados.')
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo guardar.')
    } finally {
      setGuardandoDatos(false)
    }
  }

  const subir = async (archivo: File) => {
    if (!negocio) return
    setSubiendo(true); setError(null)
    try {
      const datos = new FormData()
      datos.append('archivo', archivo)
      datos.append('inmuebleId', String(negocio.inmuebleId))
      datos.append('negocioId', String(negocio.id))
      await api<Documento>('/documentos', { method: 'POST', body: datos })
      setMensaje('Documento recibido. Predia lo está leyendo para sugerir a qué requisito corresponde.')
      await cargar()
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo subir el archivo.')
    } finally {
      setSubiendo(false)
      if (inputArchivo.current) inputArchivo.current.value = ''
    }
  }

  const preparar = async () => {
    setPreparando(true); setError(null)
    try {
      await api<Recordatorio>(`/negocios/${id}/recordatorios`, { method: 'POST' })
      setMensaje('Recordatorio preparado. Revísalo y apruébalo en la bandeja: todavía no se envió nada.')
      await cargar()
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo preparar el recordatorio.')
    } finally {
      setPreparando(false)
    }
  }

  if (!negocio || !estado) return error ? <Aviso tipo="error">{error}</Aviso> : <p className="text-niebla">Cargando…</p>
  const pendienteDeAprobar = recordatorios.filter((r) => r.estado === 'SUGERIDO').length

  return (
    <div className="mx-auto max-w-3xl">
      {avisoFoto && <div className="mb-3"><Aviso tipo="error">{avisoFoto}</Aviso></div>}
      <header className="relative isolate overflow-hidden rounded-panel bg-prusia px-5 pb-6 pt-5 text-white md:px-8 md:pb-8 md:pt-6">
        <Terreno semilla={negocio.inmuebleId * 31 + 7} trazar niveles={12}
          className="absolute inset-0 -z-10 size-full text-trazo opacity-40" />
        <Link to="/negocios" className="text-sm text-trazo hover:text-white">← Negocios</Link>
        <p className="rotulo mt-5 text-sm text-trazo">Negocio de venta</p>
        <h1 className="ancha mt-1 text-[32px] font-semibold leading-[1.05] text-white md:text-5xl">{negocio.inmuebleTitulo}</h1>
        <dl className="mt-6 grid grid-cols-2 gap-x-6 text-sm md:grid-cols-4">
          {[
            ['Vende', negocio.propietarioNombre],
            ['Compra', negocio.clienteCompradorNombre ?? 'Sin comprador'],
            ['Firma', negocio.fechaFirmaTentativa ? formatoFecha(negocio.fechaFirmaTentativa) : 'Sin fecha'],
            ['Asesor', negocio.asesorNombre ?? '—'],
          ].map(([k, v]) => (
            <div key={k} className="border-t border-white/25 py-2.5">
              <dt className="rotulo text-trazo">{k}</dt>
              <dd className="mt-0.5 break-words font-medium">{v}</dd>
            </div>
          ))}
        </dl>
        <p className="ancha mt-6 text-2xl font-semibold md:text-3xl">
          <span className={estado.listoParaFirmar ? 'text-[#9be7b5]' : 'text-white'}>{estado.semaforo}</span>
        </p>
        <div className="mt-4">
          <CotaAvance items={estado.items} porcentaje={estado.porcentajeAvance} tono="sobre-azul" />
        </div>
      </header>
      {estado.advertencias.length > 0 && (
        <ul className="mt-4 space-y-2">
          {estado.advertencias.map((a) => <li key={a}><Aviso tipo="info">{a}</Aviso></li>)}
        </ul>
      )}

      {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}
      {mensaje && <div className="mt-4"><Aviso tipo="info">{mensaje}</Aviso></div>}

      <section className="mt-8" aria-labelledby="titulo-requisitos">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="titulo-requisitos" className="text-lg font-semibold">Documentos del vendedor</h2>
          <label className={`${claseBotonSecundario} cursor-pointer`}>
            {subiendo ? 'Subiendo…' : 'Subir un PDF'}
            <input ref={inputArchivo} type="file" accept="application/pdf" className="hidden" disabled={subiendo}
              onChange={(e) => { const f = e.target.files?.[0]; if (f) void subir(f) }} />
          </label>
        </div>
        <p className="mt-1 text-xs text-niebla">{estado.aviso} Predia no da asesoría legal.</p>

        <ul className="mt-4 divide-y divide-linea border-y border-linea">
          {estado.items.map((item) => (
            <FilaRequisito key={item.id} item={item} negocioId={negocio.id} documentos={documentos}
              abierto={abierto === item.id} onAbrir={() => setAbierto(abierto === item.id ? null : item.id)}
              onCambio={async () => { setAbierto(null); await cargar() }} onError={setError} />
          ))}
        </ul>
      </section>

      <AccesoPortalPropietario propietarioId={negocio.propietarioId} nombre={negocio.propietarioNombre} />

      <section className="mt-10" aria-labelledby="titulo-recordatorios">
        <h2 id="titulo-recordatorios" className="text-lg font-semibold">Recordatorios al propietario</h2>
        <p className="mt-1 text-sm text-niebla">
          Predia redacta el mensaje con lo que falta; tú lo revisas y lo apruebas. Sin tu aprobación no se envía nada.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button onClick={() => void preparar()} disabled={preparando || estado.listoParaFirmar} className={claseBotonPrimario}>
            {preparando ? 'Preparando…' : 'Preparar recordatorio'}
          </button>
          {pendienteDeAprobar > 0 && (
            <Link to="/recordatorios" className="text-sm font-medium text-cobalto hover:text-cobalto-hondo">
              Hay {pendienteDeAprobar} por aprobar en la bandeja
            </Link>
          )}
        </div>
        <ul className="mt-4 space-y-2 text-sm">
          {recordatorios.slice(0, 5).map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 rounded-control border border-linea bg-white px-4 py-3">
              <span className="min-w-0 truncate text-grafito">{r.asunto}</span>
              <span className="text-xs text-niebla">
                {r.estado === 'ENVIADO' ? `Enviado el ${formatoFechaHora(r.enviadoEn)}${r.aprobadoPor ? ` · aprobó ${r.aprobadoPor}` : ''}`
                  : r.estado === 'SUGERIDO' ? 'Por aprobar' : r.estado === 'DESCARTADO' ? 'Descartado' : r.estado === 'ERROR_ENVIO' ? 'Error al enviar' : r.estado}
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-10 rounded-panel border border-linea bg-white p-5" aria-labelledby="titulo-datos">
        <h2 id="titulo-datos" className="text-lg font-semibold">Datos del negocio</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <Campo etiqueta="Fecha tentativa de firma">
            {(idc) => <input id={idc} type="date" value={fechaFirma} onChange={(e) => setFechaFirma(e.target.value)} className={claseInput} />}
          </Campo>
          <Campo etiqueta="¿Hipoteca vigente?">
            {(idc) => (
              <select id={idc} value={hipoteca} onChange={(e) => setHipoteca(e.target.value as HipotecaVigente)} className={claseInput}>
                <option value="DESCONOCIDO">Aún no lo sé</option><option value="NO">No</option><option value="SI">Sí</option>
              </select>
            )}
          </Campo>
        </div>
        <label className="mt-4 flex items-center gap-2 text-sm text-grafito">
          <input type="checkbox" checked={apoderado} onChange={(e) => setApoderado(e.target.checked)} className="size-4 accent-[var(--color-cobalto)]" />
          El vendedor actúa por medio de apoderado
        </label>
        <button onClick={() => void guardarDatos()} disabled={guardandoDatos} className={`${claseBotonSecundario} mt-4`}>
          {guardandoDatos ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </section>
    </div>
  )
}

interface PropsFila {
  item: ItemDocumental
  negocioId: number
  documentos: Documento[]
  abierto: boolean
  onAbrir: () => void
  onCambio: () => Promise<void>
  onError: (m: string | null) => void
}

function FilaRequisito({ item, negocioId, documentos, abierto, onAbrir, onCambio, onError }: PropsFila) {
  const [documentoId, setDocumentoId] = useState(item.documentoId ? String(item.documentoId) : '')
  const [fecha, setFecha] = useState(item.fechaExpedicion ?? '')
  const [enviando, setEnviando] = useState(false)
  const noAplicaPorReglas = item.estado === 'NO_APLICA' && item.estadoRegistrado !== 'NO_APLICA_MANUAL'
  const indexados = documentos.filter((d) => d.estadoIndexacion === 'INDEXADO')

  const accion = async (cuerpo: Record<string, unknown>) => {
    setEnviando(true)
    onError(null)
    try {
      await api(`/negocios/${negocioId}/requisitos/${item.id}`, { method: 'PUT', body: JSON.stringify(cuerpo) })
      await onCambio()
    } catch (e) {
      onError(e instanceof ErrorApi ? e.message : 'No se pudo guardar.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <li className={`py-4 ${noAplicaPorReglas ? 'opacity-60' : ''}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="font-medium text-tinta">
            {item.nombre}
            {!item.obligatorio && <span className="ml-2 text-xs font-normal text-niebla">(recomendado)</span>}
          </div>
          <p className="mt-0.5 text-sm text-grafito">{item.detalle}</p>
          {item.estado === 'SUGERIDO' && item.sugeridoPor === 'PROPIETARIO' && (
            <p className="mt-1 inline-block border-l-2 border-cobalto bg-cobalto-suave px-2 py-0.5 text-xs text-prusia">
              Lo envió el propietario desde su portal: revísalo y confírmalo
            </p>
          )}
          {item.documentoNombre && <p className="mt-0.5 text-xs text-niebla">Documento: {item.documentoNombre}</p>}
          {item.recibidoPor && item.estadoRegistrado === 'RECIBIDO' && (
            <p className="mt-0.5 text-xs text-niebla">Lo confirmó {item.recibidoPor}.</p>
          )}
        </div>
        <EstadoRequisitoChip estado={item.estado}
          etiqueta={item.estado === 'SUGERIDO' && item.sugeridoPor === 'PROPIETARIO' ? 'Enviado por el propietario' : undefined} />
      </div>

      {!noAplicaPorReglas && (
        <div className="mt-3 flex flex-wrap gap-2">
          {item.estadoRegistrado !== 'RECIBIDO' && (
            <button onClick={onAbrir} className={claseBotonSecundario}>
              {item.estado === 'SUGERIDO' ? 'Revisar y confirmar' : 'Marcar recibido'}
            </button>
          )}
          {item.estadoRegistrado === 'RECIBIDO' && (
            <button onClick={onAbrir} className={claseBotonSecundario}>Corregir fecha o documento</button>
          )}
          {item.estadoRegistrado !== 'NO_APLICA_MANUAL' && item.estadoRegistrado !== 'RECIBIDO' && (
            <button disabled={enviando} onClick={() => void accion({ accion: 'NO_APLICA' })} className={claseBotonSecundario}>No aplica</button>
          )}
          {item.estadoRegistrado !== 'PENDIENTE' && (
            <button disabled={enviando} onClick={() => void accion({ accion: 'REABRIR' })} className={claseBotonSecundario}>Reabrir</button>
          )}
        </div>
      )}

      {abierto && (
        <div className="mt-3 space-y-3 rounded-control border border-linea bg-papel p-4">
          {item.estado === 'SUGERIDO' && (
            <Aviso tipo="info">La IA reconoció este documento y propuso la fecha. Confírmala: solo tú puedes darlo por recibido.</Aviso>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
            <Campo etiqueta="Documento (opcional)">
              {(idc) => (
                <select id={idc} value={documentoId} onChange={(e) => setDocumentoId(e.target.value)} className={claseInput}>
                  <option value="">Sin documento adjunto</option>
                  {indexados.map((d) => <option key={d.id} value={d.id}>{d.nombreArchivo}</option>)}
                </select>
              )}
            </Campo>
            {item.requiereFecha && (
              <Campo etiqueta="Fecha de expedición" ayuda="Con ella Predia sabe hasta cuándo sirve el documento.">
                {(idc) => <input id={idc} type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} required className={claseInput} />}
              </Campo>
            )}
          </div>
          <button disabled={enviando || (item.requiereFecha && !fecha && !documentoId)}
            onClick={() => void accion({ accion: 'RECIBIR', fechaExpedicion: fecha || null, documentoId: documentoId ? Number(documentoId) : null })}
            className={claseBotonPrimario}>
            {enviando ? 'Guardando…' : 'Confirmar como recibido'}
          </button>
        </div>
      )}
    </li>
  )
}
