import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { DetallePortal, DocumentoPortal, NegocioPortal, RecordatorioPortal } from '../api/tipos'
import { CotaAvance } from '../componentes/Cota'
import { Aviso, claseBotonPrimario, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { FotoDeInmueble } from '../componentes/FotoInmueble'
import { Icono } from '../componentes/Iconos'
import { EstadoRequisitoChip } from '../componentes/Semaforo'
import { Terreno } from '../componentes/Terreno'
import { formatoFecha, formatoFechaHora } from '../formato'
import { useSesion } from '../sesion/Sesion'

/** Primero lo que hay que hacer, luego lo que está en revisión y al final lo recibido. */
const prioridad = (d: DocumentoPortal) =>
  d.estado === 'VENCIDO' ? 0 : d.estado === 'PENDIENTE' ? 1 : d.estado === 'POR_VENCER' ? 2 : d.estado === 'SUGERIDO' ? 3 : 4

/**
 * El portal del propietario que vende: su venta, qué documentos faltan, cuáles vencieron, cómo conseguirlos y el envío
 * de cada uno. Lo que envía queda en revisión hasta que su asesor lo confirma. Se le trata de «usted».
 */
export function Portal() {
  const { usuario, cerrar } = useSesion()
  const [negocios, setNegocios] = useState<NegocioPortal[] | null>(null)
  const [elegido, setElegido] = useState<number | null>(null)
  const [detalle, setDetalle] = useState<DetallePortal | null>(null)
  const [mensajes, setMensajes] = useState<RecordatorioPortal[]>([])
  const [error, setError] = useState<string | null>(null)
  const [confirmacion, setConfirmacion] = useState<string | null>(null)

  useEffect(() => {
    api<NegocioPortal[]>('/portal/negocios').then((l) => {
      setNegocios(l)
      if (l.length > 0) setElegido(l[0].id)
    }).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar su información.'))
  }, [])

  const cargar = useCallback(async (id: number) => {
    try {
      const [d, m] = await Promise.all([
        api<DetallePortal>(`/portal/negocios/${id}`),
        api<RecordatorioPortal[]>(`/portal/negocios/${id}/recordatorios`),
      ])
      setDetalle(d); setMensajes(m)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar la venta.')
    }
  }, [])

  useEffect(() => { if (elegido != null) void cargar(elegido) }, [elegido, cargar])

  const n = detalle?.negocio
  const documentos = [...(detalle?.documentos ?? [])].sort((a, b) => prioridad(a) - prioridad(b))

  return (
    <div className="min-h-dvh bg-papel">
      <header className="sticky top-0 z-20 flex items-center justify-between gap-3 bg-prusia px-4 pb-3 pt-[max(0.75rem,env(safe-area-inset-top))] text-white md:px-8">
        <div className="min-w-0">
          <span className="ancha text-xl font-bold tracking-tight">Predia</span>
          <span className="ml-3 hidden text-sm text-trazo sm:inline">{usuario?.inmobiliaria}</span>
        </div>
        <div className="flex items-center gap-3 text-sm">
          <span className="hidden truncate text-white/80 sm:inline">{usuario?.nombre}</span>
          <button onClick={cerrar} className="flex items-center gap-1.5 rounded-control px-2 py-1.5 text-white/80 hover:bg-white/10 hover:text-white">
            <Icono nombre="salir" className="size-4" /> Salir
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 pb-16 pt-6 md:px-6 md:pt-10">
        {error && <Aviso tipo="error">{error}</Aviso>}
        {negocios && negocios.length === 0 && (
          <div className="py-16 text-center">
            <h1 className="ancha text-3xl font-semibold">Hola, {usuario?.nombre.split(' ')[0]}</h1>
            <p className="mt-2 text-niebla">Todavía no tiene ventas activas con {usuario?.inmobiliaria}. Cuando su asesor abra una, aparecerá aquí.</p>
          </div>
        )}

        {negocios && negocios.length > 1 && (
          <div role="tablist" aria-label="Sus ventas" className="mb-5 flex flex-wrap gap-2">
            {negocios.map((x) => (
              <button key={x.id} role="tab" aria-selected={x.id === elegido} onClick={() => setElegido(x.id)}
                className={`rounded-full border px-3.5 py-1.5 text-sm ${x.id === elegido ? 'border-tinta bg-tinta text-white' : 'border-linea bg-white text-tinta'}`}>
                {x.inmueble}
              </button>
            ))}
          </div>
        )}

        {n && detalle && (
          <>
            <section className="relative isolate overflow-hidden rounded-panel bg-prusia px-5 pb-6 pt-5 text-white md:px-8 md:pb-8 md:pt-7" aria-label="Su venta">
              <Terreno semilla={n.inmuebleId * 31 + 7} trazar niveles={12} className="absolute inset-0 -z-20 size-full text-trazo opacity-40" />
              <FotoDeInmueble inmuebleId={n.inmuebleId} foto={n.foto} tam="grande" titulo={n.inmueble} base={`/portal/negocios/${n.id}/foto`} />
              {n.foto && <div className="absolute inset-0 -z-10 bg-gradient-to-t from-prusia via-prusia/85 to-prusia/30" aria-hidden="true" />}
              <p className="rotulo text-sm text-trazo">Venta de</p>
              <h1 className="ancha mt-1 text-[30px] font-semibold leading-[1.05] text-white md:text-5xl">{n.inmueble}</h1>
              <p className="mt-1 text-white/75">{n.barrio}, {n.ciudad}</p>
              <p className="ancha mt-6 text-2xl font-semibold md:text-3xl">
                {n.estado === 'FIRMADO' ? 'Su venta ya se firmó'
                  : n.listoParaFirmar ? <span className="text-[#9be7b5]">Su venta está lista para firmar</span>
                    : `Le falta${n.faltan === 1 ? '' : 'n'} ${n.faltan} documento${n.faltan === 1 ? '' : 's'} para firmar`}
              </p>
              <div className="mt-4">
                <CotaAvance items={detalle.documentos} porcentaje={n.porcentajeAvance} tono="sobre-azul" />
              </div>
              <dl className="mt-6 grid grid-cols-2 gap-x-6 text-sm">
                <div className="border-t border-white/25 py-2.5">
                  <dt className="rotulo text-trazo">Firma prevista</dt>
                  <dd className="mt-0.5 font-medium">{n.fechaFirma ? formatoFecha(n.fechaFirma) : 'Por definir'}</dd>
                </div>
                <div className="border-t border-white/25 py-2.5">
                  <dt className="rotulo text-trazo">Su asesor</dt>
                  <dd className="mt-0.5 font-medium [overflow-wrap:anywhere]">
                    {n.asesorNombre ?? '—'}
                    {n.asesorEmail && <a href={`mailto:${n.asesorEmail}`} className="block text-xs font-normal text-trazo underline">{n.asesorEmail}</a>}
                  </dd>
                </div>
              </dl>
            </section>

            {confirmacion && <div className="mt-4"><Aviso tipo="info">{confirmacion}</Aviso></div>}

            <section className="mt-8" aria-labelledby="titulo-documentos">
              <h2 id="titulo-documentos" className="ancha text-xl font-semibold">Sus documentos</h2>
              <p className="mt-1 text-sm text-niebla">Lo que su inmobiliaria necesita para la firma. Puede enviar cada documento en PDF desde aquí.</p>
              <ul className="mt-4 divide-y divide-linea border-y border-linea">
                {documentos.map((d) => (
                  <FilaDocumento key={d.id} documento={d} negocioId={n.id}
                    onEnviado={async (archivo) => {
                      setConfirmacion(`Recibimos «${archivo}». Su asesor lo revisará y le confirmará.`)
                      await cargar(n.id)
                    }} />
                ))}
              </ul>
              <p className="mt-3 text-xs text-niebla">{detalle.aviso}</p>
            </section>

            <section className="mt-10" aria-labelledby="titulo-mensajes">
              <h2 id="titulo-mensajes" className="ancha text-xl font-semibold">Mensajes de su inmobiliaria</h2>
              {mensajes.length === 0 && <p className="mt-2 text-sm text-niebla">Todavía no le han enviado recordatorios.</p>}
              <ul className="mt-3 space-y-2">
                {mensajes.map((m) => (
                  <li key={`${m.enviadoEn}-${m.asunto}`}>
                    <details className="rounded-control border border-linea bg-white px-4 py-3">
                      <summary className="cursor-pointer text-sm">
                        <span className="font-medium text-tinta">{m.asunto}</span>
                        <span className="ml-2 text-xs text-niebla">{formatoFechaHora(m.enviadoEn)}</span>
                      </summary>
                      <p className="mt-3 whitespace-pre-line text-sm text-grafito [overflow-wrap:anywhere]">{m.cuerpo}</p>
                    </details>
                  </li>
                ))}
              </ul>
            </section>
          </>
        )}
      </main>
    </div>
  )
}

function FilaDocumento({ documento: d, negocioId, onEnviado }: {
  documento: DocumentoPortal
  negocioId: number
  onEnviado: (archivo: string) => Promise<void>
}) {
  const [abierto, setAbierto] = useState(false)
  const [fecha, setFecha] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const archivo = useRef<HTMLInputElement>(null)
  const hoy = new Date().toLocaleDateString('en-CA')

  const enviar = async () => {
    const f = archivo.current?.files?.[0]
    if (!f) { setError('Elija el archivo PDF.'); return }
    setEnviando(true); setError(null)
    const datos = new FormData()
    datos.append('archivo', f)
    if (fecha) datos.append('fechaExpedicion', fecha)
    try {
      await api(`/portal/negocios/${negocioId}/requisitos/${d.id}/documento`, { method: 'POST', body: datos })
      setAbierto(false); setFecha('')
      await onEnviado(f.name)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo enviar el documento.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <li className="py-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="font-medium text-tinta">
            {d.nombre}{!d.obligatorio && <span className="ml-2 text-xs font-normal text-niebla">(recomendado)</span>}
          </div>
          <p className="mt-0.5 text-sm text-grafito">
            {d.enviadoPorUsted ? 'Usted lo envió. Su asesor lo está revisando.' : d.detalle}
          </p>
          {d.descripcion && d.estado !== 'RECIBIDO' && (
            <p className="mt-1 text-xs text-niebla">{d.descripcion}</p>
          )}
        </div>
        <EstadoRequisitoChip estado={d.enviadoPorUsted ? 'SUGERIDO' : d.estado}
          etiqueta={d.enviadoPorUsted || d.estado === 'SUGERIDO' ? 'En revisión' : undefined} />
      </div>

      {d.puedeEnviar && !abierto && (
        <button onClick={() => setAbierto(true)} className={`${claseBotonSecundario} mt-3`}>
          {d.enviadoPorUsted ? 'Enviar otro archivo' : 'Enviar documento'}
        </button>
      )}
      {abierto && (
        <div className="mt-3 grid gap-3 rounded-control border border-linea bg-white p-3 sm:grid-cols-[1fr_auto]">
          <div className="space-y-3">
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-tinta">Archivo PDF</span>
              <input ref={archivo} type="file" accept="application/pdf" className="block w-full text-sm" />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-tinta">Fecha de expedición <span className="font-normal text-niebla">(si aparece en el documento)</span></span>
              <input type="date" max={hoy} value={fecha} onChange={(e) => setFecha(e.target.value)} className={claseInput} />
            </label>
          </div>
          <div className="flex items-end gap-2">
            <button onClick={() => void enviar()} disabled={enviando} className={claseBotonPrimario}>{enviando ? 'Enviando…' : 'Enviar'}</button>
            <button onClick={() => { setAbierto(false); setError(null) }} disabled={enviando} className={claseBotonSecundario}>Cancelar</button>
          </div>
          {error && <p role="alert" className="text-sm text-red-700 sm:col-span-2">{error}</p>}
        </div>
      )}
    </li>
  )
}
