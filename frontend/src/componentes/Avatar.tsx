/** Las iniciales de un nombre: «Jorge Ramírez Pardo» → «JR», «Paola y Juan» → «PJ». */
export function iniciales(nombre: string): string {
  const palabras = nombre.split(/\s+/).filter((p) => p.length > 1 || /\p{L}/u.test(p))
  const significativas = palabras.filter((p) => !['y', 'e', 'de', 'del', 'la'].includes(p.toLowerCase()))
  return (significativas.length > 0 ? significativas : palabras).slice(0, 2).map((p) => p[0].toUpperCase()).join('')
}

/** Un círculo con las iniciales: azul de Prusia lleno para el cliente, suave para el propietario. */
export function Avatar({ nombre, tono = 'prusia', tamano = 'normal' }: { nombre: string; tono?: 'prusia' | 'suave'; tamano?: 'normal' | 'grande' }) {
  const color = tono === 'prusia' ? 'bg-prusia text-white' : 'bg-cobalto-suave text-cobalto-hondo'
  const medida = tamano === 'grande' ? 'size-11 text-base' : 'size-8 text-xs'
  return (
    <span className={`flex shrink-0 items-center justify-center rounded-full font-semibold ${color} ${medida}`} aria-hidden="true">
      {iniciales(nombre)}
    </span>
  )
}
