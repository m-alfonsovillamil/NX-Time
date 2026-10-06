/**
 * El marco de las pantallas de fuera de la sesión: entrar, recuperar el
 * acceso, registrar una empresa y confirmar el correo.
 *
 * En el móvil es lo de siempre: la tarjeta del formulario centrada sobre el
 * degradado. En escritorio, la tarjeta sola en mitad de 1400 px parecía una
 * página a medio cargar; ahí va al lado un panel con la marca y, en tres
 * puntos, qué es esto. Es la primera pantalla que ve quien llega a registrar
 * su empresa, y la única en que la aplicación puede presentarse.
 *
 * El panel es contenido de verdad (se lee), pero no lleva encabezados: el
 * `<h1>` de la página sigue siendo el de su formulario.
 */

import type { ReactNode } from 'react';

import { Puntos } from '../../componentes/Basicos';
import { T } from '../../i18n/es';

export function MarcoDeAcceso({ children }: { children: ReactNode }) {
  return (
    <main className="nx-acceso-marco">
      <aside className="nx-acceso-marca" aria-label={T.marca.etiqueta}>
        <p className="nx-acceso-marca__nombre">
          <img src="/iconos/icono.svg" alt="" width={48} height={48} />
          {T.app.nombre}
        </p>
        <p className="nx-acceso-marca__lema">{T.marca.lema}</p>
        <Puntos puntos={T.marca.puntos} />
      </aside>
      <div className="nx-centrado">{children}</div>
    </main>
  );
}
