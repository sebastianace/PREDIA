import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { EstadoInmueble, Inmueble } from '../api/tipos'
import { Aviso, Campo, CampoPesos, claseBotonPrimario, claseInput } from '../componentes/Formulario'
import { GaleriaInmueble } from '../componentes/GaleriaInmueble'

const tipos = ['APARTAMENTO', 'CASA', 'LOCAL', 'OFICINA', 'LOTE'] as const
const nombreTipo: Record<string, string> = { APARTAMENTO: 'Apartamento', CASA: 'Casa', LOCAL: 'Local', OFICINA: 'Oficina', LOTE: 'Lote' }
const estados: EstadoInmueble[] = ['DISPONIBLE', 'SEPARADO', 'ARRENDADO', 'VENDIDO']
const nombreEstado: Record<EstadoInmueble, string> = { DISPONIBLE: 'Disponible', SEPARADO: 'Separado', ARRENDADO: 'Arrendado', VENDIDO: 'Vendido' }

/** Alta y edición de un inmueble. Al guardar, Predia recalcula el vector con el que se ordena la búsqueda. */
export function InmuebleForm() {
  const { id } = useParams()
  const edicion = id != null
  const navegar = useNavigate()
  const { state } = useLocation() as { state: { aviso?: string; info?: string } | null }

  const [codigo, setCodigo] = useState('')
  const [titulo, setTitulo] = useState('')
  const [tipo, setTipo] = useState<string>('APARTAMENTO')
  const [operacion, setOperacion] = useState<'VENTA' | 'ARRIENDO'>('VENTA')
  const [direccion, setDireccion] = useState('')
  const [barrio, setBarrio] = useState('')
  const [ciudad, setCiudad] = useState('Bucaramanga')
  const [estrato, setEstrato] = useState(3)
  const [area, setArea] = useState(60)
  const [habitaciones, setHabitaciones] = useState(2)
  const [banos, setBanos] = useState(1)
  const [parqueaderos, setParqueaderos] = useState(0)
  const [mascotas, setMascotas] = useState(false)
  const [administracion, setAdministracion] = useState<number | null>(0)
  const [precio, setPrecio] = useState<number | null>(null)
  const [esVis, setEsVis] = useState(false)
  const [ph, setPh] = useState(true)
  const [descripcion, setDescripcion] = useState('')
  const [estado, setEstado] = useState<EstadoInmueble>('DISPONIBLE')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!edicion) return
    api<Inmueble>(`/inmuebles/${id}`).then((i) => {
      setCodigo(i.codigo); setTitulo(i.titulo); setTipo(i.tipo); setOperacion(i.operacion)
      setDireccion(i.direccion); setBarrio(i.barrio); setCiudad(i.ciudad); setEstrato(i.estrato ?? 3)
      setArea(i.areaM2); setHabitaciones(i.habitaciones); setBanos(i.banos); setParqueaderos(i.parqueaderos)
      setMascotas(i.aceptaMascotas); setAdministracion(i.valorAdministracion)
      setPrecio(i.operacion === 'VENTA' ? i.precioVenta : i.canonArriendo)
      setEsVis(i.esVis); setPh(i.propiedadHorizontal); setDescripcion(i.descripcion); setEstado(i.estado)
    }).catch((e) => setError(e.message))
  }, [edicion, id])

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setGuardando(true)
    setError(null)
    const cuerpo = {
      codigo, titulo, tipo, direccion, barrio, ciudad, estrato, areaM2: area, habitaciones, banos, parqueaderos,
      aceptaMascotas: mascotas, valorAdministracion: administracion ?? 0, operacion,
      precioVenta: operacion === 'VENTA' ? precio : null, canonArriendo: operacion === 'ARRIENDO' ? precio : null,
      esVis, propiedadHorizontal: ph, descripcion, estado,
    }
    try {
      const guardado = await api<Inmueble>(edicion ? `/inmuebles/${id}` : '/inmuebles', {
        method: edicion ? 'PUT' : 'POST', body: JSON.stringify(cuerpo),
      })
      // Un inmueble nuevo pasa a su edición: ahí se agregan las fotos y planos, que ya necesitan su id.
      if (!edicion) {
        navegar(`/inventario/${guardado.id}/editar`, { state: { info: 'Inmueble creado. Ahora agrega sus fotos y planos.' } })
        setGuardando(false)
        return
      }
      navegar('/inventario')
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo guardar el inmueble.')
      setGuardando(false)
    }
  }

  const numero = (valor: number, set: (n: number) => void, min = 0, max = 30) => (idc: string) => (
    <input id={idc} type="number" min={min} max={max} value={valor} onChange={(e) => set(Number(e.target.value))} className={claseInput} />
  )

  return (
    <div className="mx-auto max-w-2xl">
      <Link to="/inventario" className="text-sm text-niebla hover:text-tinta">← Inventario</Link>
      <h1 className="ancha mt-2 text-[32px] font-semibold leading-[1.05] md:text-5xl">
        {edicion ? 'Editar inmueble' : 'Nuevo inmueble'}
      </h1>

      {state?.aviso && <div className="mt-4"><Aviso tipo="error">{state.aviso}</Aviso></div>}
      {state?.info && <div className="mt-4"><Aviso tipo="info">{state.info}</Aviso></div>}

      <form onSubmit={guardar} className="mt-6 space-y-5">
        {edicion
          ? <GaleriaInmueble inmuebleId={Number(id)} />
          : <p className="rounded-panel border border-dashed border-linea bg-white px-4 py-5 text-sm text-niebla">Guarda el inmueble para agregar sus fotos y planos.</p>}

        <div className="grid gap-5 sm:grid-cols-2">
          <Campo etiqueta="Código">{(idc) => <input id={idc} value={codigo} onChange={(e) => setCodigo(e.target.value)} required className={claseInput} />}</Campo>
          <Campo etiqueta="Estado">
            {(idc) => (
              <select id={idc} value={estado} onChange={(e) => setEstado(e.target.value as EstadoInmueble)} className={claseInput}>
                {estados.map((s) => <option key={s} value={s}>{nombreEstado[s]}</option>)}
              </select>
            )}
          </Campo>
        </div>
        <Campo etiqueta="Título">{(idc) => <input id={idc} value={titulo} onChange={(e) => setTitulo(e.target.value)} required className={claseInput} />}</Campo>

        <div className="grid gap-5 sm:grid-cols-3">
          <Campo etiqueta="Tipo">
            {(idc) => (
              <select id={idc} value={tipo} onChange={(e) => setTipo(e.target.value)} className={claseInput}>
                {tipos.map((t) => <option key={t} value={t}>{nombreTipo[t]}</option>)}
              </select>
            )}
          </Campo>
          <Campo etiqueta="Operación">
            {(idc) => (
              <select id={idc} value={operacion} onChange={(e) => setOperacion(e.target.value as 'VENTA' | 'ARRIENDO')} className={claseInput}>
                <option value="VENTA">Venta</option>
                <option value="ARRIENDO">Arriendo</option>
              </select>
            )}
          </Campo>
          <CampoPesos etiqueta={operacion === 'VENTA' ? 'Precio de venta' : 'Canon mensual'} valor={precio} onChange={setPrecio} />
        </div>

        <Campo etiqueta="Dirección">{(idc) => <input id={idc} value={direccion} onChange={(e) => setDireccion(e.target.value)} required className={claseInput} />}</Campo>
        <div className="grid gap-5 sm:grid-cols-2">
          <Campo etiqueta="Barrio">{(idc) => <input id={idc} value={barrio} onChange={(e) => setBarrio(e.target.value)} required className={claseInput} />}</Campo>
          <Campo etiqueta="Ciudad">{(idc) => <input id={idc} value={ciudad} onChange={(e) => setCiudad(e.target.value)} required className={claseInput} />}</Campo>
        </div>

        <div className="grid grid-cols-2 gap-5 sm:grid-cols-5">
          <Campo etiqueta="Estrato">{numero(estrato, setEstrato, 1, 6)}</Campo>
          <Campo etiqueta="Área (m²)">{numero(area, setArea, 1, 100000)}</Campo>
          <Campo etiqueta="Habitaciones">{numero(habitaciones, setHabitaciones)}</Campo>
          <Campo etiqueta="Baños">{numero(banos, setBanos)}</Campo>
          <Campo etiqueta="Parqueaderos">{numero(parqueaderos, setParqueaderos)}</Campo>
        </div>

        <CampoPesos etiqueta="Administración al mes" valor={administracion} onChange={setAdministracion} />

        <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-grafito">
          {([['Acepta mascotas', mascotas, setMascotas], ['Propiedad horizontal', ph, setPh], ['Vivienda de interés social (VIS)', esVis, setEsVis]] as const).map(([etiqueta, valor, set]) => (
            <label key={etiqueta} className="flex items-center gap-2">
              <input type="checkbox" checked={valor} onChange={(e) => set(e.target.checked)} className="size-4 accent-[var(--color-cobalto)]" />
              {etiqueta}
            </label>
          ))}
        </div>

        <Campo etiqueta="Descripción" ayuda="Es lo que lee la búsqueda por similitud: cuéntalo como se lo contarías a un cliente.">
          {(idc) => <textarea id={idc} rows={5} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} className={claseInput} />}
        </Campo>

        {error && <Aviso tipo="error">{error}</Aviso>}
        <button type="submit" disabled={guardando || precio == null} className={`${claseBotonPrimario} w-full sm:w-auto`}>
          {guardando ? 'Guardando…' : edicion ? 'Guardar cambios' : 'Crear inmueble'}
        </button>
      </form>
    </div>
  )
}
