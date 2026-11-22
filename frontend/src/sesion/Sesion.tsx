import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, token } from '../api/cliente'
import type { EstadoSistema, PerfilUsuario, RespuestaLogin } from '../api/tipos'

interface ValorSesion {
  usuario: PerfilUsuario | null
  sistema: EstadoSistema | null
  cargando: boolean
  iniciar: (email: string, password: string) => Promise<void>
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

  const cargarSistema = () => api<EstadoSistema>('/sistema/estado').then(setSistema).catch(() => setSistema(null))

  useEffect(() => {
    if (!token.leer()) {
      setCargando(false)
      return
    }
    api<PerfilUsuario>('/auth/yo')
      .then((u) => {
        setUsuario(u)
        return cargarSistema()
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
    token.guardar(r.token)
    setUsuario(r.usuario)
    await cargarSistema()
  }

  return (
    <ContextoSesion.Provider value={{ usuario, sistema, cargando, iniciar, cerrar }}>{children}</ContextoSesion.Provider>
  )
}

export function useSesion() {
  const valor = useContext(ContextoSesion)
  if (!valor) throw new Error('useSesion debe usarse dentro de ProveedorSesion')
  return valor
}
