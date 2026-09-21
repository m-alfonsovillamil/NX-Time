/**
 * El cartel de «el servidor está despertando».
 *
 * No es una cortesía. Render, en el plan gratuito, apaga el servicio tras 15
 * minutos sin tráfico, y despertarlo ha llegado a tardar más de tres minutos
 * medidos. Sin este cartel se ve una página congelada durante ese rato, y
 * cualquiera la cierra antes de que el servidor conteste — es decir, la
 * aplicación parecería rota justo en la primera visita, que es la única que
 * mucha gente hará.
 *
 * Se pinta solo cuando una petición lleva más de tres segundos esperando y el
 * servidor podía estar dormido; el criterio lo decide `cliente.ts`.
 */

import { useSyncExternalStore } from 'react';

import { servidorDespertando, suscribirseADespertando } from '../api/cliente';
import { T } from '../i18n/es';

export function ServidorDespertando() {
  const despertando = useSyncExternalStore(
    suscribirseADespertando,
    servidorDespertando,
    // En el servidor no hay peticiones en vuelo. Hace falta aunque hoy no haya
    // SSR: sin este tercer argumento, el hook lanza si alguna vez se renderiza
    // fuera del navegador, incluidos los tests.
    () => false,
  );

  if (!despertando) return null;

  return (
    <aside className="nx-despertando" role="status">
      <strong>{T.servidor.despertando}</strong>
      <span>{T.servidor.despertandoDetalle}</span>
    </aside>
  );
}
