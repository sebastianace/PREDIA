import { useEffect, useState, type FormEvent } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { Tasa, TipoTasa } from '../api/tipos'
import { Aviso, Campo, claseBotonPrimario, claseInput } from '../componentes/Formulario'
import { formatoTasaEa } from '../formato'
import { useSesion } from '../sesion/Sesion'

export function Tasas() {
  const { usuario } = useSesion()
  const esAdmin = usuario?.rol === 'ADMIN'
  const [tasas, setTasas] = useState<Tasa[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [banco, setBanco] = useState('')
  const [tipo, setTipo] = useState<TipoTasa>('NO_VIS')
  const [porcentaje, setPorcentaje] = useState('')
  const [desde, setDesde] = useState(new Date().toISOString().slice(0, 10))
  const [fuente, setFuente] = useState('')
  const [esEjemplo, setEsEjemplo] = useState(false)
  const [guardando, setGuardando] = useState(false)

  const cargar = () => api<Tasa[]>('/tasas').then(setTasas).catch((e) => setError(e.message))
  useEffect(() => { void cargar() }, [])

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setGuardando(true)
    setError(null)
    try {
      // El formulario pide el porcentaje (12 = 12 %); la API recibe la fracción (0,12).
      const fraccion = Number(porcentaje.replace(',', '.')) / 100
      await api('/tasas', { method: 'POST', body: JSON.stringify({
        banco, tipo, tasaEa: fraccion, vigenteDesde: desde, fuente, esEjemplo }) })
      setBanco(''); setPorcentaje(''); setFuente(''); setEsEjemplo(false)
      await cargar()
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo guardar la tasa.')
    } finally {
      setGuardando(false)
    }
  }

  const eliminar = async (t: Tasa) => {
    if (!window.confirm(`¿Eliminar la tasa de ${t.banco}?`)) return
    await api(`/tasas/${t.id}`, { method: 'DELETE' })
    await cargar()
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="ancha text-[30px] font-semibold leading-tight md:text-4xl">Tasas de crédito</h1>
      <p className="mt-1 text-niebla">
        Con estas tasas se calculan los presupuestos y las simulaciones. Las carga el administrador, con su fuente.
      </p>

      {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}

      <ul className="mt-6 divide-y divide-linea border-y border-linea">
        {tasas?.map((t) => (
          <li key={t.id} className="flex items-start justify-between gap-4 py-4">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-tinta">{t.banco}</span>
                <span className="rounded-full bg-papel px-2 py-0.5 text-xs text-grafito">{t.tipo === 'VIS' ? 'VIS' : 'No VIS'}</span>
                {t.esEjemplo && <span className="rounded-full bg-ambar-suave px-2 py-0.5 text-xs font-medium text-ambar">Tasa de ejemplo</span>}
              </div>
              <div className="mt-0.5 text-sm text-niebla">Vigente desde {t.vigenteDesde} · {t.fuente}</div>
            </div>
            <div className="flex shrink-0 items-center gap-4">
              <div className="ancha cifra text-lg font-semibold text-tinta">{formatoTasaEa(t.tasaEa)}</div>
              {esAdmin && <button onClick={() => void eliminar(t)} className="text-sm text-red-700 hover:underline">Eliminar</button>}
            </div>
          </li>
        ))}
        {tasas && tasas.length === 0 && <li className="py-6 text-sm text-niebla">Todavía no hay tasas cargadas.</li>}
      </ul>

      {esAdmin ? (
        <form onSubmit={guardar} className="mt-8 space-y-4 rounded-panel border border-linea bg-white p-5">
          <h2 className="text-lg font-semibold">Cargar una tasa</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <Campo etiqueta="Banco">{(id) => <input id={id} value={banco} onChange={(e) => setBanco(e.target.value)} required className={claseInput} />}</Campo>
            <Campo etiqueta="Tipo">
              {(id) => (
                <select id={id} value={tipo} onChange={(e) => setTipo(e.target.value as TipoTasa)} className={claseInput}>
                  <option value="NO_VIS">No VIS</option>
                  <option value="VIS">VIS</option>
                </select>
              )}
            </Campo>
            <Campo etiqueta="Tasa efectiva anual (%)" ayuda="Por ejemplo, 12 o 11,5">
              {(id) => <input id={id} inputMode="decimal" value={porcentaje} onChange={(e) => setPorcentaje(e.target.value)} required className={claseInput} />}
            </Campo>
            <Campo etiqueta="Vigente desde">{(id) => <input id={id} type="date" value={desde} onChange={(e) => setDesde(e.target.value)} required className={claseInput} />}</Campo>
          </div>
          <Campo etiqueta="Fuente" ayuda="De dónde sale la tasa: página del banco, circular, fecha de consulta…">
            {(id) => <input id={id} value={fuente} onChange={(e) => setFuente(e.target.value)} required className={claseInput} />}
          </Campo>
          <label className="flex items-center gap-2 text-sm text-grafito">
            <input type="checkbox" checked={esEjemplo} onChange={(e) => setEsEjemplo(e.target.checked)} className="size-4 accent-[var(--color-cobalto)]" />
            Es una tasa de ejemplo (no real)
          </label>
          <button type="submit" disabled={guardando} className={claseBotonPrimario}>{guardando ? 'Guardando…' : 'Guardar tasa'}</button>
        </form>
      ) : (
        <p className="mt-6 text-sm text-niebla">Solo el administrador puede cargar o corregir tasas.</p>
      )}
    </div>
  )
}
