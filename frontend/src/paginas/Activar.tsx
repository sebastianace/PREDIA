import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { RespuestaLogin, VistaInvitacion } from '../api/tipos'
import { Terreno } from '../componentes/Terreno'
import { useSesion } from '../sesion/Sesion'

const MINIMO = 10
const campo =
  'w-full rounded-control border border-linea bg-white px-3.5 py-3 text-base text-tinta outline-none transition-colors placeholder:text-niebla focus:border-cobalto focus:ring-4 focus:ring-cobalto-suave'

/**
 * Página pública del enlace de invitación: crear la contraseña (o elegir una nueva). Al propietario se le trata de
 * «usted»; al personal, de «tú».
 */
export function Activar() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { entrarCon } = useSesion()
  const navegar = useNavigate()
  const [vista, setVista] = useState<VistaInvitacion | null>(null)
  const [invalido, setInvalido] = useState<string | null>(null)
  const [clave, setClave] = useState('')
  const [repetida, setRepetida] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!token) { setInvalido('Este enlace no está completo. Ábrelo de nuevo desde el correo.'); return }
    api<VistaInvitacion>(`/auth/invitaciones/${encodeURIComponent(token)}`)
      .then(setVista)
      .catch((e) => setInvalido(e instanceof ErrorApi ? e.message : 'No se pudo abrir el enlace.'))
  }, [token])

  const usted = vista?.rol === 'PROPIETARIO'
  const coinciden = clave === repetida
  const puede = clave.length >= MINIMO && coinciden && !enviando

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    if (!puede) return
    setEnviando(true); setError(null)
    try {
      const r = await api<RespuestaLogin>('/auth/activar', { method: 'POST', body: JSON.stringify({ token, password: clave }) })
      await entrarCon(r)
      navegar(r.usuario.rol === 'PROPIETARIO' ? '/portal' : '/', { replace: true })
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo guardar la contraseña.')
      setEnviando(false)
    }
  }

  return (
    <div className="grid min-h-dvh bg-white md:grid-cols-[1fr_1fr]">
      <section className="relative isolate flex min-h-[14rem] flex-col justify-between overflow-hidden bg-prusia px-6 pb-8 pt-7 text-white md:min-h-0 md:px-14 md:py-12">
        <Terreno semilla={1102} trazar className="absolute inset-0 -z-20 size-full text-trazo" />
        <div className="absolute inset-0 -z-10 bg-gradient-to-t from-prusia via-prusia/70 to-transparent" aria-hidden="true" />
        <span className="ancha text-2xl font-bold tracking-tight">Predia</span>
        {vista && (
          <p className="ancha max-w-md text-[26px] font-semibold leading-[1.1] text-white md:text-[40px]">{vista.inmobiliaria}</p>
        )}
      </section>

      <section className="flex items-start justify-center px-6 py-10 md:items-center md:px-14">
        <div className="w-full max-w-sm">
          {invalido && (
            <>
              <h1 className="ancha text-[28px] font-semibold leading-tight">Enlace no válido</h1>
              <p role="alert" className="mt-3 text-grafito">{invalido}</p>
              <a href="/" className="mt-6 inline-block text-sm font-medium text-cobalto">Ir a iniciar sesión</a>
            </>
          )}
          {!invalido && !vista && <p className="text-niebla">Abriendo el enlace…</p>}
          {vista && (
            <form onSubmit={enviar} noValidate>
              <h1 className="ancha text-[28px] font-semibold leading-tight">
                {vista.yaTieneCuenta ? (usted ? 'Elija una contraseña nueva' : 'Elige una contraseña nueva')
                  : `Hola, ${vista.nombre.split(' ')[0]}`}
              </h1>
              <p className="mt-2 text-niebla">
                {vista.yaTieneCuenta ? `Para su cuenta de ${vista.inmobiliaria}.`
                  : usted ? 'Cree su contraseña para entrar a su portal. Después podrá entrar cuando quiera con su correo y esta contraseña.'
                    : 'Crea tu contraseña para entrar a Predia.'}
              </p>

              <label className="mb-1.5 mt-8 block text-sm font-medium text-tinta" htmlFor="clave">Contraseña</label>
              <input id="clave" type="password" autoComplete="new-password" value={clave} onChange={(e) => setClave(e.target.value)} className={campo} />
              <p className={`mt-1 text-xs ${clave.length > 0 && clave.length < MINIMO ? 'text-red-700' : 'text-niebla'}`}>Mínimo {MINIMO} caracteres.</p>

              <label className="mb-1.5 mt-5 block text-sm font-medium text-tinta" htmlFor="repetida">Repite la contraseña</label>
              <input id="repetida" type="password" autoComplete="new-password" value={repetida} onChange={(e) => setRepetida(e.target.value)} className={campo} />
              {repetida.length > 0 && !coinciden && <p className="mt-1 text-xs text-red-700">Las contraseñas no coinciden.</p>}

              {error && <p role="alert" className="mt-5 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
              <button type="submit" disabled={!puede}
                className="mt-7 w-full rounded-control bg-tinta py-3 font-semibold text-white transition-colors hover:bg-prusia disabled:cursor-not-allowed disabled:bg-niebla">
                {enviando ? 'Guardando…' : 'Guardar y entrar'}
              </button>
            </form>
          )}
        </div>
      </section>
    </div>
  )
}
