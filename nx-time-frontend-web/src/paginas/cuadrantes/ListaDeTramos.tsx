/**
 * Los tramos de un día, editables: desde, hasta y si acaba al día siguiente
 * (el turno de noche). Un día sin tramos es libre. Sirve para cada día de una
 * plantilla y para el día de una excepción.
 */

import { Boton, Campo } from '../../componentes/Basicos';
import { cuadrantes } from '../../i18n/es/cuadrantes';
import type { TramoEditable } from './reglas';

const P = cuadrantes.plantilla;

export function ListaDeTramos({
  id,
  dia,
  tramos,
  alCambiar,
  bloqueada = false,
}: {
  id: string;
  /** Para los nombres accesibles: «Quitar el tramo 2 del lunes». */
  dia: string;
  tramos: readonly TramoEditable[];
  alCambiar: (tramos: TramoEditable[]) => void;
  bloqueada?: boolean;
}) {
  function cambiar(i: number, cambio: Partial<TramoEditable>) {
    alCambiar(tramos.map((t, j) => (j === i ? { ...t, ...cambio } : t)));
  }

  return (
    <div className="nx-tramos-editables">
      {tramos.length === 0 && <span className="nx-sutil">{P.libre}</span>}
      {tramos.map((t, i) => (
        <div key={i} className="nx-tramo-editable">
          <Campo id={`${id}-${i}-desde`} etiqueta={P.desde} type="time" disabled={bloqueada} value={t.inicio} onChange={(e) => cambiar(i, { inicio: e.target.value })} />
          <Campo id={`${id}-${i}-hasta`} etiqueta={P.hasta} type="time" disabled={bloqueada} value={t.fin} onChange={(e) => cambiar(i, { fin: e.target.value })} />
          <label className="nx-casilla">
            <input type="checkbox" disabled={bloqueada} checked={t.alDiaSiguiente} onChange={(e) => cambiar(i, { alDiaSiguiente: e.target.checked })} />
            {P.alDiaSiguiente}
          </label>
          {!bloqueada && (
            <Boton variante="texto" aria-label={P.quitarTramoDe(dia, i + 1)} onClick={() => alCambiar(tramos.filter((_, j) => j !== i))}>
              {P.quitarTramo}
            </Boton>
          )}
        </div>
      ))}
      {!bloqueada && (
        <div>
          <Boton
            variante="texto"
            aria-label={`${P.anadirTramo} (${dia})`}
            onClick={() => alCambiar([...tramos, { inicio: '09:00', fin: '14:00', alDiaSiguiente: false }])}
          >
            + {P.anadirTramo}
          </Boton>
        </div>
      )}
    </div>
  );
}
