/**
 * Curvas de nivel generadas, no dibujadas a mano: un campo de alturas suave (suma de ondas con fases que salen de
 * una semilla) al que se le trazan las líneas de igual altura con «marching squares». La misma semilla da siempre
 * el mismo terreno, así que cada inmueble puede tener el suyo.
 */

function aleatorio(semilla: number) {
  // mulberry32: pequeño, determinista y suficiente para elegir fases.
  let a = semilla >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export interface Curva {
  /** Índice del nivel, de 0 (valle) hacia arriba. */
  nivel: number
  /** Cada quinta curva es «maestra»: se dibuja más gruesa, como en un mapa topográfico. */
  maestra: boolean
  d: string
}

type Punto = [number, number]

export function curvasDeNivel(semilla: number, ancho: number, alto: number, niveles = 14, celda = 12): Curva[] {
  const rnd = aleatorio(semilla * 7919 + 13)
  const ondas = Array.from({ length: 6 }, (_, i) => {
    const angulo = rnd() * Math.PI * 2
    const frecuencia = (0.9 + i * 0.55 + rnd() * 0.4) / Math.max(ancho, alto) * Math.PI * 2
    return { kx: Math.cos(angulo) * frecuencia, ky: Math.sin(angulo) * frecuencia, fase: rnd() * Math.PI * 2, amp: 1 / (1 + i * 0.7) }
  })
  const columnas = Math.ceil(ancho / celda) + 1
  const filas = Math.ceil(alto / celda) + 1
  const campo: number[] = []
  let min = Infinity
  let max = -Infinity
  for (let j = 0; j < filas; j++) {
    for (let i = 0; i < columnas; i++) {
      let v = 0
      for (const o of ondas) v += o.amp * Math.sin(o.kx * i * celda + o.ky * j * celda + o.fase)
      campo.push(v)
      if (v < min) min = v
      if (v > max) max = v
    }
  }

  const curvas: Curva[] = []
  for (let n = 0; n < niveles; n++) {
    const umbral = min + ((max - min) * (n + 1)) / (niveles + 1)
    const segmentos: [Punto, Punto][] = []
    const v = (i: number, j: number) => campo[j * columnas + i]
    const cruce = (x0: number, y0: number, v0: number, x1: number, y1: number, v1: number): Punto => {
      const t = (umbral - v0) / (v1 - v0)
      return [x0 + (x1 - x0) * t, y0 + (y1 - y0) * t]
    }
    for (let j = 0; j < filas - 1; j++) {
      for (let i = 0; i < columnas - 1; i++) {
        const a = v(i, j), b = v(i + 1, j), c = v(i + 1, j + 1), d = v(i, j + 1)
        const x = i * celda, y = j * celda
        const arriba = (): Punto => cruce(x, y, a, x + celda, y, b)
        const derecha = (): Punto => cruce(x + celda, y, b, x + celda, y + celda, c)
        const abajo = (): Punto => cruce(x, y + celda, d, x + celda, y + celda, c)
        const izquierda = (): Punto => cruce(x, y, a, x, y + celda, d)
        const caso = (a > umbral ? 8 : 0) | (b > umbral ? 4 : 0) | (c > umbral ? 2 : 0) | (d > umbral ? 1 : 0)
        switch (caso) {
          case 1: case 14: segmentos.push([izquierda(), abajo()]); break
          case 2: case 13: segmentos.push([abajo(), derecha()]); break
          case 3: case 12: segmentos.push([izquierda(), derecha()]); break
          case 4: case 11: segmentos.push([arriba(), derecha()]); break
          case 6: case 9: segmentos.push([arriba(), abajo()]); break
          case 7: case 8: segmentos.push([arriba(), izquierda()]); break
          case 5: segmentos.push([izquierda(), arriba()], [abajo(), derecha()]); break
          case 10: segmentos.push([arriba(), derecha()], [izquierda(), abajo()]); break
        }
      }
    }
    const d = unir(segmentos)
    if (d) curvas.push({ nivel: n, maestra: n % 5 === 2, d })
  }
  return curvas
}

/** Une los segmentos que comparten extremo en líneas largas (un trazo continuo se ve y pesa menos que miles de tramos). */
function unir(segmentos: [Punto, Punto][]): string {
  const clave = (p: Punto) => `${p[0].toFixed(2)},${p[1].toFixed(2)}`
  const porExtremo = new Map<string, number[]>()
  segmentos.forEach((s, i) => {
    for (const p of s) {
      const k = clave(p)
      porExtremo.set(k, [...(porExtremo.get(k) ?? []), i])
    }
  })
  const usado = new Array(segmentos.length).fill(false)
  const lineas: Punto[][] = []
  const siguiente = (desde: Punto): [Punto, number] | null => {
    for (const i of porExtremo.get(clave(desde)) ?? []) {
      if (usado[i]) continue
      const [p, q] = segmentos[i]
      usado[i] = true
      return [clave(p) === clave(desde) ? q : p, i]
    }
    return null
  }
  for (let i = 0; i < segmentos.length; i++) {
    if (usado[i]) continue
    usado[i] = true
    const linea: Punto[] = [segmentos[i][0], segmentos[i][1]]
    for (let paso = siguiente(linea[linea.length - 1]); paso; paso = siguiente(linea[linea.length - 1])) linea.push(paso[0])
    for (let paso = siguiente(linea[0]); paso; paso = siguiente(linea[0])) linea.unshift(paso[0])
    lineas.push(linea)
  }
  return lineas
    .map((l) => 'M' + l.map((p) => `${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join('L'))
    .join('')
}
