import { useEffect, useState, type FormEvent } from 'react'
import { api, ErrorApi } from '../api/cliente'
import type { Rol, UsuarioInvitado, UsuarioPersonal } from '../api/tipos'
import { Aviso, Campo, claseBotonPrimario, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { formatoFechaHora } from '../formato'

const nombreEstado: Record<UsuarioPersonal['estado'], { texto: string; clase: string }> = {
  ACTIVO: { texto: 'Activo', clase: 'text-emerald-700' },
  INVITADO: { texto: 'Invitado', clase: 'text-ambar' },
  DESACTIVADO: { texto: 'Desactivado', clase: 'text-niebla' },
  SIN_INVITAR: { texto: 'Sin invitar', clase: 'text-niebla' },
}

/** El ADMIN administra al personal: crea, invita, cambia el rol y desactiva (lo que cierra la sesión al instante). */
export function Usuarios() {
  const [lista, setLista] = useState<UsuarioPersonal[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [rol, setRol] = useState<Rol>('ASESOR')
  const [creando, setCreando] = useState(false)
  const [invitado, setInvitado] = useState<UsuarioInvitado | null>(null)
  const [copiado, setCopiado] = useState(false)

  const cargar = () => api<UsuarioPersonal[]>('/usuarios').then(setLista).catch((e) => setError(e.message))
  useEffect(() => { void cargar() }, [])

  const crear = async (e: FormEvent) => {
    e.preventDefault()
    setCreando(true); setError(null); setInvitado(null)
    try {
      const r = await api<UsuarioInvitado>('/usuarios', { method: 'POST', body: JSON.stringify({ nombre, email, rol }) })
      setInvitado(r); setNombre(''); setEmail(''); setRol('ASESOR')
      await cargar()
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo crear el usuario.')
    } finally {
      setCreando(false)
    }
  }

  const cambiar = async (u: UsuarioPersonal, cambios: { rol?: Rol; activo?: boolean }) => {
    if (cambios.activo === false && !window.confirm(`¿Desactivar a ${u.nombre}? Su sesión se cerrará de inmediato.`)) return
    setError(null)
    try {
      await api(`/usuarios/${u.id}`, { method: 'PUT', body: JSON.stringify(cambios) })
      await cargar()
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo guardar el cambio.')
    }
  }

  const reenviar = async (u: UsuarioPersonal) => {
    setError(null); setInvitado(null)
    try {
      setInvitado(await api<UsuarioInvitado>(`/usuarios/${u.id}/invitacion`, { method: 'POST' }))
      await cargar()
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo reenviar la invitación.')
    }
  }

  const copiar = async (texto: string) => {
    try { await navigator.clipboard.writeText(texto); setCopiado(true); setTimeout(() => setCopiado(false), 2000) } catch { /* sin portapapeles */ }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="ancha text-[32px] font-semibold leading-[1.05] md:text-5xl">Usuarios</h1>
      <p className="mt-1 text-niebla">
        El personal de tu inmobiliaria. Cada persona recibe un enlace para crear su contraseña; a los propietarios se les
        invita desde su negocio.
      </p>

      <form onSubmit={crear} className="mt-6 rounded-panel border border-linea bg-white p-5">
        <h2 className="text-base font-semibold">Nuevo usuario</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-[1fr_1fr_auto]">
          <Campo etiqueta="Nombre">{(id) => <input id={id} value={nombre} onChange={(e) => setNombre(e.target.value)} required maxLength={150} className={claseInput} />}</Campo>
          <Campo etiqueta="Correo">{(id) => <input id={id} type="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={150} className={claseInput} />}</Campo>
          <Campo etiqueta="Rol">
            {(id) => (
              <select id={id} value={rol} onChange={(e) => setRol(e.target.value as Rol)} className={claseInput}>
                <option value="ASESOR">Asesor</option>
                <option value="ADMIN">Administrador</option>
              </select>
            )}
          </Campo>
        </div>
        <button type="submit" disabled={creando || !nombre.trim() || !email.trim()} className={`${claseBotonPrimario} mt-4`}>
          {creando ? 'Creando…' : 'Crear e invitar'}
        </button>
      </form>

      {invitado && (
        <div className="mt-4 space-y-2">
          <Aviso tipo={invitado.correoEnviado ? 'info' : 'error'}>
            {invitado.correoEnviado ? `Invitación enviada a ${invitado.usuario.email}.`
              : 'El correo no se pudo enviar: comparte el enlace por otro medio.'}
          </Aviso>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-niebla">Enlace personal (vence en 72 horas, sirve una vez):</span>
            <button onClick={() => void copiar(invitado.enlace)} className={claseBotonSecundario}>{copiado ? 'Copiado' : 'Copiar enlace'}</button>
          </div>
        </div>
      )}
      {error && <div className="mt-4"><Aviso tipo="error">{error}</Aviso></div>}

      <ul className="mt-8 divide-y divide-linea border-y border-linea">
        {lista?.map((u) => (
          <li key={u.id} className="py-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="font-medium text-tinta">{u.nombre}{u.esUstedMismo && <span className="ml-2 text-xs font-normal text-niebla">(tú)</span>}</div>
                <div className="truncate text-sm text-niebla">{u.email}</div>
                <div className="mt-1 text-xs text-niebla">
                  <span className={`font-medium ${nombreEstado[u.estado].clase}`}>{nombreEstado[u.estado].texto}</span>
                  {u.estado === 'INVITADO' && u.invitacionVence && ` · el enlace vence el ${formatoFechaHora(u.invitacionVence)}`}
                  {u.ultimoIngreso && ` · último ingreso: ${formatoFechaHora(u.ultimoIngreso)}`}
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <select aria-label={`Rol de ${u.nombre}`} value={u.rol} disabled={u.esUstedMismo}
                  onChange={(e) => void cambiar(u, { rol: e.target.value as Rol })}
                  className="rounded-control border border-linea bg-white px-2.5 py-2 text-sm text-tinta disabled:bg-papel disabled:text-niebla">
                  <option value="ASESOR">Asesor</option>
                  <option value="ADMIN">Administrador</option>
                </select>
                {u.estado === 'ACTIVO' && !u.esUstedMismo && (
                  <button onClick={() => void cambiar(u, { activo: false })} className={claseBotonSecundario}>Desactivar</button>
                )}
                {u.estado === 'DESACTIVADO' && (
                  <button onClick={() => void cambiar(u, { activo: true })} className={claseBotonSecundario}>Reactivar</button>
                )}
                {!u.esUstedMismo && (
                  <button onClick={() => void reenviar(u)} className={claseBotonSecundario}>
                    {u.estado === 'INVITADO' ? 'Reenviar invitación' : 'Nueva contraseña'}
                  </button>
                )}
              </div>
            </div>
          </li>
        ))}
        {!lista && !error && <li className="py-4 text-niebla">Cargando…</li>}
      </ul>
    </div>
  )
}
