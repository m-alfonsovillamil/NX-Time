/**
 * Lo que el service worker (`public/sw.js`) le dice a la página abierta:
 *
 * - `push`: ha llegado una notificación. La campana se actualiza ya, sin
 *   esperar a su consulta de cada minuto.
 * - `abrir`: se ha pulsado una notificación y esta es la pestaña que la
 *   atiende. Se navega dentro de la aplicación, sin recargar.
 *
 * No pinta nada. Va dentro del router y del cliente de consultas, que es lo
 * que necesita.
 */

import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useNavigate } from 'react-router';

import { CLAVES_AVISOS } from '../navegacion/Campana';

type Mensaje = { nxTime: 'push' } | { nxTime: 'abrir'; url: string };

function esMensaje(datos: unknown): datos is Mensaje {
  return typeof datos === 'object' && datos !== null && 'nxTime' in datos;
}

export function PuenteDelServiceWorker() {
  const consultas = useQueryClient();
  const navegar = useNavigate();

  useEffect(() => {
    const sw = globalThis.navigator?.serviceWorker;
    if (sw === undefined) return;
    const alRecibir = (evento: MessageEvent) => {
      const datos: unknown = evento.data;
      if (!esMensaje(datos)) return;
      if (datos.nxTime === 'push') {
        void consultas.invalidateQueries({ queryKey: CLAVES_AVISOS.todo });
      } else {
        // Solo rutas de esta misma web: el service worker ya lo garantiza,
        // y aquí se vuelve a mirar porque navegar es lo que importa.
        const url = new URL(datos.url, globalThis.location.origin);
        if (url.origin === globalThis.location.origin) void navegar(url.pathname + url.search);
      }
    };
    sw.addEventListener('message', alRecibir);
    return () => sw.removeEventListener('message', alRecibir);
  }, [consultas, navegar]);

  return null;
}
