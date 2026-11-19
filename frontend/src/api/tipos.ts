export type Rol = 'ADMIN' | 'ASESOR'

export interface PerfilUsuario {
  id: number
  nombre: string
  email: string
  rol: Rol
  inmobiliariaId: number
  inmobiliaria: string
}

export interface RespuestaLogin {
  token: string
  expiraEn: string
  usuario: PerfilUsuario
}

export interface EstadoSistema {
  iaDisponible: boolean
  modeloChat: string
  modeloEmbeddings: string
  aviso: string | null
}

export type EstadoInmueble = 'DISPONIBLE' | 'SEPARADO' | 'ARRENDADO' | 'VENDIDO'

export interface Inmueble {
  id: number
  codigo: string
  titulo: string
  tipo: string
  direccion: string
  barrio: string
  ciudad: string
  estrato: number | null
  areaM2: number
  habitaciones: number
  banos: number
  parqueaderos: number
  aceptaMascotas: boolean
  valorAdministracion: number
  operacion: 'VENTA' | 'ARRIENDO'
  precioVenta: number | null
  canonArriendo: number | null
  esVis: boolean
  propiedadHorizontal: boolean
  descripcion: string
  estado: EstadoInmueble
}

export type EstadoIndexacion = 'EN_COLA' | 'PROCESANDO' | 'INDEXADO' | 'ERROR'
export type ClasificacionEstado = 'SIN_CLASIFICAR' | 'SUGERIDA' | 'CONFIRMADA'

export interface Documento {
  id: number
  inmuebleId: number
  negocioId: number | null
  nombreArchivo: string
  tamanoBytes: number
  paginas: number | null
  paginasOcr: number
  estadoIndexacion: EstadoIndexacion
  error: string | null
  indexadoEn: string | null
  vectorizado: boolean
  tipoSugerido: string | null
  fechaExpedicionSugerida: string | null
  confianza: number | null
  clasificacionEstado: ClasificacionEstado
  tipoConfirmado: string | null
  fechaExpedicionConfirmada: string | null
  creadoEn: string
}

export interface Cita {
  numero: number
  documentoId: number
  nombreArchivo: string
  paginaInicio: number
  paginaFin: number
  seccion: string | null
}

export interface RespuestaConsulta {
  respuesta: string | null
  citas: Cita[]
  sinEvidencia: boolean
  modoManual: boolean
}

export type Intencion = 'COMPRAR' | 'ARRENDAR'

export interface Cliente {
  id: number
  nombre: string
  email: string | null
  telefono: string | null
  ingresoFamiliar: number
  ahorro: number
  deudasMensuales: number
  intencion: Intencion
  habitacionesMin: number
  zonas: string[]
  preferencias: string
  autorizacionDatos: boolean
  autorizacionFecha: string
}

/** Lo que la IA propone a partir de lo que dijo el cliente: solo una sugerencia que el asesor confirma. */
export interface PerfilSugerido {
  nombre: string | null
  ingresoFamiliar: number | null
  ahorro: number | null
  deudasMensuales: number | null
  intencion: Intencion | null
  habitacionesMin: number | null
  zonas: string[]
  preferencias: string | null
  email: string | null
  telefono: string | null
}

export type TipoTasa = 'VIS' | 'NO_VIS'

export interface Tasa {
  id: number
  banco: string
  tipo: TipoTasa
  tasaEa: number
  vigenteDesde: string
  fuente: string
  fechaCarga: string
  esEjemplo: boolean
}

export interface PresupuestoCalculado {
  cuotaMaxima: number
  montoQueSoportaLaCuota: number
  topePorAhorro: number
  topePorCapacidadDePago: number
  topeDeCompra: number
  limitante: 'AHORRO' | 'CAPACIDAD_DE_PAGO'
  cuotaInicialMinimaDelTope: number
  endeudamientoConCuotaMaxima: number | null
  alertaEndeudamiento: boolean
}

export interface PresupuestoCliente {
  clienteId: number
  nombre: string
  plazoAnios: number
  vis: boolean
  pctCuotaMaximaIngreso: number
  pctCuotaInicial: number
  tasa: Tasa
  ingresoFamiliar: number
  ahorro: number
  deudasMensuales: number
  presupuesto: PresupuestoCalculado
  avisos: string[]
}

export interface ResultadoInmueble {
  inmueble: Inmueble
  coincidenciaPct: number | null
}

export interface RespuestaBusqueda {
  resultados: ResultadoInmueble[]
  ordenadoPorSimilitud: boolean
  aviso: string | null
}

export interface Simulacion {
  montoFinanciado: number
  plazoAnios: number
  numeroCuotas: number
  cuotaMensual: number
  totalAPagar: number
  totalIntereses: number
  tasaMensual: number
  tasa: Tasa
  cuotaMaxima: number | null
  porcentajeSobreIngreso: number | null
  cumpleCuotaMaxima: boolean | null
  precioInmueble: number | null
  cuotaInicial: number | null
  cuotaInicialMinima: number | null
  cumpleCuotaInicial: boolean | null
  aviso: string
}
