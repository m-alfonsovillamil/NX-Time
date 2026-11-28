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
 *
 * En el móvil (plan P13) va plegado debajo del formulario («Qué es…»): quien
 * llega desde un enlace o desde la ficha de Play también tiene que poder saber
 * qué es esto, sin que el formulario deje de ser lo primero. Las dos versiones
 * están en la página, y el CSS enseña una u otra (con `display: none`, así que
 * el lector de pantalla tampoco lee la que no se ve).
 */

import type { ReactNode } from 'react';

import { Puntos } from '../../componentes/Basicos';
import { Icono } from '../../componentes/Icono';
import { PieLegal } from '../../componentes/PieLegal';
import { T } from '../../i18n/es';
import { URL_APP } from '../../legal/titular';

/**
 * El enlace a la app, si hay dónde descargarla (`application.app.download-url`).
 * En un iPhone no: la app es solo para Android, y allí se usa la web instalada.
 */
function EnlaceALaApp() {
  if (!URL_APP || /iPhone|iPad|iPod/.test(navigator.userAgent)) return null;
  return (
    <a className="nx-acceso-app" href={URL_APP} rel="noopener">
      <Icono nombre="movil" tamano={20} />
      <span>
        <strong>{T.marca.app}</strong>
        <span className="nx-sutil">{T.marca.appDetalle}</span>
      </span>
    </a>
  );
}

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
        <EnlaceALaApp />
      </aside>
      <div className="nx-acceso-lado">
        <div className="nx-centrado">{children}</div>
        <details className="nx-acceso-marca-movil">
          <summary>{T.marca.etiqueta}</summary>
          <p className="nx-acceso-marca-movil__lema">{T.marca.lema}</p>
          <Puntos puntos={T.marca.puntos} />
          <EnlaceALaApp />
        </details>
        <PieLegal />
      </div>
    </main>
  );
}
