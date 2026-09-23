/**
 * Saca el mensaje que se le enseña a una persona de una respuesta de error.
 *
 * Espeja `ApiErrorParser.kt`, y por el mismo motivo por el que aquella clase
 * existe: el backend devuelve `ProblemDetail` (RFC 7807) con un campo `detail`
 * **escrito para que lo lea alguien**, y en la app ese trabajo no llegaba a la
 * pantalla —tres ViewModel volcaban el JSON entero en un Toast—. Al fichar dos
 * veces se leía «Error al registrar fichaje: 409 Conflict» en vez de «Ya hay
 * una jornada activa.».
 *
 * ```json
 * {"type":"about:blank","title":"Conflict","status":409,
 *  "detail":"Ya hay una jornada activa.","instance":"/api/v1/fichaje"}
 * ```
 */

import { T } from '../i18n/es';

/** Forma mínima de un ProblemDetail, sin exigir los campos que no se usan. */
interface ProblemDetail {
  detail?: unknown;
}

/**
 * El `detail` del cuerpo, o `null` si no hay uno aprovechable.
 *
 * Acepta `unknown` y comprueba antes de leer porque **el cuerpo de un error no
 * siempre lo escribe la aplicación**: un proxy o un balanceador pueden
 * responder HTML, y `openapi-fetch` entrega entonces algo que no tiene esta
 * forma. Quedarse sin mensaje no puede convertirse en un error de JavaScript
 * justo cuando algo ya ha ido mal.
 */
export function detalleDelProblema(cuerpo: unknown): string | null {
  if (typeof cuerpo !== 'object' || cuerpo === null) return null;
  const detalle = (cuerpo as ProblemDetail).detail;
  if (typeof detalle !== 'string') return null;
  const limpio = detalle.trim();
  return limpio.length > 0 ? limpio : null;
}

/**
 * Solo para cuando el backend no dice nada aprovechable. Explica qué puede
 * hacer quien lo lee, que es más útil que enseñarle el número.
 */
export function mensajeGenerico(codigo: number): string {
  if (codigo >= 500) return T.errores.servidor;
  switch (codigo) {
    case 400:
      return T.errores.datosInvalidos;
    case 401:
      return T.errores.sesionCaducada;
    case 403:
      return T.errores.sinPermisos;
    case 404:
      return T.errores.noEncontrado;
    case 409:
      return T.errores.conflicto;
    case 429:
      return T.errores.demasiadosIntentos;
    default:
      return T.errores.inesperado;
  }
}

/** El mensaje de una respuesta sin éxito: el del servidor si lo hay, si no el genérico. */
export function mensajeDeError(cuerpo: unknown, codigo: number): string {
  return detalleDelProblema(cuerpo) ?? mensajeGenerico(codigo);
}

/**
 * El mensaje de un fallo de red, que no trae código.
 *
 * Un `AbortError` aquí no es "lo has cancelado tú": es el timeout largo del
 * arranque en frío agotado, así que se cuenta como servidor que no responde.
 */
export function mensajeDeRed(error: unknown, conectado: boolean = navegadorConectado()): string {
  if (error instanceof DOMException && (error.name === 'TimeoutError' || error.name === 'AbortError')) {
    return T.errores.servidor;
  }
  // Un `fetch` que falla sin respuesta es lo mismo para el navegador tanto si
  // no hay red como si el servidor rechazó la petición por CORS: los dos
  // llegan como un `TypeError` sin más detalle, y a propósito -- el navegador
  // no deja a la página distinguirlos.
  //
  // Decir "comprueba tu conexión" en los dos casos manda a mirar el wifi a
  // quien lo tiene bien, justo cuando el fallo es un CORS_ALLOWED_ORIGINS sin
  // poner en el backend. Lo único que sí se sabe es si el navegador cree
  // tener red, así que se usa eso: sin red, se dice; con red, el problema
  // está en el otro lado.
  return conectado ? T.errores.sinServidor : T.errores.red;
}

function navegadorConectado(): boolean {
  return typeof navigator === 'undefined' || navigator.onLine;
}
