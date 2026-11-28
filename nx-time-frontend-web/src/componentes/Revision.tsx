/**
 * La tarjeta de lo que espera una decisión de quien gestiona (horas extra,
 * correcciones, incidencias): la misma forma en todas, y la misma que en la
 * app (`TarjetaDeRevision` de Android), para que se lean igual de un vistazo.
 *
 * ```
 * Nombre de la persona                 [Estado]
 * Qué es y cuándo
 * +3h 15m  por encima de 40h 00m
 * Entrada  08:54 h → 08:39 h
 * ┌ Motivo ─────────────────────┐
 * └ Olvidé fichar la salida…    ┘
 * [Aprobar] [Rechazar]
 * ```
 *
 * Antes cada una era una frase («Javier pide corregir un fichaje suyo», «De
 * 08:54–17:24 h a 08:39–17:24 h») y había que leerla entera para encontrar a
 * quién y cuánto (revisión del 28/11/2026).
 */

import type { ReactNode } from 'react';

export function TarjetaDeRevision({
  titulo,
  subtitulo,
  estado,
  cifra,
  cifraDetalle,
  acciones,
  children,
}: {
  titulo: ReactNode;
  subtitulo?: ReactNode | undefined;
  estado?: ReactNode | undefined;
  /** Lo que se juzga, en grande: «+3h 15m», «34 min». */
  cifra?: string | undefined;
  /** El listón contra el que se mide la cifra, al lado. */
  cifraDetalle?: string | undefined;
  acciones?: ReactNode | undefined;
  children?: ReactNode | undefined;
}) {
  return (
    <li className="nx-incidencia nx-revision">
      <div className="nx-incidencia__cabecera">
        <strong className="nx-revision__titulo">{titulo}</strong>
        {estado}
      </div>
      {subtitulo !== undefined && <span className="nx-sutil">{subtitulo}</span>}
      {cifra !== undefined && (
        <p className="nx-revision__cifra">
          <span className="nx-revision__numero">{cifra}</span>
          {cifraDetalle !== undefined && <span className="nx-sutil">{cifraDetalle}</span>}
        </p>
      )}
      {children}
      {acciones !== undefined && <div className="nx-acciones-fila nx-revision__acciones">{acciones}</div>}
    </li>
  );
}

/** Una fila de {@link FilasDeCambio}. Sin `antes` (o igual que `despues`), el valor una vez. */
export type FilaDeCambio = { etiqueta: string; antes?: string | undefined; despues: string };

/** Filas «Entrada 08:54 h → 08:39 h»: lo de antes tachado y lo propuesto en negrita. Sin cambio, el valor una vez. */
export function FilasDeCambio({ filas }: { filas: FilaDeCambio[] }) {
  if (filas.length === 0) return null;
  return (
    <dl className="nx-revision__filas">
      {filas.map(({ etiqueta, antes, despues }) => (
        <div key={etiqueta} className="nx-revision__fila">
          <dt className="nx-sutil">{etiqueta}</dt>
          <dd>
            {antes !== undefined && antes !== despues ? (
              <>
                <del className="nx-sutil">{antes}</del>
                <span aria-hidden="true" className="nx-sutil">
                  {' → '}
                </span>
                <strong>{despues}</strong>
              </>
            ) : (
              despues
            )}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/** Lo que alguien escribió (un motivo, una justificación), en un recuadro suave. */
export function NotaDeRevision({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="nx-revision__nota">
      <span className="nx-revision__nota-etiqueta">{etiqueta}</span>
      <span>{children}</span>
    </div>
  );
}
