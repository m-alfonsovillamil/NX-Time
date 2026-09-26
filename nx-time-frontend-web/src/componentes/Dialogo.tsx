/**
 * Un diálogo modal sobre `<dialog>` nativo.
 *
 * Nativo y no un `<div>` con `z-index` porque `showModal()` da gratis lo que a
 * mano siempre se olvida: el foco atrapado dentro, el resto de la página inerte
 * para el teclado y el lector de pantalla, Escape para cerrar y el foco de
 * vuelta al botón que lo abrió.
 *
 * El contenido solo se monta mientras está abierto: un formulario que se
 * cierra y se vuelve a abrir empieza limpio, que es lo que se espera de
 * «Cancelar».
 *
 * jsdom (los tests) no implementa `showModal()`; ahí se abre con el atributo
 * `open`, que basta para que el contenido exista y se pueda probar.
 */

import { useEffect, useId, useRef, type ReactNode } from 'react';

import { T } from '../i18n/es';
import { Boton } from './Basicos';

export function Dialogo({
  abierto,
  titulo,
  alCerrar,
  children,
  acciones,
}: {
  abierto: boolean;
  titulo: string;
  alCerrar: () => void;
  children: ReactNode;
  /** Los botones de abajo. Sin ellos, uno de «Cerrar». */
  acciones?: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const idTitulo = useId();

  useEffect(() => {
    const dialogo = ref.current;
    if (dialogo === null) return;
    if (abierto && !dialogo.open) {
      if (typeof dialogo.showModal === 'function') dialogo.showModal();
      else dialogo.setAttribute('open', '');
    } else if (!abierto && dialogo.open) {
      if (typeof dialogo.close === 'function') dialogo.close();
      else dialogo.removeAttribute('open');
    }
  }, [abierto]);

  return (
    <dialog
      ref={ref}
      className="nx-dialogo"
      aria-labelledby={idTitulo}
      // Escape dispara `cancel` y luego `close`. Se escucha `close` para que
      // el estado de React siga al del navegador, cierre quien cierre.
      onClose={() => {
        if (abierto) alCerrar();
      }}
      // Un clic en el fondo cae en el propio <dialog>, no en su contenido.
      onClick={(evento) => {
        if (evento.target === ref.current) alCerrar();
      }}
    >
      {abierto && (
        <div className="nx-dialogo__cuerpo">
          <h2 id={idTitulo}>{titulo}</h2>
          {/* Lo que se desplaza es el contenido: el título y los botones
              se quedan a la vista, por larga que sea la lista. */}
          <div className="nx-dialogo__contenido">{children}</div>
          <div className="nx-dialogo__acciones">
            {acciones ?? (
              <Boton variante="texto" onClick={alCerrar}>
                {T.app.cerrar}
              </Boton>
            )}
          </div>
        </div>
      )}
    </dialog>
  );
}
