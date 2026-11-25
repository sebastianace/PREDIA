import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { Cliente, Intencion, PerfilSugerido } from '../api/tipos'
import {
  Aviso, Autorizacion, Campo, CampoEtiquetas, CampoPesos, Contador, Segmentado, SeccionNumerada,
  claseBotonPrimario, claseBotonSecundario, claseInput,
} from '../componentes/Formulario'
import { Icono } from '../componentes/Iconos'
import { PanelAzul } from '../componentes/PanelAzul'
import { useSesion } from '../sesion/Sesion'

type CampoIa = 'nombre' | 'email' | 'telefono' | 'ingreso' | 'ahorro' | 'deudas' | 'intencion' | 'habitaciones' | 'zonas' | 'preferencias'

function Requisito({ cumplido, texto }: { cumplido: boolean; texto: string }) {
  return (
    <li className={`flex items-center gap-2 text-sm ${cumplido ? 'text-white' : 'text-trazo'}`}>
      <Icono nombre={cumplido ? 'circuloCheck' : 'circulo'} className="size-4 shrink-0" />{texto}
    </li>
  )
}

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
  const [zonas, setZonas] = useState<string[]>([])
  const [preferencias, setPreferencias] = useState('')
  const [autorizacion, setAutorizacion] = useState(false)

  const [textoLibre, setTextoLibre] = useState('')
  const [extrayendo, setExtrayendo] = useState(false)
  const [sugeridos, setSugeridos] = useState<Set<CampoIa>>(new Set())
  const [errorIa, setErrorIa] = useState<string | null>(null)

  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!edicion) return
    api<Cliente>(`/clientes/${id}`).then((c) => {
      setNombre(c.nombre); setEmail(c.email ?? ''); setTelefono(c.telefono ?? '')
      setIngreso(c.ingresoFamiliar); setAhorro(c.ahorro); setDeudas(c.deudasMensuales)
      setIntencion(c.intencion); setHabitaciones(c.habitacionesMin); setZonas(c.zonas)
      setPreferencias(c.preferencias)
    }).catch((e) => setError(e.message))
  }, [edicion, id])

  /** El asesor tocó el campo: deja de ser una sugerencia de la IA. */
  const editado = (campo: CampoIa) => setSugeridos((s) => { const n = new Set(s); n.delete(campo); return n })
  const es = (campo: CampoIa) => sugeridos.has(campo)

  const extraer = async () => {
    setExtrayendo(true)
    setErrorIa(null)
    try {
      const r = await api<{ perfil: PerfilSugerido }>('/clientes/extraer-perfil', {
        method: 'POST', body: JSON.stringify({ texto: textoLibre }),
      })
      const p = r.perfil
      const nuevos = new Set<CampoIa>()
      if (p.nombre) { setNombre(p.nombre); nuevos.add('nombre') }
      if (p.ingresoFamiliar != null) { setIngreso(p.ingresoFamiliar); nuevos.add('ingreso') }
      if (p.ahorro != null) { setAhorro(p.ahorro); nuevos.add('ahorro') }
      if (p.deudasMensuales != null) { setDeudas(p.deudasMensuales); nuevos.add('deudas') }
      if (p.intencion) { setIntencion(p.intencion); nuevos.add('intencion') }
      if (p.habitacionesMin != null) { setHabitaciones(p.habitacionesMin); nuevos.add('habitaciones') }
      if (p.zonas.length > 0) { setZonas(p.zonas); nuevos.add('zonas') }
      if (p.preferencias) { setPreferencias(p.preferencias); nuevos.add('preferencias') }
      if (p.email) { setEmail(p.email); nuevos.add('email') }
      if (p.telefono) { setTelefono(p.telefono); nuevos.add('telefono') }
      setSugeridos(nuevos)
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
      intencion, habitacionesMin: habitaciones, zonas, preferencias,
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

  const tieneNombre = nombre.trim() !== ''
  const tieneIngreso = ingreso != null
  const puedeGuardar = tieneNombre && tieneIngreso && (edicion || autorizacion) && !guardando
  const faltan = [!tieneNombre && 'el nombre', !tieneIngreso && 'el ingreso', !edicion && !autorizacion && 'la autorización'].filter(Boolean) as string[]
  const textoFalta = faltan.length === 0 ? '' : `Falta ${faltan.length === 1 ? faltan[0] : `${faltan.slice(0, -1).join(', ')} y ${faltan[faltan.length - 1]}`}.`

  return (
    <div className="mx-auto max-w-4xl">
      <Link to={edicion ? `/clientes/${id}` : '/clientes'} className="text-sm text-niebla hover:text-tinta">← Volver</Link>
      <h1 className="ancha mt-2 text-[32px] font-semibold leading-[1.05] md:text-5xl">
        {edicion ? 'Editar cliente' : 'Nuevo cliente'}
      </h1>

      {!edicion && (
        <section className="mt-6 rounded-panel border border-linea bg-white p-4 md:p-5" aria-labelledby="titulo-ia">
          <h2 id="titulo-ia" className="flex items-center gap-2 text-base font-semibold">
            <Icono nombre="chispa" className="size-5 text-cobalto" />Lo que dijo el cliente
          </h2>
          <div className="mt-3 grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
            <textarea value={textoLibre} onChange={(e) => setTextoLibre(e.target.value)} rows={3} aria-labelledby="titulo-ia"
              placeholder="Ganan 7,5 millones entre los dos, tienen 90 millones ahorrados y buscan en Floridablanca…"
              className={claseInput} />
            <div className="flex sm:items-center">
              <button type="button" onClick={() => void extraer()}
                disabled={extrayendo || textoLibre.trim().length < 10 || sistema?.iaDisponible === false}
                className={claseBotonSecundario}>
                <span className="flex items-center gap-2"><Icono nombre="enviar" className="size-4" />
                  {extrayendo ? 'Leyendo…' : 'Leer con IA'}</span>
              </button>
            </div>
          </div>
          <p className="mt-2 text-sm text-niebla">
            {sugeridos.size > 0
              ? 'Revisa lo sugerido: nada se guarda hasta que confirmes.'
              : 'Pega o dicta lo que te contó: Predia propone el perfil y tú lo revisas antes de guardar.'}
            {sistema?.iaDisponible === false && <span className="ml-2 text-ambar">Modo manual: llena el formulario a mano.</span>}
          </p>
          {errorIa && <div className="mt-3"><Aviso tipo="error">{errorIa}</Aviso></div>}
        </section>
      )}

      <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_16.5rem] lg:items-start">
        <aside className="lg:order-2 lg:sticky lg:top-6" aria-label="Resumen del perfil">
          <PanelAzul semilla={959} className="rounded-panel p-4">
            <div className="relative">
              <div className="rotulo text-sm text-trazo">{edicion ? 'Perfil del cliente' : 'Perfil en construcción'}</div>
              <div className="ancha mt-1 break-words text-lg font-semibold">{tieneNombre ? nombre : 'Sin nombre aún'}</div>
              <div className="mt-0.5 text-sm text-trazo">
                {intencion === 'COMPRAR' ? 'Quiere comprar' : 'Quiere arrendar'}
                {habitaciones > 0 && ` · ${habitaciones}+ hab.`}
              </div>
              {zonas.length > 0 && <div className="text-sm text-trazo">{zonas.join(', ')}</div>}
              <ul className="mt-3 space-y-1.5 border-t border-trazo/35 pt-3">
                <Requisito cumplido={tieneNombre} texto="Nombre" />
                <Requisito cumplido={tieneIngreso} texto="Ingreso familiar" />
                {!edicion && <Requisito cumplido={autorizacion} texto="Autorización de datos" />}
              </ul>
              <p className="mt-3 text-xs leading-snug text-trazo">
                El tope de compra aparece al guardar: lo calculan las reglas de Predia, no la IA.
              </p>
            </div>
          </PanelAzul>
        </aside>

        <form onSubmit={guardar} className="space-y-4 lg:order-1">
          <SeccionNumerada numero={1} titulo="Contacto">
            <div className="space-y-4">
              <Campo etiqueta="Nombre" sugerido={es('nombre')}>
                {(idc) => <input id={idc} value={nombre} required className={claseInput}
                  onChange={(e) => { setNombre(e.target.value); editado('nombre') }} />}
              </Campo>
              <div className="grid gap-4 sm:grid-cols-2">
                <Campo etiqueta="Correo (opcional)" sugerido={es('email')}>
                  {(idc) => <input id={idc} type="email" value={email} placeholder="nombre@correo.com" className={claseInput}
                    onChange={(e) => { setEmail(e.target.value); editado('email') }} />}
                </Campo>
                <Campo etiqueta="Teléfono (opcional)" sugerido={es('telefono')}>
                  {(idc) => <input id={idc} value={telefono} className={claseInput}
                    onChange={(e) => { setTelefono(e.target.value); editado('telefono') }} />}
                </Campo>
              </div>
            </div>
          </SeccionNumerada>

          <SeccionNumerada numero={2} titulo="Situación económica">
            <div className="grid gap-4 sm:grid-cols-3">
              <CampoPesos etiqueta="Ingreso al mes" valor={ingreso} sugerido={es('ingreso')} onChange={(v) => { setIngreso(v); editado('ingreso') }} />
              <CampoPesos etiqueta="Ahorro" valor={ahorro} sugerido={es('ahorro')} onChange={(v) => { setAhorro(v); editado('ahorro') }} />
              <CampoPesos etiqueta="Deudas al mes" valor={deudas} sugerido={es('deudas')} onChange={(v) => { setDeudas(v); editado('deudas') }} />
            </div>
            <p className="mt-2 text-xs text-niebla">
              Con esto Predia calcula cuánto puede comprar. Las deudas se muestran como alerta; no se restan de la cuota máxima.
            </p>
          </SeccionNumerada>

          <SeccionNumerada numero={3} titulo="Qué busca">
            <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
              <Segmentado etiqueta="Quiere" valor={intencion}
                opciones={[{ valor: 'COMPRAR', texto: 'Comprar' }, { valor: 'ARRENDAR', texto: 'Arrendar' }]}
                onChange={(v) => { setIntencion(v); editado('intencion') }} />
              <Contador etiqueta="Habitaciones mínimas" valor={habitaciones} onChange={(v) => { setHabitaciones(v); editado('habitaciones') }} />
            </div>
            <div className="mt-4 space-y-4">
              <CampoEtiquetas etiqueta="Zonas" valores={zonas} sugerido={es('zonas')} placeholder="Escribe un barrio o municipio y pulsa Enter"
                onChange={(v) => { setZonas(v); editado('zonas') }} />
              <Campo etiqueta="Qué busca" sugerido={es('preferencias')}>
                {(idc) => (
                  <textarea id={idc} rows={3} value={preferencias} className={claseInput}
                    placeholder="Parqueadero, que acepte mascotas, cerca de colegios…"
                    onChange={(e) => { setPreferencias(e.target.value); editado('preferencias') }} />
                )}
              </Campo>
            </div>
          </SeccionNumerada>

          {!edicion && (
            <SeccionNumerada numero={4} titulo="Autorización">
              <Autorizacion quien="El cliente" marcado={autorizacion} onChange={setAutorizacion} />
            </SeccionNumerada>
          )}

          {error && <Aviso tipo="error">{error}</Aviso>}

          <div className="sticky bottom-[calc(4.4rem+env(safe-area-inset-bottom))] z-10 -mx-4 flex flex-wrap items-center gap-3 border-t border-linea bg-papel/95 px-4 py-3 backdrop-blur md:static md:mx-0 md:border-0 md:bg-transparent md:p-0 md:backdrop-blur-none">
            <button type="submit" disabled={!puedeGuardar} className={claseBotonPrimario}>
              {guardando ? 'Guardando…' : edicion ? 'Guardar cambios' : 'Guardar cliente'}
            </button>
            <Link to={edicion ? `/clientes/${id}` : '/clientes'} className={claseBotonSecundario}>Cancelar</Link>
            {!puedeGuardar && !guardando && textoFalta && <span className="text-sm text-niebla">{textoFalta}</span>}
          </div>
        </form>
      </div>
    </div>
  )
}
