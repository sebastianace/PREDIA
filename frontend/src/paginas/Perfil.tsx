import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ErrorApi, token } from '../api/cliente'
import type { Periodo, PerfilUsuario, RespuestaLogin, RendimientoPersonal } from '../api/tipos'
import { Avatar } from '../componentes/Avatar'
import { CampoFotoPerfil, aplicarCambioFoto, type CambioFoto } from '../componentes/CampoFotoPerfil'
import { Aviso, Campo, Segmentado, claseBotonPrimario, claseInput } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { PanelAzul } from '../componentes/PanelAzul'
import { formatoFecha, formatoPesos } from '../formato'
import { useSesion } from '../sesion/Sesion'

export const periodos: { valor: Periodo; texto: string }[] = [
  { valor: 'MES', texto: 'Este mes' }, { valor: 'ANIO', texto: 'Este año' }, { valor: 'TODO', texto: 'Todo' },
]

function Cifra({ nombre, valor, grande = false }: { nombre: string; valor: string; grande?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="rotulo whitespace-nowrap text-sm text-niebla">{nombre}</dt>
      <dd className={`ancha cifra whitespace-nowrap font-semibold ${grande ? 'text-[2rem] leading-tight text-prusia' : 'text-lg text-tinta'}`}>{valor}</dd>
    </div>
  )
}

/** «Mi perfil»: el asesor edita su nombre y su foto y ve sus propias cifras de ventas (nunca las de los demás). */
export function Perfil() {
  const { usuario, entrarCon } = useSesion()
  const [nombre, setNombre] = useState(usuario?.nombre ?? '')
  const [cambioFoto, setCambioFoto] = useState<CambioFoto>(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [guardado, setGuardado] = useState(false)

  const [periodo, setPeriodo] = useState<Periodo>('ANIO')
  const [datos, setDatos] = useState<RendimientoPersonal | null>(null)
  const [errorDatos, setErrorDatos] = useState<string | null>(null)

  useEffect(() => {
    setErrorDatos(null)
    api<RendimientoPersonal>(`/perfil/rendimiento?periodo=${periodo}`).then(setDatos)
      .catch((e) => setErrorDatos(e instanceof ErrorApi ? e.message : 'No se pudieron cargar tus cifras.'))
  }, [periodo])

  if (!usuario) return null
  const nombreLimpio = nombre.trim().replace(/\s+/g, ' ')
  const hayCambios = nombreLimpio !== usuario.nombre || cambioFoto !== null

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setGuardando(true)
    setError(null)
    setGuardado(false)
    try {
      let sesion: RespuestaLogin | null = null
      if (nombreLimpio !== usuario.nombre) {
        sesion = await api<RespuestaLogin>('/perfil', { method: 'PUT', body: JSON.stringify({ nombre: nombreLimpio }) })
        token.guardar(sesion.token)          // el nombre viaja dentro del token: se usa el nuevo
      }
      await aplicarCambioFoto(`/perfil/usuarios/${usuario.id}/foto`, cambioFoto)
      // Se vuelve a leer el perfil para que el menú y el resto de la pantalla muestren el nombre y la foto nuevos.
      const yo = await api<PerfilUsuario>('/auth/yo')
      await entrarCon({ token: sesion?.token ?? token.leer() ?? '', expiraEn: sesion?.expiraEn ?? '', usuario: yo })
      setNombre(yo.nombre)
      setCambioFoto(null)
      setGuardado(true)
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudieron guardar los cambios.')
    } finally {
      setGuardando(false)
    }
  }

  const r = datos?.rendimiento
  return (
    <div className="mx-auto max-w-4xl">
      <PanelAzul semilla={usuario.id * 13 + 5} ancho={1000} alto={220} niveles={10} className="rounded-panel px-5 py-5 md:px-7 md:py-6">
        <div className="relative flex flex-wrap items-center gap-4">
          <Avatar nombre={usuario.nombre} tono="blanco" tamano="gigante" foto={{ ruta: `/perfil/usuarios/${usuario.id}/foto`, version: usuario.fotoVersion }} />
          <div className="min-w-0">
            <p className="rotulo text-sm text-trazo">Mi perfil</p>
            <h1 className="ancha break-words text-2xl font-semibold text-white md:text-3xl">{usuario.nombre}</h1>
            <p className="mt-1.5 flex flex-wrap gap-1.5">
              <span className="rounded-full bg-cobalto-suave px-2.5 py-0.5 text-xs font-medium text-cobalto-hondo">{usuario.rol === 'ADMIN' ? 'Administrador' : 'Asesor'}</span>
              <span className="rounded-full bg-white/15 px-2.5 py-0.5 text-xs">{usuario.inmobiliaria}</span>
            </p>
          </div>
        </div>
      </PanelAzul>

      <div className="mt-4 grid gap-4 lg:grid-cols-2 lg:items-start">
        <form onSubmit={guardar} className="space-y-5 rounded-panel border border-linea bg-white p-4 md:p-5">
          <h2 className="flex items-center gap-2 text-base font-semibold text-tinta"><Icono nombre="usuario" className="size-5 text-cobalto" />Mis datos</h2>
          <CampoFotoPerfil nombre={usuario.nombre} valor={cambioFoto} onChange={setCambioFoto}
            actual={{ ruta: `/perfil/usuarios/${usuario.id}/foto`, version: usuario.fotoVersion }}
            ayuda="Se recorta en círculo y la ven tus compañeros y tus clientes. Se guarda una copia reducida." />
          <Campo etiqueta="Nombre">
            {(id) => <input id={id} value={nombre} maxLength={150} required onChange={(e) => { setNombre(e.target.value); setGuardado(false) }} className={claseInput} />}
          </Campo>
          <Campo etiqueta="Correo" ayuda="Identifica tu cuenta: lo cambia el administrador.">
            {(id) => (
              <div className="relative">
                <Icono nombre="candado" className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-niebla" />
                <input id={id} value={usuario.email} disabled className={`${claseInput} pl-9`} />
              </div>
            )}
          </Campo>
          {error && <Aviso tipo="error">{error}</Aviso>}
          {guardado && <Aviso tipo="info">Cambios guardados.</Aviso>}
          <button type="submit" disabled={guardando || nombreLimpio === '' || !hayCambios} className={claseBotonPrimario}>
            {guardando ? 'Guardando…' : 'Guardar cambios'}
          </button>
        </form>

        <section className="rounded-panel border border-linea bg-white p-4 md:p-5" aria-labelledby="titulo-rendimiento">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="titulo-rendimiento" className="flex items-center gap-2 text-base font-semibold text-tinta">
              <Icono nombre="barras" className="size-5 text-cobalto" />Mi rendimiento
            </h2>
            <Segmentado etiqueta="Periodo" ocultarEtiqueta opciones={periodos} valor={periodo} onChange={setPeriodo} />
          </div>
          {errorDatos && <div className="mt-4"><Aviso tipo="error">{errorDatos}</Aviso></div>}
          {!datos && !errorDatos && <p className="mt-4 text-niebla">Cargando…</p>}
          {r && (
            <>
              <dl className="mt-5 border-l-2 border-prusia pl-4"><Cifra nombre="Inmuebles vendidos" valor={String(r.vendidos)} grande /></dl>
              <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-4">
                <Cifra nombre="Valor vendido" valor={formatoPesos(r.valorVendido)} />
                <Cifra nombre="Negocios abiertos" valor={String(r.abiertos)} />
                <Cifra nombre="Días promedio de cierre" valor={r.diasPromedioCierre == null ? '—' : `${r.diasPromedioCierre} días`} />
                <Cifra nombre="Tasa de cierre" valor={r.tasaDeCierre == null ? '—' : `${r.tasaDeCierre} %`} />
              </dl>
              {datos.ultimosCierres.length > 0 && (
                <div className="mt-5 border-t border-linea pt-4">
                  <h3 className="text-sm font-semibold text-tinta">Últimos cierres</h3>
                  <ul className="mt-2 divide-y divide-linea">
                    {datos.ultimosCierres.map((c) => (
                      <li key={c.negocioId}>
                        <Link to={`/negocios/${c.negocioId}`} className="flex items-baseline justify-between gap-3 py-2 text-sm hover:text-cobalto">
                          <span className="min-w-0 truncate">{c.inmueble}</span>
                          <span className="shrink-0 text-niebla">{formatoPesos(c.valor)} · {formatoFecha(c.firmadoEn)}</span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <p className="mt-5 flex items-start gap-2 border-t border-linea pt-4 text-xs text-niebla">
                <Icono nombre="escudo" className="mt-px size-4 shrink-0" />
                Se cuentan los negocios que se confirmaron como firmados con «Cerrar negocio». Estos números no se editan a mano.
              </p>
            </>
          )}
        </section>
      </div>
    </div>
  )
}
