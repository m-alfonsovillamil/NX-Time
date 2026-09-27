/**
 * La sesión vista desde React.
 *
 * `useSyncExternalStore` y no un contexto: la sesión vive en un módulo porque
 * `cliente.ts` tiene que leerla y escribirla desde fuera de React (ver
 * `sesion.ts`). Esto es lo que conecta ese módulo con el árbol de componentes
 * sin duplicar el estado en los dos sitios.
 *
 * Con eso, cuando el refresco falla y `cliente.ts` cierra la sesión, **cada
 * pantalla se entera sola** y el router lleva al login. Sin ello pasaría lo que
 * pasaba en la app antes de que existiera `sesionCaducada`: la sesión muerta y
 * la pantalla enseñando el nombre cacheado, sin forma de volver a entrar.
 */

import { useSyncExternalStore } from 'react';

import { haySesion, sesionActual, suscribirse, type Sesion } from './sesion';

export function useSesionIniciada(): boolean {
  return useSyncExternalStore(suscribirse, haySesion, () => false);
}

/**
 * Quién ha entrado y qué puede hacer.
 *
 * `puede` pregunta por una authority **resuelta por el servidor** (ADR 005):
 * el menú y las rutas se construyen con esto, igual que `Permisos.kt` en la
 * app. No autoriza nada —eso lo hace el `@PreAuthorize` de cada endpoint—;
 * solo decide qué se enseña, para no ofrecer una pantalla que luego dé 403.
 */
export function useSesion(): { sesion: Sesion | null; puede: (authority: string) => boolean } {
  const sesion = useSyncExternalStore(suscribirse, sesionActual, () => null);
  return {
    sesion,
    puede: (authority) => sesion?.authorities.includes(authority) ?? false,
  };
}
