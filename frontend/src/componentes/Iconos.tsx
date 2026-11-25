// Íconos de trazo fino, del mismo grosor que las líneas del plano.
const trazos = {
  inicio: 'M3.5 10.2 12 3.5l8.5 6.7V20a.5.5 0 0 1-.5.5h-5.5v-6h-5v6H4a.5.5 0 0 1-.5-.5z',
  inventario: 'M3.5 20.5h17M5 20.5V9.5l7-5 7 5v11M9.5 20.5v-5h5v5M9 11.5h1.5M13.5 11.5H15',
  salir: 'M14.5 4.5h4a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-4M10 16.5 5.5 12 10 7.5M5.5 12h10',
  cama: 'M3 18.5v-11M3 14h18v4.5M21 14v-2.5a2 2 0 0 0-2-2h-8V14M6.5 12a1.5 1.5 0 1 0 0-.01',
  bano: 'M4 11.5h16v2a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM6 11.5V5.8A2.3 2.3 0 0 1 10.3 5M7.5 18.5 6.5 20.5M16.5 18.5l1 2',
  area: 'M4 4h16v16H4zM4 9h4M16 20v-4M9 4v3',
  carro: 'M5 16.5v2M19 16.5v2M3.5 16.5h17V12l-2-5h-13l-2 5zM3.5 12h17M7 14.2h.01M17 14.2h.01',
  alerta: 'M12 4 2.8 19.5h18.4zM12 10v4.5M12 17h.01',
  expediente: 'M7 3.5h8l4 4V20a.5.5 0 0 1-.5.5H7a.5.5 0 0 1-.5-.5V4a.5.5 0 0 1 .5-.5ZM14.5 3.5V8h4.3M9 12.5h6M9 15.5h6M9 9.5h2.5',
  subir: 'M12 15.5V4.5M8 8.5 12 4.5l4 4M4.5 15.5V19a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-3.5',
  enviar: 'M3.5 12 20 4.5 13 20l-2.3-6.7L3.5 12Z',
  reloj: 'M12 7.5V12l3 2M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0Z',
  clientes: 'M16.5 19.5v-1a4 4 0 0 0-4-4h-5a4 4 0 0 0-4 4v1M10 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM20.5 19.5v-1a4 4 0 0 0-3-3.9M14.5 4.7a3.5 3.5 0 0 1 0 6.6',
  tasas: 'M5 19 19 5M7.5 9a1.5 1.5 0 1 0 0-.01M16.5 18a1.5 1.5 0 1 0 0-.01',
  negocios: 'M4.5 7.5h15v11a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1ZM9 7.5v-2a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M4.5 12.5h15M10.5 12.5v2h3v-2',
  recordatorios: 'M4.5 6.5h15v11h-15zM4.5 7l7.5 6 7.5-6',
  requisitos: 'M5 12.5 9 16.5 19 6.5M5 5.5h6M5 19.5h14',
  asistente: 'M4.5 5.5h15a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H13l-4 3.5v-3.5H4.5a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1ZM8.5 11h.01M12 11h.01M15.5 11h.01',
  reduccion: 'M4 6.5 10 12.5l3.5-3.5 6.5 6.5M14.5 15.5H20V10',
  mas: 'M5 7h14M5 12h14M5 17h14',
  chulo: 'M4.5 12.5 9.5 17.5 19.5 6.5',
  equis: 'M6 6l12 12M18 6 6 18',
  izquierda: 'M14.5 5.5 8 12l6.5 6.5',
  derecha: 'M9.5 5.5 16 12l-6.5 6.5',
  foto: 'M4.5 5.5h15v13h-15zM4.5 15.5l4.5-4.5 4 4 2.5-2.5 4 4M15.5 9.5h.01',
  regla: 'M3.5 16.5 16.5 3.5l4 4-13 13zM7.5 12.5l2 2M10.5 9.5l2 2M13.5 6.5l2 2',
  mas2: 'M12 5v14M5 12h14',
  buscar: 'M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13ZM15.5 15.5 20 20',
  escudo: 'M12 3.5 5 6v5.5c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6zM9 12l2.2 2.2L15.5 10',
  telefono: 'M6.5 4h3l1.5 4-2 1.3a10 10 0 0 0 4.7 4.7L15 12l4 1.5v3a2 2 0 0 1-2 2A13.5 13.5 0 0 1 4.5 6a2 2 0 0 1 2-2Z',
  chispa: 'M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8zM19 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z',
  usuarioMas: 'M15.5 19.5v-1a4 4 0 0 0-4-4h-4a4 4 0 0 0-4 4v1M9.5 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM19 8v6M16 11h6',
  circulo: 'M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0Z',
  circuloCheck: 'M20.5 12a8.5 8.5 0 1 1-17 0 8.5 8.5 0 0 1 17 0ZM8.5 12.3l2.4 2.4 4.6-4.9',
} as const

export type NombreIcono = keyof typeof trazos

export function Icono({ nombre, className = 'size-5' }: { nombre: NombreIcono; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="none" stroke="currentColor" strokeWidth={1.5}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={trazos[nombre]} />
    </svg>
  )
}
