import { useEffect, useState } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { Requisito } from '../api/tipos'
import { Aviso, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { useSesion } from '../sesion/Sesion'

const cuando: Record<string, string> = {
  SIEMPRE: 'Siempre', SI_VALORIZACION: 'Si el municipio cobra valorización',
  SI_PROPIEDAD_HORIZONTAL: 'Si es propiedad horizontal', SI_HIPOTECA: 'Si tiene hipoteca vigente',
  SI_CASADO_O_UNION: 'Si el vendedor está casado o en unión marital', SI_APODERADO: 'Si actúa por apoderado',
  SI_PERSONA_JURIDICA: 'Si el vendedor es persona jurídica',
}
const vigencias: Record<string, string> = {
  NINGUNA: 'Sin vigencia', DIAS_DESDE_EXPEDICION: 'Días desde su expedición', ANIO_EN_CURSO: 'Del año en curso',
  MES_EN_CURSO: 'Del mes en curso',
}

/** Catálogo de documentos que se le piden al vendedor. El administrador lo ajusta a lo que exige su notaría. */
export function Requisitos() {
  const { usuario } = useSesion()
  const esAdmin = usuario?.rol === 'ADMIN'
  const [lista, setLista] = useState<Requisito[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => { api<Requisito[]>('/requisitos').then(setLista).catch((e) => setError(e.message)) }, [])

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">Requisitos del vendedor</h1>
      <p className="mt-1 text-niebla">
        Lista de referencia; confirma los requisitos con la notaría. Predia no da asesoría legal.
        {esAdmin ? ' Aquí puedes ajustarla.' : ' Solo el administrador puede modificarla.'}
      </p>
      {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}
      <ul className="mt-6 space-y-3">
        {lista?.map((r) => <Fila key={r.id} requisito={r} esAdmin={esAdmin} onGuardado={(n) => setLista((l) => l?.map((x) => (x.id === n.id ? n : x)) ?? null)} />)}
      </ul>
    </div>
  )
}

function Fila({ requisito: r, esAdmin, onGuardado }: { requisito: Requisito; esAdmin: boolean; onGuardado: (r: Requisito) => void }) {
  const [activo, setActivo] = useState(r.activo)
  const [obligatorio, setObligatorio] = useState(r.obligatorio)
  const [dias, setDias] = useState(r.vigenciaDias ?? 30)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const cambiado = activo !== r.activo || obligatorio !== r.obligatorio || (r.reglaVigencia === 'DIAS_DESDE_EXPEDICION' && dias !== r.vigenciaDias)

  const guardar = async () => {
    setGuardando(true); setError(null)
    try {
      onGuardado(await api<Requisito>(`/requisitos/${r.id}`, { method: 'PUT', body: JSON.stringify({
        nombre: r.nombre, descripcion: r.descripcion, orden: r.orden, activo, reglaAplica: r.reglaAplica,
        obligatorio, reglaVigencia: r.reglaVigencia, vigenciaDias: r.reglaVigencia === 'DIAS_DESDE_EXPEDICION' ? dias : null }) }))
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo guardar.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <li className={`rounded-panel border border-linea bg-white p-4 ${activo ? '' : 'opacity-60'}`}>
      <div className="font-medium text-tinta">{r.nombre}</div>
      <p className="mt-0.5 text-sm text-niebla">{r.descripcion}</p>
      <p className="mt-2 text-xs text-grafito">
        {cuando[r.reglaAplica]} · {vigencias[r.reglaVigencia]}{r.reglaVigencia === 'DIAS_DESDE_EXPEDICION' && ` (${r.vigenciaDias} días)`}
      </p>
      {esAdmin && (
        <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-grafito">
          <label className="flex items-center gap-2"><input type="checkbox" checked={activo} onChange={(e) => setActivo(e.target.checked)} className="size-4 accent-[var(--color-cobalto)]" />Activo</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={obligatorio} onChange={(e) => setObligatorio(e.target.checked)} className="size-4 accent-[var(--color-cobalto)]" />Obligatorio</label>
          {r.reglaVigencia === 'DIAS_DESDE_EXPEDICION' && (
            <label className="flex items-center gap-2">Vigencia
              <input type="number" min={1} max={365} value={dias} onChange={(e) => setDias(Number(e.target.value))} className={`${claseInput} !w-20 !py-1.5`} /> días
            </label>
          )}
          <button onClick={() => void guardar()} disabled={!cambiado || guardando} className={claseBotonSecundario}>{guardando ? 'Guardando…' : 'Guardar'}</button>
        </div>
      )}
      {error && <div className="mt-2"><Aviso tipo="error">{error}</Aviso></div>}
    </li>
  )
}
