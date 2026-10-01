/**
 * La sesión: el access token y lo poco que hace falta saber de quien ha entrado.
 *
 * **El access vive en memoria, y el refresh no vive aquí** (ADR 030). Desde que
 * la web tiene dominio propio, el refresh va en una cookie `HttpOnly` que pone
 * y lee el servidor: el JavaScript de esta página no lo ve, así que un XSS no
 * se lo puede llevar. Lo que sí sobrevive a recargar es esa cookie, y al
 * arrancar `cliente.ts` la usa para pedir un access nuevo sin pasar por el
 * login (`restaurarSesion`).
 *
 * Es un módulo con estado y no un contexto de React a propósito: `cliente.ts`
 * necesita leer y escribir la sesión desde fuera de cualquier componente
 * —dentro del interceptor que reintenta un 401— y pasar por un contexto ahí
 * obligaría a inyectar React en la capa de red.
 *
 * Los componentes se enteran de los cambios por [suscribirse], que es lo que
 * `useSesion` conecta a `useSyncExternalStore`.
 */

import { fijarZona } from '../util/fechas';

/** Lo que el servidor dice de quien acaba de entrar. */
export interface Sesion {
  accessToken: string;
  nombre: string;
  /**
   * Lo que esta persona puede hacer, **resuelto por el servidor**.
   *
   * No se deduce del rol: el reparto vive en `RoleAuthorities.java` y viaja
   * ya hecho en el login y en cada refresco (ver ADR 005). Copiarlo aquí sería
   * el tercer espejo del mismo reparto, y el que se quedara atrás enseñaría
   * pantallas que luego dan 403.
   */
  authorities: readonly string[];
  /**
   * La zona de su empresa (ADR 032), en la que `util/fechas.ts` cuenta los
   * días y enseña las horas. Opcional: un servidor de antes no la manda, y
   * entonces se queda la de por defecto.
   */
  zonaHoraria?: string | undefined;
}

let sesion: Sesion | null = null;
const oyentes = new Set<() => void>();

function avisar(): void {
  for (const oyente of oyentes) oyente();
}

export function sesionActual(): Sesion | null {
  return sesion;
}

export function haySesion(): boolean {
  return sesion !== null;
}

/**
 * Tras entrar o tras renovar: el refresco trae también el nombre y las
 * authorities, así que un cambio de rol llega sin volver a entrar.
 */
export function abrirSesion(nueva: Sesion): void {
  sesion = nueva;
  // Antes de avisar: quien se entera del cambio ya tiene que pintar en la zona nueva.
  fijarZona(nueva.zonaHoraria);
  avisar();
}

export function cerrarSesion(): void {
  sesion = null;
  fijarZona(null);
  avisar();
}

export function suscribirse(oyente: () => void): () => void {
  oyentes.add(oyente);
  return () => {
    oyentes.delete(oyente);
  };
}

/** Si quien ha entrado tiene una authority concreta. Ver [Sesion.authorities]. */
export function puede(authority: string): boolean {
  return sesion?.authorities.includes(authority) ?? false;
}
