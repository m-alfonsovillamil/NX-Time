/**
 * Tomar la ubicación al entrar y al salir (ADR 056), si la empresa la pide.
 *
 * **Nunca falla ni bloquea**: sin permiso, sin GPS o sin respuesta se ficha
 * igual, y el estado dice por qué no la hay. El tope es propio porque el
 * `timeout` de la geolocalización no cuenta el rato que el navegador se queda
 * esperando a que se conteste a su pregunta de permiso.
 */

import type { components } from '../../api/schema';

export type UbicacionDelFichaje = components['schemas']['UbicacionDelFichaje'];

const TOPE_MS = 8000;

/** Una posición de hace unos segundos vale: no se ha movido nadie en lo que tarda en pulsar. */
const ANTIGUEDAD_ADMITIDA_MS = 30_000;

export function tomarUbicacion(): Promise<UbicacionDelFichaje> {
  return new Promise((resolver) => {
    const noDisponible = () => resolver({ estado: 'NO_DISPONIBLE' });
    const tope = setTimeout(noDisponible, TOPE_MS);
    try {
      navigator.geolocation.getCurrentPosition(
        (posicion) => {
          clearTimeout(tope);
          resolver({
            estado: 'CAPTURADA',
            latitud: posicion.coords.latitude,
            longitud: posicion.coords.longitude,
            precisionM: Math.round(posicion.coords.accuracy),
          });
        },
        (error) => {
          clearTimeout(tope);
          resolver({ estado: error.code === error.PERMISSION_DENIED ? 'DENEGADA' : 'NO_DISPONIBLE' });
        },
        { enableHighAccuracy: true, timeout: TOPE_MS, maximumAge: ANTIGUEDAD_ADMITIDA_MS },
      );
    } catch {
      // Un navegador sin geolocalización (o que la rompe): se ficha igual.
      clearTimeout(tope);
      noDisponible();
    }
  });
}

const CLAVE_INFORMADA = 'nx-ubicacion-informada';

/** Las últimas personas a las que se ha enseñado en este navegador; con más, se olvida a la primera. */
const PERSONAS_RECORDADAS = 50;

/**
 * Se recuerda **por persona**, no por navegador: en un ordenador compartido,
 * antes solo lo veía la primera que fichaba. Quién es sale del `sub` del token
 * (su correo), y no se guarda el correo sino un resumen: en `localStorage` lo
 * podría leer cualquiera que use ese ordenador. Un resumen de 32 bits no es
 * secreto (con una lista de correos se podría cotejar), pero no deja el correo
 * a la vista, y para esto no hace falta más.
 */
function personaDe(token: string | undefined): string {
  return resumen(subDelToken(token) ?? '');
}

function subDelToken(token: string | undefined): string | null {
  try {
    const carga = token?.split('.')[1];
    if (carga === undefined) return null;
    const base64 = carga.replace(/-/g, '+').replace(/_/g, '/');
    const datos: unknown = JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')));
    const sub = (datos as { sub?: unknown } | null)?.sub;
    return typeof sub === 'string' ? sub : null;
  } catch {
    return null;
  }
}

/** FNV-1a de 32 bits: basta para distinguir a quien usa un mismo navegador. */
function resumen(texto: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < texto.length; i++) {
    h ^= texto.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(16);
}

function leerInformadas(): string[] {
  try {
    const valor: unknown = JSON.parse(localStorage.getItem(CLAVE_INFORMADA) ?? '[]');
    return Array.isArray(valor) ? valor.filter((v): v is string => typeof v === 'string') : [];
  } catch {
    return [];
  }
}

/** Si a esta persona ya se le ha enseñado para qué se toma. Sin almacenamiento, se vuelve a enseñar. */
export function ubicacionInformada(token: string | undefined): boolean {
  return leerInformadas().includes(personaDe(token));
}

export function recordarUbicacionInformada(token: string | undefined) {
  const persona = personaDe(token);
  const informadas = leerInformadas().filter((p) => p !== persona);
  try {
    localStorage.setItem(CLAVE_INFORMADA, JSON.stringify([...informadas, persona].slice(-PERSONAS_RECORDADAS)));
  } catch {
    // Ventana privada: la próxima vez se vuelve a enseñar, nada más.
  }
}
