import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { RespuestaLogin, VistaInvitacion } from '../api/tipos'
import { FachadaEdificio } from '../componentes/FachadaEdificio'
import { Icono } from '../componentes/Iconos'
import { Terreno } from '../componentes/Terreno'
import { useSesion } from '../sesion/Sesion'

const MINIMO = 10
const campo =
  'w-full rounded-control border border-linea bg-white px-3.5 py-3 text-base text-tinta outline-none transition-colors placeholder:text-niebla focus:border-cobalto focus:ring-4 focus:ring-cobalto-suave'

/** Una orientación para quien elige la contraseña; la única regla que se exige es el mínimo de caracteres. */
function fuerza(clave: string): { nivel: number; texto: string } {
  if (clave.length === 0) return { nivel: 0, texto: '' }
  let nivel = clave.length >= MINIMO ? 1 : 0
  if (clave.length >= 14) nivel++
  if (/[a-z]/.test(clave) && /[A-Z]/.test(clave)) nivel++
  if (/\d/.test(clave) && /[^\p{L}\d]/u.test(clave)) nivel++
  return { nivel, texto: clave.length < MINIMO ? 'Muy corta' : ['', 'Aceptable', 'Buena', 'Buena', 'Fuerte'][nivel] }
}

const portalDe = {
  PROPIETARIO: {
    nombre: 'Portal del propietario',
    lema: 'Su venta, sus documentos y su avance en un solo lugar.',
    beneficios: [['expediente', 'Vea qué documentos faltan y cuáles vencieron'], ['subir', 'Envíelos desde aquí; su asesor los confirma']],
  },
  COMPRADOR: {
    nombre: 'Portal del comprador',
    lema: 'Los inmuebles que su asesor eligió para usted, con su cuota calculada.',
    beneficios: [['inventario', 'Vea los inmuebles que su asesor le compartió'], ['reduccion', 'Simule su compra y su cuota mensual']],
  },
} as const

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
  const [verClave, setVerClave] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!token) { setInvalido('Este enlace no está completo. Ábrelo de nuevo desde el correo.'); return }
    api<VistaInvitacion>(`/auth/invitaciones/${encodeURIComponent(token)}`)
      .then(setVista)
      .catch((e) => setInvalido(e instanceof ErrorApi ? e.message : 'No se pudo abrir el enlace.'))
  }, [token])

  const usted = vista?.rol === 'PROPIETARIO' || vista?.rol === 'COMPRADOR'
  const coinciden = clave === repetida
  const portal = vista && (vista.rol === 'PROPIETARIO' || vista.rol === 'COMPRADOR') ? portalDe[vista.rol] : null
  const f = fuerza(clave)
  const puede = clave.length >= MINIMO && coinciden && !enviando

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    if (!puede) return
    setEnviando(true); setError(null)
    try {
      const r = await api<RespuestaLogin>('/auth/activar', { method: 'POST', body: JSON.stringify({ token, password: clave }) })
      await entrarCon(r)
      navegar(r.usuario.rol === 'PROPIETARIO' ? '/portal' : r.usuario.rol === 'COMPRADOR' ? '/comprador' : '/', { replace: true })
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo guardar la contraseña.')
      setEnviando(false)
    }
  }

  const tipoClave = verClave ? 'text' : 'password'
  const ojo = 'absolute right-1 top-1/2 -translate-y-1/2 rounded-control px-2.5 py-1.5 text-xs font-medium text-cobalto hover:bg-cobalto-suave'

  return (
    <div className="grid min-h-dvh bg-papel md:grid-cols-[1fr_1fr]">
      <section className="relative isolate flex min-h-[18rem] flex-col justify-between overflow-hidden bg-prusia px-6 pb-8 pt-7 text-white md:min-h-0 md:px-14 md:py-12">
        <Terreno semilla={1102} className="absolute inset-0 -z-20 size-full text-trazo opacity-25" />
        <FachadaEdificio className="absolute inset-x-6 bottom-24 top-16 -z-10 h-[calc(100%-10rem)] w-[calc(100%-3rem)] text-white md:inset-x-14 md:bottom-40 md:top-24 md:h-[calc(100%-16rem)] md:w-[calc(100%-7rem)]" />
        <span className="ancha text-2xl font-bold tracking-tight">Predia</span>
        {vista && (
          <div>
            {portal && (
              <span className="mb-3 hidden items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-sm md:inline-flex">
                <Icono nombre="inicio" className="size-4" />{portal.nombre}
              </span>
            )}
            <p className="ancha max-w-md text-[26px] font-semibold leading-[1.1] text-white md:text-[40px]">{vista.inmobiliaria}</p>
            {portal && <p className="mt-2 hidden max-w-sm text-sm text-trazo md:block">{portal.lema}</p>}
          </div>
        )}
      </section>

      <section className="flex items-start justify-center px-4 py-8 md:items-center md:px-12">
        <div className="w-full max-w-sm rounded-panel border border-linea bg-white p-6 md:p-7">
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
              <ol className="flex items-center gap-2 text-xs text-niebla" aria-label="Pasos">
                <li className="flex items-center gap-1.5 text-tinta" aria-current="step">
                  <span className="flex size-5 items-center justify-center rounded-full bg-prusia text-[11px] text-white">1</span>
                  {vista.yaTieneCuenta ? 'Elija su contraseña' : usted ? 'Cree su contraseña' : 'Crea tu contraseña'}
                </li>
                <li className="h-px flex-1 bg-linea" aria-hidden="true" />
                <li className="flex items-center gap-1.5">
                  <span className="flex size-5 items-center justify-center rounded-full border border-niebla/50 text-[11px]">2</span>
                  {usted ? 'Entre a su portal' : 'Entra a Predia'}
                </li>
              </ol>

              <h1 className="ancha mt-5 text-[28px] font-semibold leading-tight">
                {vista.yaTieneCuenta ? (usted ? 'Elija una contraseña nueva' : 'Elige una contraseña nueva')
                  : `Hola, ${vista.nombre.split(' ')[0]}`}
              </h1>
              <p className="mt-2 text-niebla">
                {vista.yaTieneCuenta ? `Para su cuenta de ${vista.inmobiliaria}.`
                  : usted ? 'Cree su contraseña para entrar a su portal. Después podrá entrar cuando quiera con su correo y esta contraseña.'
                    : 'Crea tu contraseña para entrar a Predia.'}
              </p>

              {portal && !vista.yaTieneCuenta && (
                <ul className="mt-4 space-y-1.5 rounded-control bg-cobalto-suave px-3.5 py-3 text-sm text-prusia">
                  {portal.beneficios.map(([icono, texto]) => (
                    <li key={texto} className="flex items-start gap-2.5"><Icono nombre={icono} className="mt-0.5 size-4 shrink-0" />{texto}</li>
                  ))}
                </ul>
              )}

              <label className="mb-1.5 mt-6 block text-sm font-medium text-tinta" htmlFor="clave">Contraseña</label>
              <div className="relative">
                <input id="clave" type={tipoClave} autoComplete="new-password" value={clave} onChange={(e) => setClave(e.target.value)} className={`${campo} pr-20`} />
                <button type="button" onClick={() => setVerClave((v) => !v)} aria-pressed={verClave} className={ojo}>{verClave ? 'Ocultar' : 'Mostrar'}</button>
              </div>
              <div className="mt-2 flex items-center gap-1.5" aria-hidden="true">
                {[1, 2, 3, 4].map((n) => (
                  <span key={n} className={`h-1 flex-1 rounded-full ${f.nivel >= n ? (f.nivel <= 1 ? 'bg-ambar' : f.nivel === 2 ? 'bg-cobalto' : 'bg-emerald-600') : 'bg-linea'}`} />
                ))}
              </div>
              <p className={`mt-1 text-xs ${clave.length > 0 && clave.length < MINIMO ? 'text-red-700' : 'text-niebla'}`}>
                Mínimo {MINIMO} caracteres.{f.texto && ` · ${f.texto}`}
              </p>

              <label className="mb-1.5 mt-5 block text-sm font-medium text-tinta" htmlFor="repetida">Repite la contraseña</label>
              <input id="repetida" type={tipoClave} autoComplete="new-password" value={repetida} onChange={(e) => setRepetida(e.target.value)} className={campo} />
              {repetida.length > 0 && !coinciden && <p className="mt-1 text-xs text-red-700">Las contraseñas no coinciden.</p>}

              {error && <p role="alert" className="mt-5 border-l-2 border-red-600 bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}
              <button type="submit" disabled={!puede}
                className="mt-7 w-full rounded-control bg-prusia py-3 font-semibold text-white transition-colors hover:bg-prusia-claro disabled:cursor-not-allowed disabled:bg-niebla">
                {enviando ? 'Guardando…' : 'Guardar y entrar'}
              </button>
            </form>
          )}
        </div>
      </section>
    </div>
  )
}
