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
