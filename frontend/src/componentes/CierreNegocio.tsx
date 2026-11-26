import { useState } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { EstadoDocumental, Negocio } from '../api/tipos'
import { Aviso, Campo, CampoPesos, claseBotonPrimario, claseBotonSecundario, claseInput } from './Formulario'
import { Icono } from './Iconos'
import { formatoFechaHora, formatoPesos } from '../formato'

/**
 * Cerrar un negocio es registrar la venta firmada: la persona confirma y las reglas deciden. Mientras falten documentos
 * solo un administrador puede cerrarlo (con una nota); al cerrarlo el inmueble queda vendido y la venta cuenta para el
 * asesor. Solo un administrador puede reabrir un cierre, y siempre con una nota.
 */
export function CierreNegocio({ negocio, estado, esAdmin, alCambiar }: {
  negocio: Negocio
  estado: EstadoDocumental
  esAdmin: boolean
  alCambiar: () => void
}) {
  const [abierto, setAbierto] = useState(false)
  const [valor, setValor] = useState<number | null>(negocio.precioPublicado ?? null)
  const [nota, setNota] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const enviar = async (ruta: 'cerrar' | 'reabrir') => {
    setEnviando(true)
    setError(null)
    try {
      await api(`/negocios/${negocio.id}/${ruta}`, {
        method: 'POST',
        body: JSON.stringify(ruta === 'cerrar' ? { valorCierre: valor, nota: nota.trim() || null } : { nota: nota.trim() }),
      })
      setAbierto(false)
      setNota('')
      alCambiar()
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo completar la acción.')
    } finally {
      setEnviando(false)
    }
  }

  if (negocio.estado === 'FIRMADO') {
    return (
      <section className="mt-4 rounded-panel border border-linea bg-white p-4 md:p-5" aria-label="Cierre del negocio">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="flex items-center gap-2 text-sm font-semibold text-prusia">
              <Icono nombre="circuloCheck" className="size-5" />Venta cerrada
            </p>
            <p className="ancha mt-1 text-2xl font-semibold text-tinta">{formatoPesos(negocio.valorCierre)}</p>
            <p className="mt-0.5 text-sm text-niebla">Firmada el {formatoFechaHora(negocio.firmadoEn)} · cuenta para {negocio.asesorNombre ?? 'su asesor'}</p>
            {negocio.notaCierre && <p className="mt-2 text-sm text-grafito">Nota del cierre: {negocio.notaCierre}</p>}
          </div>
          {esAdmin && !abierto && (
            <button type="button" onClick={() => setAbierto(true)} className={claseBotonSecundario}>Reabrir negocio</button>
          )}
        </div>
        {esAdmin && abierto && (
          <div className="mt-4 space-y-3 border-t border-linea pt-4">
            <Campo etiqueta="¿Por qué se reabre?" ayuda="Obligatorio (mínimo 10 caracteres). El inmueble vuelve a quedar disponible y la venta deja de contar.">
              {(id) => <textarea id={id} rows={2} value={nota} onChange={(e) => setNota(e.target.value)} className={claseInput} />}
            </Campo>
            {error && <Aviso tipo="error">{error}</Aviso>}
            <div className="flex gap-2">
              <button type="button" disabled={enviando || nota.trim().length < 10} onClick={() => void enviar('reabrir')} className={claseBotonPrimario}>
                {enviando ? 'Reabriendo…' : 'Confirmar reapertura'}
              </button>
              <button type="button" onClick={() => { setAbierto(false); setError(null) }} className={claseBotonSecundario}>Cancelar</button>
            </div>
          </div>
        )}
      </section>
    )
  }

  if (negocio.estado !== 'ABIERTO') return null

  const listo = estado.listoParaFirmar
  const necesitaNota = !listo && esAdmin
  const puedeConfirmar = (valor ?? 0) > 0 && (!necesitaNota || nota.trim().length >= 10) && !enviando

  return (
    <section className="mt-4 rounded-panel border border-linea bg-white p-4 md:p-5" aria-label="Cierre del negocio">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-tinta">¿Ya se firmó la venta?</h2>
          <p className="mt-0.5 text-sm text-niebla">
            {listo ? 'La lista de documentos está completa: puedes cerrar el negocio.'
              : esAdmin ? `${estado.semaforo}: como administrador puedes cerrarlo con una nota.`
                : `Todavía no se puede cerrar: ${estado.semaforo.toLowerCase()}.`}
          </p>
        </div>
        {negocio.puedeCerrar && !abierto && (
          <button type="button" disabled={!listo && !esAdmin} onClick={() => setAbierto(true)} className={claseBotonPrimario}>Cerrar negocio</button>
        )}
      </div>
      {!negocio.puedeCerrar && <p className="mt-2 text-xs text-niebla">Solo {negocio.asesorNombre ?? 'el asesor del negocio'} o un administrador puede cerrarlo.</p>}
      {negocio.puedeCerrar && abierto && (
        <div className="mt-4 space-y-4 border-t border-linea pt-4">
          <div className="max-w-sm">
            <CampoPesos etiqueta="Valor de la venta" valor={valor} onChange={setValor}
              ayuda={negocio.precioPublicado ? `Precio publicado: ${formatoPesos(negocio.precioPublicado)}. Corrígelo si se vendió por otro valor.` : 'Indica por cuánto se vendió.'} />
          </div>
          {necesitaNota && (
            <Campo etiqueta="¿Por qué se cierra con documentos pendientes?" ayuda="Obligatorio (mínimo 10 caracteres).">
              {(id) => <textarea id={id} rows={2} value={nota} onChange={(e) => setNota(e.target.value)} className={claseInput} />}
            </Campo>
          )}
          <p className="text-sm text-grafito">
            Al confirmar, <strong className="font-medium text-tinta">{negocio.inmuebleTitulo}</strong> pasa a «Vendido» y la venta cuenta para {negocio.asesorNombre ?? 'su asesor'}.
          </p>
          {error && <Aviso tipo="error">{error}</Aviso>}
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={!puedeConfirmar} onClick={() => void enviar('cerrar')} className={claseBotonPrimario}>
              {enviando ? 'Cerrando…' : 'Confirmar cierre'}
            </button>
            <button type="button" onClick={() => { setAbierto(false); setError(null) }} className={claseBotonSecundario}>Cancelar</button>
          </div>
        </div>
      )}
    </section>
  )
}
