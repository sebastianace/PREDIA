import { useEffect, useState } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { AccesoPortal, CorreoVistaPrevia, InvitacionPortal } from '../api/tipos'
import { formatoFechaHora } from '../formato'
import { Aviso, claseBotonPrimario, claseBotonSecundario } from './Formulario'

const textoEstado: Record<AccesoPortal['estado'], string> = {
  SIN_INVITAR: 'Todavía no tiene acceso a su portal.',
  INVITADO: 'Invitado: aún no ha creado su contraseña.',
  ACTIVO: 'Tiene acceso a su portal.',
  DESACTIVADO: 'Su acceso al portal está desactivado.',
}

/**
 * El acceso del propietario a su portal, desde el negocio. Invitar es una acción explícita del asesor: primero ve el
 * correo tal como saldrá y solo se envía cuando él lo confirma.
 */
export function AccesoPortalPropietario({ propietarioId, clienteId, nombre }:
  { propietarioId?: number; clienteId?: number; nombre: string }) {
  // El mismo componente administra el acceso de un propietario (su venta) y el de un cliente comprador (sus inmuebles).
  const esCliente = clienteId != null
  const base = esCliente ? `/clientes/${clienteId}` : `/propietarios/${propietarioId}`
  const id = esCliente ? clienteId : propietarioId
  const [acceso, setAcceso] = useState<AccesoPortal | null>(null)
  const [vista, setVista] = useState<CorreoVistaPrevia | null>(null)
  const [resultado, setResultado] = useState<InvitacionPortal | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [trabajando, setTrabajando] = useState(false)
  const [copiado, setCopiado] = useState(false)

  const cargar = () => api<AccesoPortal>(`${base}/acceso`).then(setAcceso).catch(() => setAcceso(null))
  useEffect(() => { void cargar() }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  const prepararInvitacion = async () => {
    setError(null); setResultado(null)
    try {
      setVista(await api<CorreoVistaPrevia>(`${base}/invitacion/vista-previa`))
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo preparar la invitación.')
    }
  }

  const enviar = async () => {
    setTrabajando(true); setError(null)
    try {
      const r = await api<InvitacionPortal>(`${base}/invitacion`, { method: 'POST' })
      setResultado(r); setAcceso(r.acceso); setVista(null)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo enviar la invitación.')
    } finally {
      setTrabajando(false)
    }
  }

  const cambiarAcceso = async (activo: boolean) => {
    if (!activo && !window.confirm(`¿Desactivar el acceso de ${nombre} a su portal? Su sesión se cerrará de inmediato.`)) return
    setTrabajando(true); setError(null)
    try {
      setAcceso(await api<AccesoPortal>(`${base}/acceso`, { method: 'PUT', body: JSON.stringify({ activo }) }))
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo cambiar el acceso.')
    } finally {
      setTrabajando(false)
    }
  }

  const copiar = async (texto: string) => {
    try { await navigator.clipboard.writeText(texto); setCopiado(true); setTimeout(() => setCopiado(false), 2000) } catch { /* sin portapapeles */ }
  }

  if (!acceso) return null
  const yaTieneCuenta = acceso.estado === 'ACTIVO' || acceso.estado === 'DESACTIVADO'

  return (
    <section className="mt-10" aria-labelledby="titulo-portal">
      <h2 id="titulo-portal" className="text-lg font-semibold">{esCliente ? 'Acceso del cliente' : 'Portal del propietario'}</h2>
      <p className="mt-1 text-sm text-niebla">
        {esCliente
          ? `Desde su portal, ${nombre} ve su presupuesto y los inmuebles que tú le compartas, simula su compra y te deja mensajes. No ve la dirección exacta ni datos del propietario.`
          : `Desde su portal, ${nombre} ve qué documentos faltan, cuáles vencieron y puede enviarlos. Lo que envía queda en revisión hasta que tú lo confirmas.`}
      </p>

      <div className="mt-4 rounded-panel border border-linea bg-white p-4 md:p-5">
        <p className="font-medium text-tinta">{textoEstado[acceso.estado]}</p>
        <p className="mt-0.5 text-sm text-niebla">
          {acceso.estado === 'INVITADO' && acceso.invitacionVence && `El enlace vence el ${formatoFechaHora(acceso.invitacionVence)}.`}
          {acceso.estado === 'ACTIVO' && (acceso.ultimoIngreso ? `Último ingreso: ${formatoFechaHora(acceso.ultimoIngreso)}.` : 'Aún no ha entrado.')}
          {acceso.estado === 'SIN_INVITAR' && !acceso.email && 'Para invitarlo, primero agrega su correo.'}
        </p>

        {!vista && (
          <div className="mt-4 flex flex-wrap gap-2">
            {acceso.estado !== 'DESACTIVADO' && (
              <button onClick={() => void prepararInvitacion()} disabled={trabajando || !acceso.email}
                className={acceso.estado === 'SIN_INVITAR' ? claseBotonPrimario : claseBotonSecundario}>
                {acceso.estado === 'SIN_INVITAR' ? 'Invitar al portal' : yaTieneCuenta ? 'Enviar enlace para nueva contraseña' : 'Reenviar invitación'}
              </button>
            )}
            {acceso.estado === 'ACTIVO' && (
              <button onClick={() => void cambiarAcceso(false)} disabled={trabajando} className={claseBotonSecundario}>Desactivar acceso</button>
            )}
            {acceso.estado === 'DESACTIVADO' && (
              <button onClick={() => void cambiarAcceso(true)} disabled={trabajando} className={claseBotonSecundario}>Reactivar acceso</button>
            )}
          </div>
        )}

        {vista && (
          <div className="mt-4 border-t border-linea pt-4">
            <p className="text-sm text-niebla">Así le llegará el correo a <strong className="text-tinta">{vista.para}</strong>:</p>
            <div className="mt-2 rounded-control border border-linea bg-papel p-3 text-sm">
              <p className="font-medium text-tinta">{vista.asunto}</p>
              <p className="mt-2 whitespace-pre-line text-grafito [overflow-wrap:anywhere]">{vista.cuerpo}</p>
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              <button onClick={() => void enviar()} disabled={trabajando} className={claseBotonPrimario}>
                {trabajando ? 'Enviando…' : 'Enviar invitación'}
              </button>
              <button onClick={() => setVista(null)} disabled={trabajando} className={claseBotonSecundario}>Cancelar</button>
            </div>
          </div>
        )}

        {resultado && (
          <div className="mt-4 space-y-2">
            <Aviso tipo={resultado.correoEnviado ? 'info' : 'error'}>
              {resultado.correoEnviado
                ? `Invitación enviada a ${resultado.acceso.email}.`
                : 'El correo no se pudo enviar. Comparte el enlace por otro medio (por ejemplo, WhatsApp).'}
            </Aviso>
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-niebla">Enlace personal (vence en 72 horas, sirve una vez):</span>
              <button onClick={() => void copiar(resultado.enlace)} className={claseBotonSecundario}>
                {copiado ? 'Copiado' : 'Copiar enlace'}
              </button>
            </div>
          </div>
        )}
        {error && <div className="mt-3"><Aviso tipo="error">{error}</Aviso></div>}
      </div>
    </section>
  )
}
