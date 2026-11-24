import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { InmuebleResumenComprador, InteresComprador, PerfilComprador, SolicitudDelComprador } from '../api/tipos'
import { CajaMensajeAsesor } from '../componentes/CajaMensajeAsesor'
import { AvisoTope } from '../componentes/AvisoTope'
import { CifraAcotada } from '../componentes/Cota'
import { EncabezadoComprador } from '../componentes/EncabezadoComprador'
import { Aviso } from '../componentes/Formulario'
import { FotoDeInmueble } from '../componentes/FotoInmueble'
import { Terreno } from '../componentes/Terreno'
import { formatoFechaHora, formatoPesos, formatoPorcentaje, formatoTasaEa } from '../formato'
import { useSesion } from '../sesion/Sesion'

/**
 * El inicio del comprador: su presupuesto (con la razón de cada cifra) y los inmuebles que su asesor le compartió.
 * Se le trata de «usted». Las cifras las calcula el servidor; aquí solo se muestran.
 */
export function Comprador() {
  const { usuario } = useSesion()
  const [perfil, setPerfil] = useState<PerfilComprador | null>(null)
  const [inmuebles, setInmuebles] = useState<InmuebleResumenComprador[] | null>(null)
  const [solicitudes, setSolicitudes] = useState<SolicitudDelComprador[]>([])
  const [error, setError] = useState<string | null>(null)

  const cargar = useCallback(() => {
    api<PerfilComprador>('/comprador/perfil').then(setPerfil).catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar su información.'))
    api<InmuebleResumenComprador[]>('/comprador/inmuebles').then(setInmuebles).catch(() => setInmuebles([]))
    api<SolicitudDelComprador[]>('/comprador/solicitudes').then(setSolicitudes).catch(() => setSolicitudes([]))
  }, [])
  useEffect(() => { cargar() }, [cargar])

  const marcar = async (i: InmuebleResumenComprador, interes: InteresComprador) => {
    // Pulsar de nuevo la opción activa la quita.
    const nuevo = i.interes === interes ? null : interes
    try {
      await api(`/comprador/inmuebles/${i.id}/interes`, { method: 'PUT', body: JSON.stringify({ interes: nuevo }) })
      setInmuebles((lista) => lista?.map((x) => x.id === i.id ? { ...x, interes: nuevo } : x) ?? null)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo guardar su opinión.')
    }
  }

  const p = perfil?.presupuesto
  return (
    <div className="min-h-dvh bg-papel">
      <EncabezadoComprador />
      <main className="mx-auto max-w-4xl px-4 pb-16 pt-6 md:px-6 md:pt-10">
        {error && <div className="mb-4"><Aviso tipo="error">{error}</Aviso></div>}

        <section className="relative isolate overflow-hidden rounded-panel bg-prusia px-5 pb-6 pt-5 text-white md:px-9 md:pb-9 md:pt-8" aria-label="Su presupuesto">
          <Terreno semilla={usuario ? usuario.id * 13 + 5 : 11} trazar niveles={14} className="absolute inset-0 -z-10 size-full text-trazo opacity-40" />
          <p className="rotulo text-sm text-trazo">Su búsqueda con {usuario?.inmobiliaria}</p>
          <h1 className="ancha mt-1 text-[32px] font-semibold leading-[1.05] text-white md:text-5xl">Hola, {usuario?.nombre.split(' ')[0]}</h1>
          {p ? (
            <>
              <div className="mt-8">
                <CifraAcotada tono="sobre-azul" grande etiqueta="Puede comprar hasta" valor={formatoPesos(p.topeDeCompra)}
                  razon={p.limitante === 'AHORRO'
                    ? `Lo limita su ahorro: ${formatoPesos(p.ahorro)} es la cuota inicial del ${formatoPorcentaje(p.pctCuotaInicial, 0)} que se exige.`
                    : 'Lo limita su capacidad de pago: la cuota máxima mensual no alcanza para financiar más.'} />
              </div>
              <dl className="mt-6 grid grid-cols-1 gap-x-8 text-sm sm:grid-cols-2">
                <div className="flex justify-between gap-3 border-t border-white/25 py-2.5">
                  <dt className="text-trazo">Cuota máxima al mes ({formatoPorcentaje(p.pctCuotaMaxima, 0)} de su ingreso)</dt>
                  <dd className="cifra font-medium">{formatoPesos(p.cuotaMaxima)}</dd>
                </div>
                <div className="flex justify-between gap-3 border-t border-white/25 py-2.5">
                  <dt className="text-trazo">Plazo de referencia</dt>
                  <dd className="cifra font-medium">{p.plazoAnios} años</dd>
                </div>
              </dl>
              <p className="mt-3 text-xs text-white/60">
                Calculado con la tasa de {p.tasa.banco} ({formatoTasaEa(p.tasa.tasaEa)}){p.tasa.esEjemplo && ', una tasa de ejemplo'}.
                Es una estimación: el banco decide el crédito real.
              </p>
            </>
          ) : (
            <p className="mt-4 max-w-xl text-white/80">
              {perfil ? 'Su asesor aún está preparando su presupuesto. Mientras tanto, puede ver los inmuebles que le comparta.' : 'Cargando…'}
            </p>
          )}
        </section>

        <section className="mt-10" aria-labelledby="titulo-inmuebles">
          <h2 id="titulo-inmuebles" className="ancha text-2xl font-semibold">Inmuebles para usted</h2>
          <p className="mt-1 text-sm text-niebla">Los que {perfil?.asesor?.nombre ?? 'su asesor'} eligió pensando en su búsqueda.</p>
          {inmuebles && inmuebles.length === 0 && (
            <p className="mt-6 border-y border-linea py-8 text-center text-niebla">
              Todavía no le han compartido inmuebles. Cuando su asesor le comparta uno, lo verá aquí.
            </p>
          )}
          <ul className="mt-5 grid grid-cols-1 gap-5 md:grid-cols-2">
            {inmuebles?.map((i) => (
              <li key={i.id} className="overflow-hidden rounded-panel border border-linea bg-white">
                <Link to={`/comprador/inmuebles/${i.id}`} className="block">
                  <div className="relative isolate aspect-[16/10] overflow-hidden bg-white">
                    <Terreno semilla={i.id * 31 + 7} niveles={9} grosor={2} className="absolute inset-0 -z-20 size-full text-cobalto/60" />
                    <FotoDeInmueble inmuebleId={i.id} foto={i.foto} tam="grande" titulo={i.titulo} base={`/comprador/inmuebles/${i.id}/foto`} />
                  </div>
                  <div className="px-5 pt-4">
                    <h3 className="truncate font-semibold text-tinta">{i.titulo}</h3>
                    <p className="truncate text-sm text-niebla">{i.barrio}, {i.ciudad} · {i.habitaciones} hab · {i.areaM2} m²</p>
                    <p className="ancha cifra mt-2 text-2xl font-semibold text-tinta">{formatoPesos(i.precio)}</p>
                    {!i.disponible && <p className="mt-1 text-sm font-medium text-sello">Este inmueble ya no está disponible.</p>}
                    {i.disponible && i.excedenteSobreTope != null && <AvisoTope excedente={i.excedenteSobreTope} />}
                    {i.nota && <p className="mt-2 border-l-2 border-cobalto pl-3 text-sm text-grafito [overflow-wrap:anywhere]">{i.nota}</p>}
                  </div>
                </Link>
                <div className="flex flex-wrap gap-2 px-5 pb-5 pt-4">
                  {(['ME_INTERESA', 'NO_ME_INTERESA'] as const).map((op) => (
                    <button key={op} onClick={() => void marcar(i, op)} aria-pressed={i.interes === op}
                      className={`rounded-full border px-3.5 py-1.5 text-sm ${i.interes === op
                        ? (op === 'ME_INTERESA' ? 'border-cobalto bg-cobalto-suave text-cobalto-hondo' : 'border-tinta bg-tinta text-white')
                        : 'border-linea bg-white text-tinta hover:border-niebla'}`}>
                      {op === 'ME_INTERESA' ? 'Me interesa' : 'No me interesa'}
                    </button>
                  ))}
                  <Link to={`/comprador/inmuebles/${i.id}`} className="ml-auto self-center text-sm font-medium text-cobalto hover:text-cobalto-hondo">Ver y simular</Link>
                </div>
              </li>
            ))}
          </ul>
        </section>

        <section className="mt-12" aria-labelledby="titulo-asesor">
          <h2 id="titulo-asesor" className="ancha text-2xl font-semibold">Su asesor</h2>
          {perfil?.asesor && (
            <p className="mt-1 text-grafito">
              {perfil.asesor.nombre} · <a href={`mailto:${perfil.asesor.email}`} className="text-cobalto underline [overflow-wrap:anywhere]">{perfil.asesor.email}</a>
            </p>
          )}
          <div className="mt-4"><CajaMensajeAsesor inmuebleId={null} etiqueta="Escribir a mi asesor" onEnviado={() => cargar()} /></div>
          {solicitudes.length > 0 && (
            <ul className="mt-6 divide-y divide-linea border-y border-linea">
              {solicitudes.map((s) => (
                <li key={s.id} className="py-3 text-sm">
                  <div className="flex items-start justify-between gap-3">
                    <p className="min-w-0 text-grafito [overflow-wrap:anywhere]">{s.mensaje}</p>
                    <span className={`shrink-0 text-xs font-medium ${s.atendida ? 'text-emerald-700' : 'text-ambar'}`}>{s.atendida ? 'Atendido' : 'Esperando respuesta'}</span>
                  </div>
                  <p className="mt-1 text-xs text-niebla">{formatoFechaHora(s.creadaEn)}{s.inmuebleTitulo && ` · ${s.inmuebleTitulo}`}</p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  )
}
