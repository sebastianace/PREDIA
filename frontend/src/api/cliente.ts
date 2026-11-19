// Cliente HTTP de Predia: agrega el JWT y traduce los errores (ProblemDetail) a mensajes en español.

const CLAVE_TOKEN = 'predia.token'

export class ErrorApi extends Error {
  constructor(public estado: number, mensaje: string) {
    super(mensaje)
  }
}

export const token = {
  leer: () => localStorage.getItem(CLAVE_TOKEN),
  guardar: (valor: string) => localStorage.setItem(CLAVE_TOKEN, valor),
  borrar: () => localStorage.removeItem(CLAVE_TOKEN),
}

export async function api<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const cabeceras = new Headers(opciones.headers)
  const t = token.leer()
  if (t) cabeceras.set('Authorization', `Bearer ${t}`)
  if (opciones.body && !(opciones.body instanceof FormData)) cabeceras.set('Content-Type', 'application/json')

  const respuesta = await fetch(`/api${ruta}`, { ...opciones, headers: cabeceras })
  if (respuesta.status === 401 && t) {
    token.borrar()
    window.dispatchEvent(new Event('predia:sesion-expirada'))
  }
  if (!respuesta.ok) {
    let mensaje = 'No se pudo completar la operación.'
    try {
      const problema = await respuesta.json()
      mensaje = problema.detail ?? mensaje
    } catch {
      /* respuesta sin cuerpo JSON */
    }
    throw new ErrorApi(respuesta.status, mensaje)
  }
  if (respuesta.status === 204) return undefined as T
  return respuesta.json() as Promise<T>
}
