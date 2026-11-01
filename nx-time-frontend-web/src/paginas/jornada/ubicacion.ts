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

/** Si en este navegador ya se ha enseñado para qué se toma. Sin almacenamiento, se vuelve a enseñar. */
export function ubicacionInformada(): boolean {
  try {
    return localStorage.getItem(CLAVE_INFORMADA) !== null;
  } catch {
    return false;
  }
}

export function recordarUbicacionInformada() {
  try {
    localStorage.setItem(CLAVE_INFORMADA, '1');
  } catch {
    // Ventana privada: la próxima vez se vuelve a enseñar, nada más.
  }
}
