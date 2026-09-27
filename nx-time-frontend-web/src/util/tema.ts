/**
 * El tema de la interfaz: claro, oscuro o el del sistema, como en la app.
 *
 * `tokens.css` ya trae los dos juegos de colores: el oscuro se aplica por
 * `prefers-color-scheme` salvo que la raíz lleve `data-tema="claro"`, y con
 * `data-tema="oscuro"` se fuerza. Aquí solo se pone o se quita ese atributo.
 *
 * Se guarda en `localStorage` porque es una preferencia de este navegador,
 * no de la cuenta: en el móvil del trabajo se puede querer otra cosa. Y todo
 * acceso va en try/catch, porque en una ventana privada `localStorage` puede
 * lanzar, y un tema que no se guarda no puede tumbar la página.
 */

export type Tema = 'sistema' | 'claro' | 'oscuro';

const CLAVE = 'nx-tema';

export function temaGuardado(): Tema {
  try {
    const valor = globalThis.localStorage?.getItem(CLAVE);
    return valor === 'claro' || valor === 'oscuro' ? valor : 'sistema';
  } catch {
    return 'sistema';
  }
}

export function aplicarTema(tema: Tema, { guardar = true } = {}): void {
  const raiz = document.documentElement;
  if (tema === 'sistema') delete raiz.dataset['tema'];
  else raiz.dataset['tema'] = tema;
  if (!guardar) return;
  try {
    if (tema === 'sistema') globalThis.localStorage?.removeItem(CLAVE);
    else globalThis.localStorage?.setItem(CLAVE, tema);
  } catch {
    // Sin almacenamiento, el tema vale para esta visita y ya está.
  }
}
