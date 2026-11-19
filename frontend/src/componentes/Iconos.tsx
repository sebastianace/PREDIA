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
  chulo: 'M4.5 12.5 9.5 17.5 19.5 6.5',
  equis: 'M6 6l12 12M18 6 6 18',
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
