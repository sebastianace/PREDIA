import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { Cliente, Intencion, PerfilSugerido } from '../api/tipos'
import { Aviso, Campo, CampoPesos, claseBotonPrimario, claseBotonSecundario, claseInput } from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { useSesion } from '../sesion/Sesion'

/** Alta y edición de un cliente. Lo que propone la IA es solo una sugerencia: el asesor lo revisa y lo confirma. */
export function ClienteForm() {
  const { id } = useParams()
  const edicion = id != null
  const navegar = useNavigate()
  const { sistema } = useSesion()

  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [telefono, setTelefono] = useState('')
  const [ingreso, setIngreso] = useState<number | null>(null)
  const [ahorro, setAhorro] = useState<number | null>(null)
  const [deudas, setDeudas] = useState<number | null>(null)
  const [intencion, setIntencion] = useState<Intencion>('COMPRAR')
  const [habitaciones, setHabitaciones] = useState(0)
  const [zonas, setZonas] = useState('')
  const [preferencias, setPreferencias] = useState('')
  const [autorizacion, setAutorizacion] = useState(false)

  const [textoLibre, setTextoLibre] = useState('')
  const [extrayendo, setExtrayendo] = useState(false)
  const [sugerencia, setSugerencia] = useState(false)
  const [errorIa, setErrorIa] = useState<string | null>(null)

  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!edicion) return
    api<Cliente>(`/clientes/${id}`).then((c) => {
      setNombre(c.nombre); setEmail(c.email ?? ''); setTelefono(c.telefono ?? '')
      setIngreso(c.ingresoFamiliar); setAhorro(c.ahorro); setDeudas(c.deudasMensuales)
      setIntencion(c.intencion); setHabitaciones(c.habitacionesMin); setZonas(c.zonas.join(', '))
      setPreferencias(c.preferencias)
    }).catch((e) => setError(e.message))
  }, [edicion, id])

  const extraer = async () => {
    setExtrayendo(true)
    setErrorIa(null)
    try {
      const r = await api<{ perfil: PerfilSugerido }>('/clientes/extraer-perfil', {
        method: 'POST', body: JSON.stringify({ texto: textoLibre }),
      })
      const p = r.perfil
      if (p.nombre) setNombre(p.nombre)
      if (p.ingresoFamiliar != null) setIngreso(p.ingresoFamiliar)
      if (p.ahorro != null) setAhorro(p.ahorro)
      if (p.deudasMensuales != null) setDeudas(p.deudasMensuales)
      if (p.intencion) setIntencion(p.intencion)
      if (p.habitacionesMin != null) setHabitaciones(p.habitacionesMin)
      if (p.zonas.length > 0) setZonas(p.zonas.join(', '))
      if (p.preferencias) setPreferencias(p.preferencias)
      if (p.email) setEmail(p.email)
      if (p.telefono) setTelefono(p.telefono)
      setSugerencia(true)
    } catch (e) {
      setErrorIa(e instanceof ErrorApi ? e.message : 'No se pudo leer el perfil con la IA.')
    } finally {
      setExtrayendo(false)
    }
  }

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setGuardando(true)
    setError(null)
    const cuerpo = {
      nombre, email, telefono, ingresoFamiliar: ingreso ?? 0, ahorro: ahorro ?? 0, deudasMensuales: deudas ?? 0,
      intencion, habitacionesMin: habitaciones,
      zonas: zonas.split(',').map((z) => z.trim()).filter(Boolean), preferencias,
      autorizacionDatos: edicion ? undefined : autorizacion,
    }
    try {
      const guardado = await api<Cliente>(edicion ? `/clientes/${id}` : '/clientes', {
        method: edicion ? 'PUT' : 'POST', body: JSON.stringify(cuerpo),
      })
      navegar(`/clientes/${guardado.id}`)
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo guardar el cliente.')
      setGuardando(false)
    }
  }

  const puedeGuardar = nombre.trim() !== '' && ingreso != null && (edicion || autorizacion) && !guardando

  return (
    <div className="mx-auto max-w-2xl">
      <Link to={edicion ? `/clientes/${id}` : '/clientes'} className="text-sm text-niebla hover:text-tinta">← Volver</Link>
      <h1 className="ancha mt-2 text-[30px] font-semibold leading-tight md:text-4xl">
        {edicion ? 'Editar cliente' : 'Nuevo cliente'}
      </h1>

      {!edicion && (
        <section className="mt-6 rounded-panel border border-linea bg-white p-5" aria-labelledby="titulo-ia">
          <h2 id="titulo-ia" className="text-lg font-semibold">Lo que dijo el cliente</h2>
          <p className="mt-1 text-sm text-niebla">
            Pega o dicta lo que te contó y Predia propone el perfil. Tú lo revisas antes de guardar.
          </p>
          <textarea value={textoLibre} onChange={(e) => setTextoLibre(e.target.value)} rows={4}
            placeholder="Ganan 7,5 millones entre los dos, tienen 90 millones ahorrados y buscan en Floridablanca…"
            className={`${claseInput} mt-3`} />
          <div className="mt-3 flex items-center gap-3">
            <button type="button" onClick={() => void extraer()}
              disabled={extrayendo || textoLibre.trim().length < 10 || sistema?.iaDisponible === false}
              className={claseBotonSecundario}>
              <span className="flex items-center gap-2"><Icono nombre="enviar" className="size-4" />
                {extrayendo ? 'Leyendo…' : 'Leer con IA'}</span>
            </button>
            {sistema?.iaDisponible === false && (
              <span className="text-sm text-ambar">Modo manual: llena el formulario a mano.</span>
            )}
          </div>
          {errorIa && <div className="mt-3"><Aviso tipo="error">{errorIa}</Aviso></div>}
        </section>
      )}

      <form onSubmit={guardar} className="mt-6 space-y-5">
        {sugerencia && (
          <Aviso tipo="info">
            Estos datos los propuso la IA a partir de lo que pegaste. Revísalos: nada se guarda hasta que confirmes.
          </Aviso>
        )}

        <Campo etiqueta="Nombre">
          {(idc) => <input id={idc} value={nombre} onChange={(e) => setNombre(e.target.value)} required className={claseInput} />}
        </Campo>
        <div className="grid gap-5 sm:grid-cols-2">
          <Campo etiqueta="Correo (opcional)">
            {(idc) => <input id={idc} type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={claseInput} />}
          </Campo>
          <Campo etiqueta="Teléfono (opcional)">
            {(idc) => <input id={idc} value={telefono} onChange={(e) => setTelefono(e.target.value)} className={claseInput} />}
          </Campo>
        </div>

        <div className="grid gap-5 sm:grid-cols-3">
          <CampoPesos etiqueta="Ingreso familiar al mes" valor={ingreso} onChange={setIngreso} />
          <CampoPesos etiqueta="Ahorro" valor={ahorro} onChange={setAhorro} />
          <CampoPesos etiqueta="Deudas al mes" valor={deudas} onChange={setDeudas}
            ayuda="Se muestran como alerta; no se restan de la cuota máxima." />
        </div>

        <div className="grid gap-5 sm:grid-cols-2">
          <Campo etiqueta="Quiere">
            {(idc) => (
              <select id={idc} value={intencion} onChange={(e) => setIntencion(e.target.value as Intencion)} className={claseInput}>
                <option value="COMPRAR">Comprar</option>
                <option value="ARRENDAR">Arrendar</option>
              </select>
            )}
          </Campo>
          <Campo etiqueta="Habitaciones mínimas">
            {(idc) => (
              <input id={idc} type="number" min={0} max={10} value={habitaciones}
                onChange={(e) => setHabitaciones(Number(e.target.value))} className={claseInput} />
            )}
          </Campo>
        </div>

        <Campo etiqueta="Zonas" ayuda="Barrios o municipios, separados por coma.">
          {(idc) => <input id={idc} value={zonas} onChange={(e) => setZonas(e.target.value)} placeholder="Floridablanca, Cañaveral" className={claseInput} />}
        </Campo>
        <Campo etiqueta="Qué busca">
          {(idc) => (
            <textarea id={idc} rows={3} value={preferencias} onChange={(e) => setPreferencias(e.target.value)}
              placeholder="Parqueadero, que acepte mascotas, cerca de colegios…" className={claseInput} />
          )}
        </Campo>

        {!edicion && (
          <label className="flex items-start gap-3 rounded-panel border border-linea bg-white p-4 text-sm text-grafito">
            <input type="checkbox" checked={autorizacion} onChange={(e) => setAutorizacion(e.target.checked)}
              className="mt-0.5 size-4 accent-[var(--color-cobalto)]" />
            <span>
              El cliente <strong className="text-tinta">autorizó el tratamiento de sus datos personales</strong> (Ley 1581
              de 2012). Sin esta autorización Predia no guarda el perfil.
            </span>
          </label>
        )}

        {error && <Aviso tipo="error">{error}</Aviso>}

        <button type="submit" disabled={!puedeGuardar} className={`${claseBotonPrimario} w-full sm:w-auto`}>
          {guardando ? 'Guardando…' : edicion ? 'Guardar cambios' : 'Guardar cliente'}
        </button>
      </form>
    </div>
  )
}
