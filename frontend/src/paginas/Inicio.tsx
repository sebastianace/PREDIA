import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/cliente'
import type { EstadoInmueble as Estado, Inmueble, ResumenDiario } from '../api/tipos'
import { estilosEstado } from '../componentes/EstadoInmueble'
import { Semaforo } from '../componentes/Semaforo'
import { formatoFecha, formatoPesos } from '../formato'
import { useSesion } from '../sesion/Sesion'

const ordenEstados: Estado[] = ['DISPONIBLE', 'SEPARADO', 'ARRENDADO', 'VENDIDO']

const saludo = () => {
  const hora = new Date().getHours()
  return hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches'
}

const fecha = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())
const hoy = fecha.charAt(0).toUpperCase() + fecha.slice(1)

const plurales: Record<Estado, [string, string]> = {
  DISPONIBLE: ['disponible', 'disponibles'],
  SEPARADO: ['separado', 'separados'],
  ARRENDADO: ['arrendado', 'arrendados'],
  VENDIDO: ['vendido', 'vendidos'],
}

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
    <div className="mx-auto max-w-3xl">
      <p className="text-sm text-niebla">{hoy}</p>
      <h1 className="ancha mt-1 text-[30px] font-semibold leading-tight md:text-4xl">
        {saludo()}, {usuario?.nombre.split(' ')[0]}
      </h1>

      <section className="mt-8 rounded-panel border border-linea bg-white p-5 md:p-6" aria-labelledby="titulo-inventario">
        <div className="flex items-baseline justify-between gap-4">
          <h2 id="titulo-inventario" className="text-lg font-semibold">Tu inventario hoy</h2>
          <Link to="/inventario" className="text-sm font-medium text-cobalto hover:text-cobalto-hondo">Ver inventario</Link>
        </div>

        {/* Cada segmento es un inmueble, coloreado por su estado */}
        <div className="mt-5 flex h-3 gap-1" role="img"
          aria-label={ordenEstados.map((e) => `${conteo(e)} ${plurales[e][conteo(e) === 1 ? 0 : 1]}`).join(', ')}>
          {inmuebles === null && <div className="flex-1 animate-pulse rounded-sm bg-linea" />}
          {ordenados.map((i) => (
            <div key={i.id} title={`${i.titulo}: ${estilosEstado[i.estado].nombre.toLowerCase()}`}
              className={`flex-1 rounded-sm ${estilosEstado[i.estado].barra}`} />
          ))}
        </div>

        <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
          {ordenEstados.map((e) => (
            <div key={e} className="flex items-baseline gap-2">
              <dt className="order-2 text-sm text-niebla">{plurales[e][conteo(e) === 1 ? 0 : 1]}</dt>
              <dd className={`ancha cifra order-1 text-xl font-semibold ${conteo(e) ? 'text-tinta' : 'text-linea'}`}>{conteo(e)}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className="mt-10" aria-labelledby="titulo-ofrecer">
        <h2 id="titulo-ofrecer" className="text-lg font-semibold">Para ofrecer</h2>
        <p className="text-sm text-niebla">Inmuebles disponibles, del más económico al más costoso.</p>
        <ul className="mt-4 divide-y divide-linea border-y border-linea">
          {[...disponibles]
            .sort((a, b) => (a.precioVenta ?? a.canonArriendo ?? 0) - (b.precioVenta ?? b.canonArriendo ?? 0))
            .map((i) => (
              <li key={i.id} className="flex items-center justify-between gap-4 py-4">
                <div className="min-w-0">
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

      <section className="mt-10" aria-labelledby="titulo-cierres">
        <div className="flex items-baseline justify-between gap-4">
          <h2 id="titulo-cierres" className="text-lg font-semibold">Cierres de negocio</h2>
          <Link to="/negocios" className="text-sm font-medium text-cobalto hover:text-cobalto-hondo">Ver negocios</Link>
        </div>

        {resumen && resumen.bloqueados.length === 0 && (
          <p className="mt-3 text-sm text-niebla">
            {resumen.negociosAbiertos === 0 ? 'No tienes negocios abiertos.' : 'Todos tus negocios abiertos están listos para firmar.'}
          </p>
        )}
        <ul className="mt-3 space-y-3">
          {resumen?.bloqueados.map((b) => (
            <li key={b.negocioId}>
              <Link to={`/negocios/${b.negocioId}`} className="block rounded-panel border border-linea bg-white p-4 hover:border-niebla">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="truncate font-medium text-tinta">{b.inmueble}</div>
                    <div className="truncate text-sm text-niebla">Vende {b.propietario}{b.fechaFirma && ` · firma ${formatoFecha(b.fechaFirma)}`}</div>
                  </div>
                  <Semaforo listo={false} texto={b.semaforo} />
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
          <Link to="/recordatorios" className="mt-6 flex items-center justify-between gap-3 rounded-panel border border-cobalto/30 bg-cobalto-suave px-4 py-3 text-sm text-prusia">
            <span><strong>{resumen.recordatoriosPorAprobar}</strong> recordatorio{resumen.recordatoriosPorAprobar === 1 ? '' : 's'} por aprobar</span>
            <span className="font-medium">Revisar</span>
          </Link>
        )}
      </section>
    </div>
  )
}
