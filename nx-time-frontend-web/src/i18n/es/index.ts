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
 * en cada merge.
 *
 * **`T` solo lleva lo común** (errores, estados de carga, acceso y el marco).
 * Los textos de cada página los importa esa página de su fichero
 * (`i18n/es/historial`), y así viajan en su trozo de JS: metidos en `T`, que lo
 * carga el marco, acabarían en el JS inicial de todo el mundo, y con treinta
 * páginas serían unos 25 kB comprimidos que nadie ha pedido. Lo destapó el
 * presupuesto de peso al llegar la segunda página (W2).
 *
 * `as const` en cada área para que cada clave sea un tipo: escribir `T.lgin`
 * no compila.
 */

import { acceso } from './acceso';
import { comun } from './comun';
import { navegacion } from './navegacion';

export const T = {
  ...comun,
  ...acceso,
  navegacion,
} as const;
