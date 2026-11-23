import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, token } from '../api/cliente'
import type { EstadoSistema, PerfilUsuario, RespuestaLogin } from '../api/tipos'

interface ValorSesion {
  usuario: PerfilUsuario | null
  sistema: EstadoSistema | null
  cargando: boolean
  iniciar: (email: string, password: string) => Promise<void>
  /** Para cuando la sesión llega por otra vía (al activar la cuenta con el enlace de invitación). */
  entrarCon: (r: RespuestaLogin) => Promise<void>
  cerrar: () => void
}

const ContextoSesion = createContext<ValorSesion | null>(null)

export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<PerfilUsuario | null>(null)
  const [sistema, setSistema] = useState<EstadoSistema | null>(null)
  const [cargando, setCargando] = useState(true)

  const cerrar = useCallback(() => {
    token.borrar()
    setUsuario(null)
    setSistema(null)
  }, [])

  // El estado de la IA es del personal; el propietario no lo necesita (y su rol no puede consultarlo).
  const cargarSistema = (rol?: string) => rol === 'PROPIETARIO' ? Promise.resolve(setSistema(null))
    : api<EstadoSistema>('/sistema/estado').then(setSistema).catch(() => setSistema(null))

  useEffect(() => {
    if (!token.leer()) {
      setCargando(false)
      return
    }
    api<PerfilUsuario>('/auth/yo')
      .then((u) => {
        setUsuario(u)
        return cargarSistema(u.rol)
      })
      .catch(() => token.borrar())
      .finally(() => setCargando(false))
  }, [])

  useEffect(() => {
    window.addEventListener('predia:sesion-expirada', cerrar)
    return () => window.removeEventListener('predia:sesion-expirada', cerrar)
  }, [cerrar])

  const iniciar = async (email: string, password: string) => {
    const r = await api<RespuestaLogin>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
    await entrarCon(r)
  }

  const entrarCon = async (r: RespuestaLogin) => {
    token.guardar(r.token)
    setUsuario(r.usuario)
    await cargarSistema(r.usuario.rol)
  }

  return (
    <ContextoSesion.Provider value={{ usuario, sistema, cargando, iniciar, entrarCon, cerrar }}>{children}</ContextoSesion.Provider>
  )
}

export function useSesion() {
  const valor = useContext(ContextoSesion)
  if (!valor) throw new Error('useSesion debe usarse dentro de ProveedorSesion')
  return valor
}
