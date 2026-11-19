import type { EstadoInmueble as Estado } from '../api/tipos'

export const estilosEstado: Record<Estado, { nombre: string; punto: string; texto: string; barra: string }> = {
  DISPONIBLE: { nombre: 'Disponible', punto: 'bg-cobalto', texto: 'text-cobalto', barra: 'bg-cobalto' },
  SEPARADO: { nombre: 'Separado', punto: 'bg-ambar', texto: 'text-ambar', barra: 'bg-ambar' },
  ARRENDADO: { nombre: 'Arrendado', punto: 'bg-prusia-claro', texto: 'text-prusia-claro', barra: 'bg-prusia-claro' },
  VENDIDO: { nombre: 'Vendido', punto: 'bg-niebla', texto: 'text-niebla', barra: 'bg-linea' },
}

export function EstadoInmueble({ estado }: { estado: Estado }) {
  const e = estilosEstado[estado]
  return (
    <span className={`inline-flex items-center gap-1.5 text-sm font-medium ${e.texto}`}>
      <span className={`size-2 rounded-full ${e.punto}`} aria-hidden="true" />
      {e.nombre}
    </span>
  )
}
