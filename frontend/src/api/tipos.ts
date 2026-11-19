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
