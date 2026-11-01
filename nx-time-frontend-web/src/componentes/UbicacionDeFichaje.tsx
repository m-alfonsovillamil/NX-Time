/**
 * Dónde se fichó (ADR 056), para los historiales: «± 25 m · Ver en el mapa», o
 * «Sin ubicación» si la empresa la pedía y no se pudo tomar.
 *
 * Un enlace a OpenStreetMap y no un mapa dentro de la página: así las
 * coordenadas solo salen de aquí si alguien pulsa, y la CSP no cambia. Sin
 * nada que decir, no pinta nada: la empresa no la pide, el fichaje es de antes,
 * o ya se borraron las coordenadas (a los 12 meses).
 */

import { fichar as F } from '../i18n/es/fichar';
import type { components } from '../api/schema';
import { Insignia } from './Basicos';

export function UbicacionDeFichaje({
  ubicacion,
  momento,
}: {
  ubicacion: components['schemas']['UbicacionDelFichaje'] | undefined;
  /** Para distinguir los enlaces de una misma tabla: «entrada de las 08:00». */
  momento: string;
}) {
  if (ubicacion === undefined) return null;
  if (ubicacion.estado === 'DENEGADA' || ubicacion.estado === 'NO_DISPONIBLE') {
    return (
      <span className="nx-ubicacion">
        <Insignia>{F.ubicacion.sin}</Insignia>
      </span>
    );
  }
  const { latitud, longitud, precisionM } = ubicacion;
  if (latitud === undefined || longitud === undefined) return null;
  return (
    <span className="nx-ubicacion nx-sutil">
      {precisionM !== undefined && `${F.ubicacion.precision(precisionM)} · `}
      <a
        className="nx-enlace"
        href={`https://www.openstreetmap.org/?mlat=${latitud}&mlon=${longitud}#map=17/${latitud}/${longitud}`}
        target="_blank"
        rel="noopener noreferrer"
        aria-label={F.ubicacion.verEnElMapaDe(momento)}
      >
        {F.ubicacion.verEnElMapa}
      </a>
    </span>
  );
}
