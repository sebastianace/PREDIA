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

export type EstadoRequisito = 'PENDIENTE' | 'SUGERIDO' | 'RECIBIDO' | 'POR_VENCER' | 'VENCIDO' | 'NO_APLICA'
export type HipotecaVigente = 'SI' | 'NO' | 'DESCONOCIDO'
export type EstadoCivil = 'SOLTERO' | 'CASADO' | 'UNION_MARITAL' | 'DIVORCIADO' | 'VIUDO'
export type TipoPersona = 'NATURAL' | 'JURIDICA'

export interface Propietario {
  id: number
  tipoPersona: TipoPersona
  nombre: string
  tipoDocumento: string
  numeroDocumento: string
  email: string | null
  telefono: string | null
  estadoCivil: EstadoCivil | null
  autorizacionDatos: boolean
  autorizacionFecha: string
}

export interface Negocio {
  id: number
  inmuebleId: number
  inmuebleTitulo: string
  propietarioId: number
  propietarioNombre: string
  clienteCompradorId: number | null
  clienteCompradorNombre: string | null
  asesorId: number
  asesorNombre: string | null
  fechaFirmaTentativa: string | null
  actuaPorApoderado: boolean
  hipotecaVigente: HipotecaVigente
  estado: 'ABIERTO' | 'FIRMADO' | 'CANCELADO'
  listoParaFirmar: boolean
  semaforo: string
  faltan: number
  porcentajeAvance: number
}

export interface ItemDocumental {
  id: number
  requisitoId: number
  codigo: string
  nombre: string
  descripcion: string
  obligatorio: boolean
  estado: EstadoRequisito
  detalle: string
  venceEl: string | null
  estadoRegistrado: 'PENDIENTE' | 'SUGERIDO' | 'RECIBIDO' | 'NO_APLICA_MANUAL'
  fechaExpedicion: string | null
  documentoId: number | null
  documentoNombre: string | null
  recibidoPor: string | null
  recibidoEn: string | null
  nota: string | null
  requiereFecha: boolean
}

export interface EstadoDocumental {
  negocioId: number
  listoParaFirmar: boolean
  semaforo: string
  faltan: number
  obligatoriosQueAplican: number
  recibidos: number
  porcentajeAvance: number
  items: ItemDocumental[]
  advertencias: string[]
  aviso: string
}

export type EstadoRecordatorio = 'SUGERIDO' | 'APROBADO' | 'ENVIADO' | 'DESCARTADO' | 'ERROR_ENVIO'

export interface Recordatorio {
  id: number
  negocioId: number
  inmuebleTitulo: string | null
  propietarioNombre: string | null
  destinatarioEmail: string | null
  estado: EstadoRecordatorio
  items: { codigo: string; nombre: string; estado: string; detalle: string; venceEl: string | null }[]
  asunto: string
  cuerpo: string
  cuerpoSugerido: string
  origenTexto: 'IA' | 'PLANTILLA'
  creadoEn: string
  editadoPor: string | null
  aprobadoPor: string | null
  aprobadoEn: string | null
  enviadoEn: string | null
  error: string | null
  advertencias: string[]
}

export interface Requisito {
  id: number
  codigo: string
  nombre: string
  descripcion: string
  orden: number
  activo: boolean
  reglaAplica: string
  obligatorio: boolean
  reglaVigencia: 'NINGUNA' | 'DIAS_DESDE_EXPEDICION' | 'ANIO_EN_CURSO' | 'MES_EN_CURSO'
  vigenciaDias: number | null
  aviso: string
}

export interface ResumenDiario {
  fecha: string
  negociosAbiertos: number
  bloqueados: {
    negocioId: number
    inmueble: string
    propietario: string
    fechaFirma: string | null
    semaforo: string
    faltan: number
    vencidos: number
    porVencer: number
    pendientes: string[]
  }[]
  vencenEstaSemana: { negocioId: number; inmueble: string; documento: string; venceEl: string; diasRestantes: number }[]
  recordatoriosPorAprobar: number
}

export interface CitaAsistente {
  numero: number
  documentoId: number
  nombreArchivo: string
  paginaInicio: number
  paginaFin: number
  seccion: string | null
}

export interface RespuestaAsistente {
  conversacionId: string
  respuesta: string
  herramientas: string[]
  citas: CitaAsistente[]
  recordatoriosCreados: number[]
  cifrasSinRespaldo: string[]
  aviso: string | null
}
