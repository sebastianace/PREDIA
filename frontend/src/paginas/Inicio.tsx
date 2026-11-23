import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { EstadoInmueble as Estado, Inmueble, ResumenDiario } from '../api/tipos'
import { CifraAcotada } from '../componentes/Cota'
import { Terreno } from '../componentes/Terreno'
import { formatoFecha, formatoPesos } from '../formato'
import { useSesion } from '../sesion/Sesion'

const ordenEstados: Estado[] = ['DISPONIBLE', 'SEPARADO', 'ARRENDADO', 'VENDIDO']

const saludo = () => {
  const hora = new Date().getHours()
  return hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches'
}

const fecha = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())
const hoy = fecha.charAt(0).toUpperCase() + fecha.slice(1)


export function Inicio() {
  const { usuario } = useSesion()
  const [inmuebles, setInmuebles] = useState<Inmueble[] | null>(null)
  const [resumen, setResumen] = useState<ResumenDiario | null>(null)

  useEffect(() => {
    api<Inmueble[]>('/inmuebles').then(setInmuebles).catch(() => setInmuebles([]))
    api<ResumenDiario>('/resumen-diario').then(setResumen).catch(() => setResumen(null))
  }, [])

  const ordenados = [...(inmuebles ?? [])].sort(
    (a, b) => ordenEstados.indexOf(a.estado) - ordenEstados.indexOf(b.estado))
  const disponibles = ordenados.filter((i) => i.estado === 'DISPONIBLE')
  const conteo = (e: Estado) => ordenados.filter((i) => i.estado === e).length

  return (
    <div className="mx-auto max-w-4xl">
      <header className="relative isolate overflow-hidden rounded-panel bg-prusia px-5 pb-7 pt-6 text-white md:px-9 md:pb-9 md:pt-8">
        <Terreno semilla={959} trazar niveles={14} className="absolute inset-0 -z-10 size-full text-trazo opacity-40" />
        <p className="rotulo text-sm text-trazo">{hoy}</p>
        <h1 className="ancha mt-1 text-[32px] font-semibold leading-[1.05] text-white md:text-5xl">
          {saludo()}, {usuario?.nombre.split(' ')[0]}
        </h1>
        <div className="mt-8 grid gap-6 md:grid-cols-3 md:gap-8">
          <CifraAcotada tono="sobre-azul" etiqueta="Inmuebles disponibles" valor={String(inmuebles ? disponibles.length : '—')}
            razon={inmuebles ? `${conteo('SEPARADO')} separados, ${conteo('ARRENDADO')} arrendados y ${conteo('VENDIDO')} vendidos` : undefined} />
          <CifraAcotada tono="sobre-azul" etiqueta="Negocios sin cerrar" valor={String(resumen ? resumen.bloqueados.length : '—')}
            razon={resumen ? `de ${resumen.negociosAbiertos} abiertos` : undefined} />
          <CifraAcotada tono="sobre-azul" etiqueta="Recordatorios por aprobar" valor={String(resumen ? resumen.recordatoriosPorAprobar : '—')}
            razon={resumen && resumen.recordatoriosPorAprobar > 0 ? 'Nada sale sin tu aprobación' : undefined} />
        </div>
      </header>

      <div className="mt-10 grid gap-10 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
        <section aria-labelledby="titulo-ofrecer">
          <div className="flex items-baseline justify-between gap-4">
            <h2 id="titulo-ofrecer" className="ancha text-xl font-semibold">Para ofrecer</h2>
            <Link to="/inventario" className="text-sm font-medium text-cobalto hover:text-cobalto-hondo">Ver inventario</Link>
          </div>
          <p className="mt-0.5 text-sm text-niebla">Del más económico al más costoso.</p>
          <ul className="mt-4 divide-y divide-linea border-y border-linea">
            {[...disponibles]
              .sort((a, b) => (a.precioVenta ?? a.canonArriendo ?? 0) - (b.precioVenta ?? b.canonArriendo ?? 0))
              .map((i) => (
                <li key={i.id} className="flex items-center gap-4 py-3.5">
                  <Terreno semilla={i.id * 31 + 7} ancho={120} alto={120} niveles={6} grosor={2.4}
                    className="size-12 shrink-0 rounded-sm bg-white text-cobalto" />
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-medium text-tinta">{i.titulo}</div>
                    <div className="truncate text-sm text-niebla">{i.barrio}, {i.ciudad}</div>
                  </div>
                  <div className="ancha cifra shrink-0 text-right font-semibold text-tinta">
                    {formatoPesos(i.operacion === 'VENTA' ? i.precioVenta : i.canonArriendo)}
                    {i.operacion === 'ARRIENDO' && <span className="block text-xs font-normal text-niebla">al mes</span>}
                  </div>
                </li>
              ))}
            {inmuebles && disponibles.length === 0 && (
              <li className="py-4 text-sm text-niebla">No hay inmuebles disponibles. Revisa el inventario para cambiar su estado.</li>
            )}
          </ul>
        </section>

        <section aria-labelledby="titulo-cierres">
          <div className="flex items-baseline justify-between gap-4">
            <h2 id="titulo-cierres" className="ancha text-xl font-semibold">Cierres de negocio</h2>
            <Link to="/negocios" className="text-sm font-medium text-cobalto hover:text-cobalto-hondo">Ver negocios</Link>
          </div>

          {resumen && resumen.bloqueados.length === 0 && (
            <p className="mt-3 text-sm text-niebla">
              {resumen.negociosAbiertos === 0 ? 'No tienes negocios abiertos.' : 'Todos tus negocios abiertos están listos para firmar.'}
            </p>
          )}
          <ul className="mt-4 divide-y divide-linea border-y border-linea">
            {resumen?.bloqueados.map((b) => (
              <li key={b.negocioId}>
                <Link to={`/negocios/${b.negocioId}`} className="block py-4 hover:bg-white/60">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="truncate font-medium text-tinta">{b.inmueble}</div>
                      <div className="truncate text-sm text-niebla">Vende {b.propietario}{b.fechaFirma && ` · firma ${formatoFecha(b.fechaFirma)}`}</div>
                    </div>
                    {b.vencidos > 0
                      ? <span className="shrink-0 -rotate-2 rounded-sm border-[1.5px] border-sello px-2 py-0.5 text-sm font-semibold text-sello">{b.vencidos} vencido{b.vencidos === 1 ? '' : 's'}</span>
                      : <span className="shrink-0 text-sm font-medium text-ambar">{b.semaforo}</span>}
                  </div>
                  <p className="mt-2 text-sm text-grafito">{b.pendientes.join(' · ')}</p>
                </Link>
              </li>
            ))}
          </ul>

          {resumen && resumen.vencenEstaSemana.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-semibold text-tinta">Vencen esta semana</h3>
              <ul className="mt-2 divide-y divide-linea border-y border-linea text-sm">
                {resumen.vencenEstaSemana.map((v) => (
                  <li key={`${v.negocioId}-${v.documento}`} className="flex items-center justify-between gap-3 py-3">
                    <span className="min-w-0 truncate text-grafito">{v.documento} · {v.inmueble}</span>
                    <span className="shrink-0 text-ambar">{v.diasRestantes === 0 ? 'vence hoy' : `en ${v.diasRestantes} día${v.diasRestantes === 1 ? '' : 's'}`}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {resumen && resumen.recordatoriosPorAprobar > 0 && (
            <Link to="/recordatorios" className="mt-6 flex items-center justify-between gap-3 border-l-2 border-cobalto bg-cobalto-suave px-4 py-3 text-sm text-prusia">
              <span><strong>{resumen.recordatoriosPorAprobar}</strong> recordatorio{resumen.recordatoriosPorAprobar === 1 ? '' : 's'} por aprobar</span>
              <span className="font-medium">Revisar</span>
            </Link>
          )}
        </section>
      </div>
    </div>
  )
}
