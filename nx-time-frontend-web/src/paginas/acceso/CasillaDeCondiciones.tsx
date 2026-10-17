/**
 * La casilla obligatoria del registro (ADR 046): aceptar las condiciones y el
 * contrato de encargo en nombre de la empresa, y declarar la mayoría de edad.
 * Los enlaces abren los textos en otra pestaña, para no perder lo escrito.
 */

import { T } from '../../i18n/es';

const A = T.registro.acepto;

export function CasillaDeCondiciones({ marcada, alCambiar }: { marcada: boolean; alCambiar: (marcada: boolean) => void }) {
  return (
    <label className="nx-casilla" htmlFor="registro-acepto">
      <input id="registro-acepto" type="checkbox" checked={marcada} onChange={(e) => alCambiar(e.target.checked)} />
      <span>
        {A.antes}
        <a className="nx-enlace" href="/legal/condiciones" target="_blank" rel="noopener">
          {A.condiciones}
        </a>
        {A.entre}
        <a className="nx-enlace" href="/legal/encargo" target="_blank" rel="noopener">
          {A.encargo}
        </a>
        {A.despues}
      </span>
    </label>
  );
}
