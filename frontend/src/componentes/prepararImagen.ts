const LADO_MAXIMO = 1600
const MAX_ARCHIVO = 30 * 1024 * 1024

/**
 * Lee la imagen que eligió el asesor y la deja como un JPEG de máximo 1600 px. Se hace aquí, en el navegador, para que
 * sirva cualquier formato que él sepa abrir (JPG, PNG, WebP, AVIF, HEIC en Safari…), se respete la orientación de las
 * fotos de celular y se suba un archivo liviano en vez de los 8 MB de una foto sin reducir.
 */
export async function prepararImagen(archivo: File, ladoMaximo = LADO_MAXIMO, calidad = 0.86): Promise<Blob> {
  if (!archivo.type.startsWith('image/') && archivo.type !== '') throw new Error('Ese archivo no es una imagen.')
  if (archivo.size > MAX_ARCHIVO) throw new Error('La imagen pesa más de 30 MB. Elige una más liviana.')
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(archivo, { imageOrientation: 'from-image' })
  } catch {
    throw new Error('No se pudo leer esa imagen. Prueba con una foto JPG o PNG.')
  }
  const escala = Math.min(1, ladoMaximo / Math.max(bitmap.width, bitmap.height))
  const lienzo = document.createElement('canvas')
  lienzo.width = Math.max(1, Math.round(bitmap.width * escala))
  lienzo.height = Math.max(1, Math.round(bitmap.height * escala))
  const contexto = lienzo.getContext('2d')
  if (!contexto) throw new Error('Tu navegador no pudo procesar la imagen.')
  contexto.fillStyle = '#ffffff'           // lo transparente (PNG) queda sobre blanco
  contexto.fillRect(0, 0, lienzo.width, lienzo.height)
  contexto.drawImage(bitmap, 0, 0, lienzo.width, lienzo.height)
  bitmap.close()
  const blob = await new Promise<Blob | null>((ok) => lienzo.toBlob(ok, 'image/jpeg', calidad))
  if (!blob) throw new Error('No se pudo preparar la imagen. Intenta con otra.')
  return blob
}
