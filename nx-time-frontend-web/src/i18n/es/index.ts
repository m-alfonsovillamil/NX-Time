/**
 * Todos los textos de la interfaz, repartidos por áreas.
 *
 * Ningún componente escribe una cadena literal, por el mismo motivo por el que
 * la app Android tiene `strings.xml` y `MensajeUi`: el día que haga falta otro
 * idioma, el trabajo es traducir esta carpeta y no releer cada pantalla
 * buscando texto suelto. Y de paso, los mensajes de error se revisan juntos,
 * que es como se nota si alguno está escrito para el programador y no para
 * quien lo va a leer.
 *
 * Un fichero por área y no uno solo: con las treinta pantallas del plan de la
 * web (ADR 029) serían dos mil líneas, y dos fases tocándolo a la vez chocarían
 * en cada merge. Aquí solo se juntan, y `T` sigue siendo el único punto de
 * entrada: `import { T } from '../i18n/es'` no cambia.
 *
 * `as const` en cada área para que cada clave sea un tipo: escribir `T.lgin`
 * no compila.
 */

import { acceso } from './acceso';
import { comun } from './comun';
import { fichar } from './fichar';
import { navegacion } from './navegacion';

export const T = {
  ...comun,
  ...acceso,
  fichar,
  navegacion,
} as const;
