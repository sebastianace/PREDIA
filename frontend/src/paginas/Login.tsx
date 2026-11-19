import { useState, type FormEvent } from 'react'
import { useSesion } from '../sesion/Sesion'

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
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-marca-900 px-4">
      <form onSubmit={enviar} className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
        <h1 className="text-2xl font-semibold text-marca-900">Predia</h1>
        <p className="mb-6 text-sm text-stone-500">Asistente para tu inmobiliaria</p>

        <label className="mb-1 block text-sm font-medium" htmlFor="email">Correo</label>
        <input id="email" type="email" autoComplete="username" required value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mb-4 w-full rounded-lg border border-stone-300 px-3 py-2.5 text-base outline-none focus:border-marca-500 focus:ring-2 focus:ring-marca-100" />

        <label className="mb-1 block text-sm font-medium" htmlFor="password">Contraseña</label>
        <input id="password" type="password" autoComplete="current-password" required value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mb-4 w-full rounded-lg border border-stone-300 px-3 py-2.5 text-base outline-none focus:border-marca-500 focus:ring-2 focus:ring-marca-100" />

        {error && <p role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <button type="submit" disabled={enviando}
          className="w-full rounded-lg bg-marca-600 py-2.5 font-medium text-white hover:bg-marca-700 disabled:opacity-60">
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </div>
  )
}
