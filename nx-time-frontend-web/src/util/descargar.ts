/**
 * Descargar un fichero que pide sesión: el Excel de horas, el PDF mensual, el CSV de analítica.
 *
 * No puede ser un `<a href>`: el navegador no pondría la cabecera
 * `Authorization`, y el access token no va en cookie (ADR 020). Así que se
 * pide con `cliente` —que sí lleva el token y sabe refrescarlo—, se recibe
 * como `Blob` y se entrega al navegador con un enlace temporal.
 *
 * El nombre lo decide el servidor en `Content-Disposition`, que el CORS de
 * producción expone desde la fase A10. Sin esa cabecera visible, todos los
 * ficheros se llamarían como el valor por defecto de aquí.
 *
 * ```ts
 * await descargar(
 *   cliente.GET('/api/v1/informes/...', { params: {...}, parseAs: 'blob' }),
 *   'informe.xlsx',
 * );
 * ```
 */

import { ErrorDeApi } from '../api/consultas';
import { T } from '../i18n/es';
import { mensajeDeError, mensajeDeRed } from './errores';

/**
 * El nombre de fichero de una cabecera `Content-Disposition`.
 *
 * Prefiere `filename*` (RFC 5987), que es la única forma de llevar acentos:
 * `informe-horas-señal.xlsx` con `filename=` a secas llega roto.
 */
export function nombreDeContentDisposition(cabecera: string | null): string | null {
  if (cabecera === null) return null;

  const extendido = /filename\*\s*=\s*([^']*)'[^']*'([^;]+)/i.exec(cabecera);
  if (extendido?.[2] !== undefined) {
    try {
      return decodeURIComponent(extendido[2].trim().replace(/^"|"$/g, ''));
    } catch {
      // Codificación rota: se intenta con el `filename` normal.
    }
  }

  const simple = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(cabecera);
  const nombre = (simple?.[2] ?? simple?.[1])?.trim();
  return nombre !== undefined && nombre !== '' ? nombre : null;
}

interface RespuestaConFichero {
  data?: Blob;
  error?: unknown;
  response: Response;
}

/** Descarga o lanza un [ErrorDeApi] con el mensaje del servidor. */
export async function descargar(peticion: Promise<RespuestaConFichero>, nombrePorDefecto: string): Promise<void> {
  let respuesta: RespuestaConFichero;
  try {
    respuesta = await peticion;
  } catch (fallo) {
    throw new ErrorDeApi(mensajeDeRed(fallo), null);
  }

  const { data, error, response } = respuesta;
  if (!response.ok) throw new ErrorDeApi(mensajeDeError(error, response.status), response.status);
  if (data === undefined) throw new ErrorDeApi(T.errores.descarga, response.status);

  const nombre = nombreDeContentDisposition(response.headers.get('Content-Disposition')) ?? nombrePorDefecto;
  const url = URL.createObjectURL(data);
  try {
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    document.body.append(enlace);
    enlace.click();
    enlace.remove();
  } finally {
    // En el siguiente ciclo y no ya: algún navegador empieza la descarga de
    // forma asíncrona y revocar al momento la cancela.
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
