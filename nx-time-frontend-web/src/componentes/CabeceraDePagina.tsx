/**
 * La cabecera de cada página: de dónde cuelga, qué es y qué se puede hacer en ella.
 *
 * Antes cada página escribía su `<header>` con un `<h1>` y, a veces, un
 * párrafo suelto debajo. Salía igual de sobrio en todas, pero también igual
 * de mudo: nada decía en qué parte de la aplicación estabas, y en una pantalla
 * ancha el título quedaba perdido en una esquina.
 *
 * Ahora lleva el icono de la sección en su recuadro de color (teal en «Lo
 * mío», índigo en «Gestión», la regla de la app), la miga de pan con el grupo
 * y el apartado del menú, y los botones a la derecha. El icono y la miga
 * salen del catálogo (`secciones.ts`) por la URL, así que no hay que pasarlos.
 */

import type { ReactNode } from 'react';
import { useLocation } from 'react-router';

import { T } from '../i18n/es';
import { seccionDeRuta } from '../navegacion/secciones';
import { Icono, type NombreIcono } from './Icono';

const N = T.navegacion;

export function CabeceraDePagina({
  titulo,
  descripcion,
  acciones,
  icono,
  miga = true,
}: {
  titulo: string;
  /** Una o dos frases: qué es esto y para qué sirve. */
  descripcion?: ReactNode;
  acciones?: ReactNode;
  /** Solo si no es el de la sección de la URL (una vista de detalle, por ejemplo). */
  icono?: NombreIcono;
  /** Sin miga en las páginas que no son de ningún apartado del menú. */
  miga?: boolean;
}) {
  const seccion = seccionDeRuta(useLocation().pathname);
  const dibujo = icono ?? seccion?.icono;

  return (
    <header className="nx-cabecera-pagina">
      {dibujo !== undefined && (
        <span className="nx-cabecera-pagina__icono">
          <Icono nombre={dibujo} />
        </span>
      )}
      <div className="nx-cabecera-pagina__textos">
        {miga && seccion !== undefined && (
          <p className="nx-cabecera-pagina__miga">
            {N.grupos[seccion.grupo]} <Icono nombre="siguiente" tamano={16} /> {N.subgrupos[seccion.subgrupo]}
          </p>
        )}
        <h1>{titulo}</h1>
        {descripcion !== undefined && <p className="nx-cabecera-pagina__descripcion">{descripcion}</p>}
      </div>
      {acciones !== undefined && <div className="nx-cabecera-pagina__acciones">{acciones}</div>}
    </header>
  );
}
