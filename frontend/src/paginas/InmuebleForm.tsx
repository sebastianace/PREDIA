import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { EstadoInmueble, FotoInmueble, Inmueble } from '../api/tipos'
import { Aviso, Campo, CampoPesos, claseBotonPrimario, claseInput } from '../componentes/Formulario'
import { SelectorFoto, type CambioFoto } from '../componentes/SelectorFoto'

const tipos = ['APARTAMENTO', 'CASA', 'LOCAL', 'OFICINA', 'LOTE'] as const
const nombreTipo: Record<string, string> = { APARTAMENTO: 'Apartamento', CASA: 'Casa', LOCAL: 'Local', OFICINA: 'Oficina', LOTE: 'Lote' }
const estados: EstadoInmueble[] = ['DISPONIBLE', 'SEPARADO', 'ARRENDADO', 'VENDIDO']
const nombreEstado: Record<EstadoInmueble, string> = { DISPONIBLE: 'Disponible', SEPARADO: 'Separado', ARRENDADO: 'Arrendado', VENDIDO: 'Vendido' }

/** Alta y edición de un inmueble. Al guardar, Predia recalcula el vector con el que se ordena la búsqueda. */
export function InmuebleForm() {
  const { id } = useParams()
  const edicion = id != null
  const navegar = useNavigate()
  const { state } = useLocation() as { state: { aviso?: string } | null }

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
  const [foto, setFoto] = useState<FotoInmueble | null>(null)
  const [cambioFoto, setCambioFoto] = useState<CambioFoto | null>(null)
  const alCambiarFoto = useCallback((c: CambioFoto) => setCambioFoto(c), [])
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
      setEsVis(i.esVis); setPh(i.propiedadHorizontal); setDescripcion(i.descripcion); setEstado(i.estado); setFoto(i.foto)
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
      // El inmueble ya quedó guardado; la foto va aparte para que un fallo suyo no pierda lo demás.
      try {
        await guardarFoto(guardado.id)
      } catch (errFoto) {
        const motivo = errFoto instanceof ErrorApi ? errFoto.message : 'No se pudo subir la foto.'
        navegar(`/inventario/${guardado.id}/editar`, { state: { aviso: `El inmueble se guardó, pero la foto no: ${motivo}` } })
        setGuardando(false)
        return
      }
      navegar('/inventario')
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo guardar el inmueble.')
      setGuardando(false)
    }
  }

  const guardarFoto = async (inmuebleId: number) => {
    const c = cambioFoto
    if (!c) return
    if (c.blob) {
      const datos = new FormData()
      datos.append('archivo', c.blob, 'foto.jpg')
      datos.append('focoX', String(c.focoX))
      datos.append('focoY', String(c.focoY))
      await api(`/inmuebles/${inmuebleId}/foto`, { method: 'PUT', body: datos })
    } else if (c.quitar && foto) {
      await api(`/inmuebles/${inmuebleId}/foto`, { method: 'DELETE' })
    } else if (c.focoCambiado && foto) {
      await api(`/inmuebles/${inmuebleId}/foto/foco`, { method: 'PATCH', body: JSON.stringify({ focoX: c.focoX, focoY: c.focoY }) })
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

      <form onSubmit={guardar} className="mt-6 space-y-5">
        <SelectorFoto inmuebleId={edicion ? Number(id) : null} foto={foto} titulo={titulo} onCambio={alCambiarFoto} />

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
