import { useState, type FormEvent } from 'react'
import { Terreno } from '../componentes/Terreno'
import { useSesion } from '../sesion/Sesion'

const campo =
  'w-full rounded-control border border-linea bg-white px-3.5 py-3 text-base text-tinta outline-none transition-colors placeholder:text-niebla focus:border-cobalto focus:ring-4 focus:ring-cobalto-suave'

export function Login() {
  const { iniciar } = useSesion()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await iniciar(email, password)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión. Revisa tu conexión e intenta de nuevo.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="grid min-h-dvh bg-white md:grid-cols-[1.15fr_1fr]">
      <section className="relative isolate flex min-h-[19rem] flex-col justify-between overflow-hidden bg-prusia px-6 pb-8 pt-7 text-white md:min-h-0 md:px-14 md:py-12">
        <Terreno semilla={959} trazar className="absolute inset-0 -z-20 size-full text-trazo" />
        <div className="absolute inset-0 -z-10 bg-gradient-to-t from-prusia via-prusia/70 to-transparent" aria-hidden="true" />
        <span className="ancha text-2xl font-bold tracking-tight">Predia</span>
        <div>
          <div className="rotulo mb-4 flex items-center gap-3 text-sm text-trazo" aria-hidden="true">
            <span className="h-px w-10 bg-trazo" />
            Bucaramanga · 959 m sobre el nivel del mar
          </div>
          <h2 className="ancha max-w-xl text-[30px] text-white font-semibold leading-[1.05] md:text-[52px]">
            Sabe qué le falta a cada negocio para firmar
          </h2>
          <p className="mt-4 max-w-md text-[15px] leading-relaxed text-white/75 md:mt-6 md:text-lg">
            Expedientes, inventario y cierres de negocio de tu inmobiliaria, a mano durante cada visita.
          </p>
        </div>
      </section>

      <section className="flex items-start justify-center px-6 py-10 md:items-center md:px-14">
        <form onSubmit={enviar} className="w-full max-w-sm" noValidate>
          <h1 className="ancha text-[28px] font-semibold leading-tight">Ingresa a tu cuenta</h1>
          <p className="mt-2 text-niebla">Usa el correo que te asignó tu inmobiliaria.</p>

          <label className="mb-1.5 mt-8 block text-sm font-medium text-tinta" htmlFor="email">Correo</label>
          <input id="email" type="email" autoComplete="username" required value={email}
            onChange={(e) => setEmail(e.target.value)} placeholder="nombre@inmobiliaria.com" className={campo} />

          <label className="mb-1.5 mt-5 block text-sm font-medium text-tinta" htmlFor="password">Contraseña</label>
          <input id="password" type="password" autoComplete="current-password" required value={password}
            onChange={(e) => setPassword(e.target.value)} className={campo} />

          {error && (
            <p role="alert" className="mt-5 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>
          )}

          <button type="submit" disabled={enviando || !email || !password}
            className="mt-7 w-full rounded-control bg-tinta py-3 font-semibold text-white transition-colors hover:bg-prusia disabled:cursor-not-allowed disabled:bg-niebla">
            {enviando ? 'Ingresando…' : 'Ingresar'}
          </button>
        </form>
      </section>
    </div>
  )
}
