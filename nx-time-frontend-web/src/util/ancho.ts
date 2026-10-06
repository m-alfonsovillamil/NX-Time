/**
 * Si la ventana es al menos así de ancha, y se entera cuando cambia.
 *
 * Casi todo lo que depende del ancho se resuelve en CSS. Esto es para lo que
 * CSS no puede decidir: **qué** se pinta, no cómo. Una lista con su detalle
 * al lado en escritorio y el mismo detalle en un diálogo en el móvil son dos
 * árboles distintos, y pintar los dos para esconder uno duplicaría los
 * formularios (y sus `id`).
 *
 * Sin `matchMedia` (los tests, un navegador muy viejo) responde que no: la
 * versión estrecha es la que funciona en cualquier sitio.
 */

import { useEffect, useState } from 'react';

export function useEsAncho(minimo: number): boolean {
  const consulta = `(min-width: ${minimo}px)`;
  const [ancho, setAncho] = useState(() => globalThis.matchMedia?.(consulta).matches === true);

  useEffect(() => {
    const medio = globalThis.matchMedia?.(consulta);
    if (medio === undefined) return;
    const alCambiar = () => setAncho(medio.matches);
    alCambiar();
    medio.addEventListener('change', alCambiar);
    return () => medio.removeEventListener('change', alCambiar);
  }, [consulta]);

  return ancho;
}
