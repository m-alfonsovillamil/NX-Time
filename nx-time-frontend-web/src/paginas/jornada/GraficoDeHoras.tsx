/**
 * Horas trabajadas por día contra la jornada esperada, en SVG propio (ADR 029, decisión 3).
 *
 * Una barra por día con lo trabajado y una raya discontinua con lo esperado,
 * que el servidor ya calcula con la jornada contratada: 0 en fin de semana,
 * festivo o ausencia aprobada. Los días de festivo o ausencia van en otro
 * color, como en la app, para que una semana corta no parezca una semana floja.
 *
 * **Un gráfico no se puede leer con un lector de pantalla**, así que lleva su
 * resumen en `aria-label` y, al lado, «Ver como tabla» con los mismos números.
 * La tabla no es un extra de accesibilidad: también es lo que quiere quien
 * necesita la cifra exacta de un día.
 */

import { useState } from 'react';

import { Boton } from '../../componentes/Basicos';
import { Tabla } from '../../componentes/Tabla';
import { fichar } from '../../i18n/es/fichar';
import { fechaCorta, minutos } from '../../util/fechas';
import type { HorasDelDia } from './consultas';

const H = fichar.horas;

/*
 * El dibujo mide siempre lo mismo por dentro, sean 7 días o 31, y es el hueco
 * de cada día lo que cambia. Si el ancho creciera con los días, el SVG se
 * encogería para caber y con él el texto del eje: con un mes, a 6 px.
 */
const ANCHO = 600;
const ALTO = 200;
const MARGEN_ABAJO = 22;
const BARRA_MAXIMA = 36;

function motivoDelDia(d: HorasDelDia): string | null {
  if (d.festivo !== undefined && d.festivo !== '') return H.festivo(d.festivo);
  if (d.ausencia !== undefined && d.ausencia !== '') return d.ausencia;
  return null;
}

export function GraficoDeHoras({
  dias,
  etiquetaDe,
  titulo,
}: {
  dias: readonly HorasDelDia[];
  /** Lo que va debajo de cada barra: la inicial del día en una semana, el número en un mes. */
  etiquetaDe: (dia: string, indice: number) => string;
  /** El resumen que se lee en lugar del dibujo. */
  titulo: string;
}) {
  const [comoTabla, setComoTabla] = useState(false);

  // La escala sale del mayor de los dos, y como poco una hora: un mes vacío
  // no puede dibujar barras de un minuto a toda altura.
  const maximo = Math.max(60, ...dias.map((d) => Math.max(d.minutosTrabajados ?? 0, d.minutosEsperados ?? 0)));
  const alto = (min: number) => ((ALTO - MARGEN_ABAJO - 8) * min) / maximo;
  const hueco = ANCHO / Math.max(1, dias.length);
  const barra = Math.min(BARRA_MAXIMA, hueco * 0.6);

  return (
    <div className="nx-grafico">
      {comoTabla ? (
        <Tabla
          titulo={H.tablaTitulo}
          filas={dias}
          claveDeFila={(d) => d.fecha ?? ''}
          columnas={[
            { clave: 'dia', cabecera: H.dia, celda: (d) => fechaCorta(d.fecha) },
            { clave: 'trabajado', cabecera: H.trabajado, celda: (d) => minutos(d.minutosTrabajados ?? 0), numerica: true },
            { clave: 'esperado', cabecera: H.esperado, celda: (d) => minutos(d.minutosEsperados ?? 0), numerica: true },
            { clave: 'motivo', cabecera: H.motivo, celda: (d) => motivoDelDia(d) ?? '' },
          ]}
        />
      ) : (
        <svg
          className="nx-grafico__dibujo"
          viewBox={`0 0 ${ANCHO} ${ALTO}`}
          role="img"
          aria-label={titulo}
        >
          {dias.map((d, i) => {
            const x = i * hueco + (hueco - barra) / 2;
            const trabajado = alto(d.minutosTrabajados ?? 0);
            const esperado = alto(d.minutosEsperados ?? 0);
            const base = ALTO - MARGEN_ABAJO;
            const especial = motivoDelDia(d) !== null;
            return (
              <g key={d.fecha}>
                <title>{`${fechaCorta(d.fecha)}: ${minutos(d.minutosTrabajados ?? 0)}${
                  especial ? ` · ${motivoDelDia(d) ?? ''}` : ''
                }`}</title>
                {trabajado > 0 && (
                  <rect
                    className={especial ? 'nx-grafico__barra nx-grafico__barra--especial' : 'nx-grafico__barra'}
                    x={x}
                    y={base - trabajado}
                    width={barra}
                    height={trabajado}
                    rx={3}
                  />
                )}
                {especial && trabajado === 0 && (
                  <rect className="nx-grafico__marca-especial" x={x} y={base - 4} width={barra} height={4} rx={2} />
                )}
                {esperado > 0 && (
                  <line
                    className="nx-grafico__esperado"
                    x1={i * hueco + 2}
                    x2={(i + 1) * hueco - 2}
                    y1={base - esperado}
                    y2={base - esperado}
                  />
                )}
                <text className="nx-grafico__eje" x={i * hueco + hueco / 2} y={ALTO - 5} textAnchor="middle">
                  {etiquetaDe(d.fecha ?? '', i)}
                </text>
              </g>
            );
          })}
          <line className="nx-grafico__base" x1={0} x2={ANCHO} y1={ALTO - MARGEN_ABAJO} y2={ALTO - MARGEN_ABAJO} />
        </svg>
      )}
      <div className="nx-grafico__pie">
        {!comoTabla && <p className="nx-sutil">{H.leyenda}</p>}
        <Boton variante="texto" onClick={() => setComoTabla((v) => !v)}>
          {comoTabla ? H.verGrafico : H.verTabla}
        </Boton>
      </div>
    </div>
  );
}
