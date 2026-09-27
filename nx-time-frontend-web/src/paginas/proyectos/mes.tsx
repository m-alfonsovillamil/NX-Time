/**
 * Un mes elegible con ‹ y ›: las horas de un proyecto, el reparto de la
 * empresa y los festivos se miran mes a mes. Empieza en el mes en curso (de
 * España) y, fuera de él, ofrece volver.
 */

import { useState } from 'react';

import { Boton } from '../../componentes/Basicos';
import { proyectos } from '../../i18n/es/proyectos';
import { hoyEnEspana } from '../../util/fechas';

const M = proyectos.mes;

export interface Mes {
  anio: number;
  mes: number;
  mover: (delta: number) => void;
  volver: () => void;
  esElActual: boolean;
}

/** @param desplazamiento meses respecto al actual con que empieza: -1 para el anterior. */
export function useMes(desplazamiento = 0): Mes {
  const hoy = hoyEnEspana();
  const actual = { anio: Number(hoy.slice(0, 4)), mes: Number(hoy.slice(5, 7)) };
  const [elegido, setElegido] = useState(() => {
    const indice = actual.anio * 12 + (actual.mes - 1) + desplazamiento;
    return { anio: Math.floor(indice / 12), mes: (indice % 12) + 1 };
  });
  return {
    ...elegido,
    mover: (delta) =>
      setElegido(({ anio, mes }) => {
        const indice = anio * 12 + (mes - 1) + delta;
        return { anio: Math.floor(indice / 12), mes: (indice % 12) + 1 };
      }),
    volver: () => setElegido(actual),
    esElActual: elegido.anio === actual.anio && elegido.mes === actual.mes,
  };
}

export function NavegadorDeMes({ mes, titulo, id }: { mes: Mes; titulo: string; id: string }) {
  return (
    <div className="nx-calendario__barra">
      <div className="nx-calendario__navegacion">
        <Boton variante="texto" aria-label={M.anterior} onClick={() => mes.mover(-1)}>
          ‹
        </Boton>
        <h2 id={id} className="nx-calendario__mes" aria-live="polite">
          {titulo}
        </h2>
        <Boton variante="texto" aria-label={M.siguiente} onClick={() => mes.mover(1)}>
          ›
        </Boton>
      </div>
      {!mes.esElActual && (
        <Boton variante="secundario" onClick={mes.volver}>
          {M.esteMes}
        </Boton>
      )}
    </div>
  );
}
