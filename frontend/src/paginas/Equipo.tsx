import { useEffect, useMemo, useState } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { Periodo, Rendimiento, RendimientoEquipo } from '../api/tipos'
import { Avatar } from '../componentes/Avatar'
import { Aviso, Segmentado } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { PanelAzul } from '../componentes/PanelAzul'
import { formatoPesos } from '../formato'
import { periodos } from './Perfil'

type Metrica = 'ventas' | 'valor' | 'dias'

const metricas: { valor: Metrica; texto: string }[] = [
  { valor: 'ventas', texto: 'Ventas' }, { valor: 'valor', texto: 'Valor vendido' }, { valor: 'dias', texto: 'Días de cierre' },
]

const medida = (r: Rendimiento, m: Metrica) => m === 'ventas' ? r.vendidos : m === 'valor' ? r.valorVendido : (r.diasPromedioCierre ?? 0)

/** «Equipo» (solo administrador): compara el rendimiento de los asesores, calculado desde los cierres confirmados. */
export function Equipo() {
  const [periodo, setPeriodo] = useState<Periodo>('ANIO')
  const [metrica, setMetrica] = useState<Metrica>('ventas')
  const [datos, setDatos] = useState<RendimientoEquipo | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setError(null)
    api<RendimientoEquipo>(`/equipo/rendimiento?periodo=${periodo}`).then(setDatos)
      .catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo cargar el equipo.'))
  }, [periodo])

  const filas = useMemo(() => {
    const lista = [...(datos?.asesores ?? [])]
    // En «días de cierre» menos es mejor: quien no tiene cierres va al final.
    lista.sort((a, b) => metrica === 'dias'
      ? (a.diasPromedioCierre ?? Infinity) - (b.diasPromedioCierre ?? Infinity)
      : medida(b, metrica) - medida(a, metrica) || b.vendidos - a.vendidos)
    return lista
  }, [datos, metrica])
  const maximo = Math.max(1, ...filas.map((r) => medida(r, metrica)))

  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="ancha text-[32px] font-semibold leading-[1.05] md:text-5xl">Equipo</h1>
          <p className="mt-1 text-niebla">Compara el rendimiento de los asesores.</p>
        </div>
        <Segmentado etiqueta="Periodo" ocultarEtiqueta opciones={periodos} valor={periodo} onChange={setPeriodo} />
      </div>

      {error && <div className="mt-6"><Aviso tipo="error">{error}</Aviso></div>}
      {!datos && !error && <p className="mt-6 text-niebla">Cargando…</p>}

      {datos && (
        <>
          <PanelAzul semilla={959} ancho={1000} alto={200} niveles={9} className="mt-5 rounded-panel px-5 py-4 md:px-6">
            <dl className="relative grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div><dt className="rotulo text-sm text-trazo">Vendidos por el equipo</dt><dd className="ancha cifra text-3xl font-semibold">{datos.totales.vendidos}</dd></div>
              <div><dt className="rotulo text-sm text-trazo">Valor vendido</dt><dd className="ancha cifra whitespace-nowrap text-2xl font-semibold sm:pt-1">{formatoPesos(datos.totales.valorVendido)}</dd></div>
              <div><dt className="rotulo text-sm text-trazo">Asesores activos</dt><dd className="ancha cifra text-3xl font-semibold">{datos.totales.asesores}</dd></div>
            </dl>
          </PanelAzul>

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <span className="text-sm text-niebla">Ordenar y comparar por</span>
            <Segmentado etiqueta="Comparar por" ocultarEtiqueta opciones={metricas} valor={metrica} onChange={setMetrica} />
          </div>

          <ol className="mt-4 space-y-3">
            {filas.map((r, i) => {
              const v = medida(r, metrica)
              const lider = i === 0 && (metrica === 'dias' ? r.diasPromedioCierre != null : v > 0)
              return (
                <li key={r.usuarioId} className="rounded-panel border border-linea bg-white p-4">
                  <div className="grid grid-cols-[1.75rem_auto_minmax(0,1fr)] items-center gap-x-3 gap-y-1 sm:gap-x-4">
                    <span className={`ancha text-xl font-semibold ${i === 0 ? 'text-prusia' : 'text-niebla'}`}>{i + 1}</span>
                    <Avatar nombre={r.nombre} tamano="enorme" foto={{ ruta: `/perfil/usuarios/${r.usuarioId}/foto`, version: r.fotoVersion }} />
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                        <span className="flex min-w-0 flex-wrap items-center gap-2 font-semibold text-tinta">
                          <span className="truncate">{r.nombre}</span>
                          {lider && <span className="inline-flex items-center gap-1 rounded-full bg-cobalto-suave px-2 py-0.5 text-xs font-medium text-cobalto-hondo"><Icono nombre="circuloCheck" className="size-3.5" />Líder</span>}
                        </span>
                        <span className="ancha cifra whitespace-nowrap font-semibold text-prusia">
                          {metrica === 'ventas' ? `${r.vendidos} ${r.vendidos === 1 ? 'vendido' : 'vendidos'}` : metrica === 'valor' ? formatoPesos(r.valorVendido)
                            : r.diasPromedioCierre == null ? 'Sin cierres' : `${r.diasPromedioCierre} días`}
                        </span>
                      </div>
                      <div className="my-2 h-2 rounded bg-cobalto-suave" role="img" aria-label={`${r.nombre}: ${Math.round((v / maximo) * 100)} % del mejor`}>
                        <div className="h-2 rounded bg-prusia" style={{ width: `${Math.round((v / maximo) * 100)}%` }} />
                      </div>
                      <dl className="flex flex-wrap gap-x-5 gap-y-0.5 text-xs text-niebla">
                        <div><dt className="sr-only">Vendidos</dt><dd>{r.vendidos} {r.vendidos === 1 ? 'vendido' : 'vendidos'}</dd></div>
                        <div><dt className="sr-only">Valor vendido</dt><dd>{formatoPesos(r.valorVendido)}</dd></div>
                        <div><dt className="sr-only">Abiertos</dt><dd>{r.abiertos} {r.abiertos === 1 ? 'abierto' : 'abiertos'}</dd></div>
                        <div><dt className="sr-only">Promedio de cierre</dt><dd>{r.diasPromedioCierre == null ? 'sin cierres' : `${r.diasPromedioCierre} ${r.diasPromedioCierre === 1 ? 'día' : 'días'} promedio`}</dd></div>
                        <div><dt className="sr-only">Tasa de cierre</dt><dd>tasa de cierre {r.tasaDeCierre == null ? '—' : `${r.tasaDeCierre} %`}</dd></div>
                      </dl>
                    </div>
                  </div>
                </li>
              )
            })}
            {filas.length === 0 && <li className="rounded-panel border border-dashed border-linea bg-white px-5 py-8 text-sm text-niebla">Todavía no hay asesores.</li>}
          </ol>
          <p className="mt-4 text-xs text-niebla">
            Se cuentan los negocios que cada asesor abrió y se confirmaron como firmados; nadie puede escribir estas cifras a mano.
            «Días de cierre»: menos es mejor. Los datos de ejemplo de la demo están marcados como tales en su nota de cierre.
          </p>
        </>
      )}
    </div>
  )
}
