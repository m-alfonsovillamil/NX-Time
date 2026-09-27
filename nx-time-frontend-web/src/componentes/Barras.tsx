/**
 * Barras horizontales: horas por proyecto, por persona…
 *
 * **Con `media`, la escala no la marca solo el mayor.** Con barras
 * proporcionales al máximo, quien más ha trabajado siempre llena la barra y
 * todos los demás parecen cortos: el gráfico dice quién trabaja más, que ya se
 * lee en las cifras, y calla lo que importa, quién se sale de lo normal. Así
 * que el tope es el mayor entre el máximo y la media, la media se marca con
 * una raya y quien la pasa sale en otro color (como en el panel de la app).
 */

import { minutos as formatoMinutos } from '../util/fechas';

export interface FilaDeBarra {
  clave: string | number;
  texto: string;
  valor: number;
}

export function Barras({
  filas,
  media,
  formato = formatoMinutos,
}: {
  filas: readonly FilaDeBarra[];
  media?: number;
  /** Cómo se escribe la cifra de cada barra. Minutos por defecto. */
  formato?: (valor: number) => string;
}) {
  // Todas a cero: barras vacías y una raya pegada al borde no dicen nada que
  // no diga ya la cifra (o la tabla de al lado).
  if (filas.every((f) => f.valor === 0)) return null;
  const tope = Math.max(Number.EPSILON, media ?? 0, ...filas.map((f) => f.valor));
  return (
    <ul className="nx-barras">
      {filas.map((f) => (
        <li key={f.clave}>
          <span className="nx-barras__texto">{f.texto}</span>
          <span className="nx-barras__carril" aria-hidden="true">
            <span
              className={`nx-barras__relleno${media !== undefined && f.valor > media ? ' nx-barras__relleno--encima' : ''}`}
              style={{ width: `${(f.valor / tope) * 100}%` }}
            />
            {media !== undefined && <span className="nx-barras__media" style={{ left: `${(media / tope) * 100}%` }} />}
          </span>
          <span className="nx-barras__cifra">{formato(f.valor)}</span>
        </li>
      ))}
    </ul>
  );
}
