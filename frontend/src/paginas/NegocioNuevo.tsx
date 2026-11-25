import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ErrorApi } from '../api/cliente'
import type { Cliente, EstadoCivil, HipotecaVigente, Inmueble, Negocio, Propietario, TipoPersona } from '../api/tipos'
import { Avatar } from '../componentes/Avatar'
import { Autorizacion, Aviso, Campo, Segmentado, claseBotonPrimario, claseInput } from '../componentes/Formulario'
import { PanelAzul } from '../componentes/PanelAzul'

const estadosCiviles: [EstadoCivil, string][] = [
  ['SOLTERO', 'Soltero(a)'], ['CASADO', 'Casado(a)'], ['UNION_MARITAL', 'En unión marital'],
  ['DIVORCIADO', 'Divorciado(a)'], ['VIUDO', 'Viudo(a)'],
]

const tiposDocumento: Record<TipoPersona, [string, string][]> = {
  NATURAL: [['CC', 'CC'], ['CE', 'CE'], ['PASAPORTE', 'Pasaporte']],
  JURIDICA: [['NIT', 'NIT']],
}
const textoEstadoCivil = Object.fromEntries(estadosCiviles) as Record<EstadoCivil, string>

/** Abre un negocio: inmueble en venta + vendedor (existente o nuevo, con su autorización de datos) + comprador. */
export function NegocioNuevo() {
  const navegar = useNavigate()
  const [inmuebles, setInmuebles] = useState<Inmueble[]>([])
  const [propietarios, setPropietarios] = useState<Propietario[]>([])
  const [clientes, setClientes] = useState<Cliente[]>([])

  const [inmuebleId, setInmuebleId] = useState('')
  const [propietarioId, setPropietarioId] = useState('')
  const [clienteId, setClienteId] = useState('')
  const [fechaFirma, setFechaFirma] = useState('')
  const [apoderado, setApoderado] = useState(false)
  const [hipoteca, setHipoteca] = useState<HipotecaVigente>('DESCONOCIDO')

  const [nuevoVendedor, setNuevoVendedor] = useState(false)
  const [tipoPersona, setTipoPersona] = useState<TipoPersona>('NATURAL')
  const [nombre, setNombre] = useState('')
  const [tipoDocumento, setTipoDocumento] = useState('CC')
  const [numeroDocumento, setNumeroDocumento] = useState('')
  const [email, setEmail] = useState('')
  const [telefono, setTelefono] = useState('')
  const [estadoCivil, setEstadoCivil] = useState<EstadoCivil>('SOLTERO')
  const [autorizacion, setAutorizacion] = useState(false)

  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Inmueble[]>('/inmuebles').then((l) => setInmuebles(l.filter((i) => i.operacion === 'VENTA' && i.estado !== 'VENDIDO')))
    api<Propietario[]>('/propietarios').then(setPropietarios)
    api<Cliente[]>('/clientes').then(setClientes)
  }, [])

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setGuardando(true)
    setError(null)
    try {
      let idVendedor = propietarioId
      if (nuevoVendedor) {
        const creado = await api<Propietario>('/propietarios', { method: 'POST', body: JSON.stringify({
          tipoPersona, nombre, tipoDocumento, numeroDocumento, email, telefono,
          estadoCivil: tipoPersona === 'NATURAL' ? estadoCivil : null, autorizacionDatos: autorizacion }) })
        idVendedor = String(creado.id)
      }
      const negocio = await api<Negocio>('/negocios', { method: 'POST', body: JSON.stringify({
        inmuebleId: Number(inmuebleId), propietarioId: Number(idVendedor),
        clienteCompradorId: clienteId ? Number(clienteId) : null, fechaFirmaTentativa: fechaFirma || null,
        actuaPorApoderado: apoderado, hipotecaVigente: hipoteca }) })
      navegar(`/negocios/${negocio.id}`)
    } catch (err) {
      setError(err instanceof ErrorApi ? err.message : 'No se pudo abrir el negocio.')
      setGuardando(false)
    }
  }

  const vendedorListo = nuevoVendedor
    ? nombre.trim() !== '' && numeroDocumento.trim() !== '' && autorizacion
    : propietarioId !== ''
  const puedeGuardar = inmuebleId !== '' && vendedorListo && !guardando

  const existente = propietarios.find((p) => String(p.id) === propietarioId)
  const juridica = tipoPersona === 'JURIDICA'
  const casado = !juridica && (estadoCivil === 'CASADO' || estadoCivil === 'UNION_MARITAL')
  const cambiarTipo = (t: TipoPersona) => { setTipoPersona(t); setTipoDocumento(t === 'JURIDICA' ? 'NIT' : 'CC') }

  // Lo que se ve en el panel azul mientras se llena el formulario: cómo quedará el vendedor en el negocio.
  const vista = nuevoVendedor
    ? { nombre: nombre.trim(), documento: numeroDocumento.trim() ? `${tipoDocumento} ${numeroDocumento.trim()}` : '', tipo: juridica ? 'Persona jurídica' : `Persona natural · ${textoEstadoCivil[estadoCivil].toLowerCase()}` }
    : existente
      ? { nombre: existente.nombre, documento: `${existente.tipoDocumento} ${existente.numeroDocumento}`,
          tipo: existente.tipoPersona === 'JURIDICA' ? 'Persona jurídica' : `Persona natural${existente.estadoCivil ? ` · ${textoEstadoCivil[existente.estadoCivil].toLowerCase()}` : ''}` }
      : { nombre: '', documento: '', tipo: '' }

  return (
    <div className="mx-auto max-w-4xl">
      <Link to="/negocios" className="text-sm text-niebla hover:text-tinta">← Negocios</Link>
      <h1 className="ancha mt-2 text-[32px] font-semibold leading-[1.05] md:text-5xl">Nuevo negocio</h1>

      <form onSubmit={guardar} className="mt-6 space-y-5">
        <div className="lg:pr-[17.5rem]">
          <Campo etiqueta="Inmueble en venta">
            {(id) => (
              <select id={id} value={inmuebleId} onChange={(e) => setInmuebleId(e.target.value)} required className={claseInput}>
                <option value="">Elige un inmueble…</option>
                {inmuebles.map((i) => <option key={i.id} value={i.id}>{i.titulo} · {i.codigo}</option>)}
              </select>
            )}
          </Campo>
        </div>

        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_16.5rem] lg:items-start">
          <aside className="lg:order-2 lg:sticky lg:top-6" aria-label="Vista del vendedor">
            <PanelAzul semilla={31} className="rounded-panel p-4">
              <div className="relative">
                <Avatar nombre={vista.nombre || 'Vendedor'} tono="blanco" tamano="enorme" />
                <div className="rotulo mt-2 text-sm text-trazo">Vendedor</div>
                <div className="ancha break-words text-lg font-semibold">
                  {vista.nombre || (nuevoVendedor ? (juridica ? 'Sin razón social aún' : 'Sin nombre aún') : 'Elige un propietario')}
                </div>
                {vista.documento && <div className="text-sm text-trazo">{vista.documento}</div>}
                {vista.tipo && <div className="text-sm text-trazo">{vista.tipo}</div>}
                <p className="mt-3 text-xs leading-snug text-trazo">Así queda el vendedor en el negocio mientras llenas el formulario.</p>
              </div>
            </PanelAzul>
          </aside>

          <fieldset className="space-y-4 rounded-panel border border-linea bg-white p-4 md:p-5 lg:order-1">
            <legend className="sr-only">Vendedor</legend>
            <h2 className="text-base font-semibold text-tinta" aria-hidden="true">Vendedor</h2>
            <Segmentado etiqueta="¿Quién vende?" valor={nuevoVendedor ? 'nuevo' : 'existente'}
              opciones={[{ valor: 'existente', texto: 'Elegir uno existente' }, { valor: 'nuevo', texto: 'Registrar uno nuevo' }]}
              onChange={(v) => setNuevoVendedor(v === 'nuevo')} />
            {!nuevoVendedor ? (
              <Campo etiqueta="Propietario" ayuda={propietarios.length === 0 ? 'Todavía no hay propietarios: registra uno nuevo.' : undefined}>
                {(id) => (
                  <select id={id} value={propietarioId} onChange={(e) => setPropietarioId(e.target.value)} className={claseInput}>
                    <option value="">Elige un propietario…</option>
                    {propietarios.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                  </select>
                )}
              </Campo>
            ) : (
              <>
                <Segmentado etiqueta="Tipo de persona" valor={tipoPersona}
                  opciones={[{ valor: 'NATURAL', texto: 'Persona natural' }, { valor: 'JURIDICA', texto: 'Persona jurídica' }]}
                  onChange={cambiarTipo} />
                <div className="grid gap-4 sm:grid-cols-2">
                  <Campo etiqueta={juridica ? 'Razón social' : 'Nombre completo'}>
                    {(id) => <input id={id} value={nombre} onChange={(e) => setNombre(e.target.value)} className={claseInput} />}
                  </Campo>
                  <Campo etiqueta="Documento">
                    {(id) => (
                      <div className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-2">
                        <select aria-label="Tipo de documento" value={tipoDocumento} onChange={(e) => setTipoDocumento(e.target.value)} className={claseInput}>
                          {tiposDocumento[tipoPersona].map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
                        </select>
                        <input id={id} inputMode={juridica ? 'text' : 'numeric'} value={numeroDocumento}
                          placeholder={juridica ? '900.123.456-7' : ''} onChange={(e) => setNumeroDocumento(e.target.value)} className={claseInput} />
                      </div>
                    )}
                  </Campo>
                  <Campo etiqueta="Correo">
                    {(id) => <input id={id} type="email" value={email} placeholder={juridica ? 'correo@empresa.com' : ''} onChange={(e) => setEmail(e.target.value)} className={claseInput} />}
                  </Campo>
                  <Campo etiqueta="Teléfono">
                    {(id) => <input id={id} value={telefono} onChange={(e) => setTelefono(e.target.value)} className={claseInput} />}
                  </Campo>
                </div>
                <p className="-mt-1 text-xs text-niebla">A este correo se envían los recordatorios, solo con tu aprobación.</p>
                {!juridica && (
                  <div>
                    <div className="max-w-xs">
                      <Campo etiqueta="Estado civil">
                        {(id) => (
                          <select id={id} value={estadoCivil} onChange={(e) => setEstadoCivil(e.target.value as EstadoCivil)} className={claseInput}>
                            {estadosCiviles.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
                          </select>
                        )}
                      </Campo>
                    </div>
                    {casado && (
                      <p role="note" className="mt-2 border-l-2 border-ambar bg-ambar-suave px-3 py-2 text-sm text-grafito">
                        Si está casado o en unión marital, se exige la firma o la declaración del cónyuge. Predia lo agrega a los documentos del cierre.
                      </p>
                    )}
                  </div>
                )}
                <div className="border-t border-linea pt-4">
                  <Autorizacion quien="El vendedor" marcado={autorizacion} onChange={setAutorizacion} />
                </div>
              </>
            )}
          </fieldset>
        </div>

        <div className="space-y-5 lg:pr-[17.5rem]">
          <Campo etiqueta="Cliente comprador (opcional)">
            {(id) => (
              <select id={id} value={clienteId} onChange={(e) => setClienteId(e.target.value)} className={claseInput}>
                <option value="">Todavía no hay comprador</option>
                {clientes.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
              </select>
            )}
          </Campo>

          <div className="grid gap-5 sm:grid-cols-2">
            <Campo etiqueta="Fecha tentativa de firma" ayuda="Con ella Predia avisa qué documentos vencen antes de firmar.">
              {(id) => <input id={id} type="date" value={fechaFirma} onChange={(e) => setFechaFirma(e.target.value)} className={claseInput} />}
            </Campo>
            <Campo etiqueta="¿El inmueble tiene hipoteca vigente?" ayuda="Se ve en el certificado de tradición y libertad.">
              {(id) => (
                <select id={id} value={hipoteca} onChange={(e) => setHipoteca(e.target.value as HipotecaVigente)} className={claseInput}>
                  <option value="DESCONOCIDO">Aún no lo sé</option>
                  <option value="NO">No</option>
                  <option value="SI">Sí</option>
                </select>
              )}
            </Campo>
          </div>

          <label className="flex items-center gap-2 text-sm text-grafito">
            <input type="checkbox" checked={apoderado} onChange={(e) => setApoderado(e.target.checked)} className="size-4 accent-[var(--color-cobalto)]" />
            El vendedor actúa por medio de apoderado
          </label>

          {error && <Aviso tipo="error">{error}</Aviso>}
          <button type="submit" disabled={!puedeGuardar} className={`${claseBotonPrimario} w-full sm:w-auto`}>
            {guardando ? 'Abriendo…' : 'Abrir negocio'}
          </button>
        </div>
      </form>
    </div>
  )
}
