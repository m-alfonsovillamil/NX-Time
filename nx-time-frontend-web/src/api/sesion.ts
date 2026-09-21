/**
 * La sesión: dos tokens y lo poco que hace falta saber de quien ha entrado.
 *
 * **Vive en memoria y solo en memoria.** No pasa por `localStorage`, ni por
 * `sessionStorage`, ni por cookie. La consecuencia visible es que recargar la
 * página cierra la sesión, y se asume: el porqué está en
 * `docs/adr/020-tokens-en-el-navegador.md`, con el detalle concreto de que
 * `onrender.com` está en la Public Suffix List y por eso la cookie `HttpOnly`
 * tiene que esperar a un dominio propio.
 *
 * Es un módulo con estado y no un contexto de React a propósito: `cliente.ts`
 * necesita leer y escribir los tokens desde fuera de cualquier componente
 * —dentro del interceptor que reintenta un 401— y pasar por un contexto ahí
 * obligaría a inyectar React en la capa de red.
 *
 * Los componentes se enteran de los cambios por [suscribirse], que es lo que
 * `useSesion` conecta a `useSyncExternalStore`.
 */

/** Lo que el servidor dice de quien acaba de entrar. */
export interface Sesion {
  accessToken: string;
  refreshToken: string;
  nombre: string;
  /**
   * Lo que esta persona puede hacer, **resuelto por el servidor**.
   *
   * No se deduce del rol: el reparto vive en `RoleAuthorities.java` y viaja
   * ya hecho en el login (ver ADR 005). Copiarlo aquí sería el tercer espejo
   * del mismo reparto, y el que se quedara atrás enseñaría pantallas que
   * luego dan 403.
   */
  authorities: readonly string[];
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

export function abrirSesion(nueva: Sesion): void {
  sesion = nueva;
  avisar();
}

/**
 * Guarda el par que devuelve `/auth/refresh`.
 *
 * **Los dos juntos.** El servidor rota el refresh desde la fase A11: el que se
 * acaba de presentar ya no vale, así que guardar solo el access dejaría a la
 * siguiente renovación llegando con un token usado, que el servidor lee como
 * una copia robada y cierra la sesión entera.
 */
export function renovarTokens(accessToken: string, refreshToken: string): void {
  if (!sesion) return;
  sesion = { ...sesion, accessToken, refreshToken };
  avisar();
}

export function cerrarSesion(): void {
  sesion = null;
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
