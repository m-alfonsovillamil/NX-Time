/**
 * El calendario de un mes: festivos, mis ausencias y, con permiso, las del equipo.
 *
 * Es la `CalendarioScreen` de la app, en solo lectura: gestionar los festivos
 * de la empresa es otra sección («Calendario laboral», fase W6), porque es otro
 * trabajo y otro permiso.
 *
 * **La rejilla es una tabla de verdad** (`<table>` con cabeceras de día), y cada
 * día es un botón con su resumen en `aria-label` («lunes, 12 de octubre:
 * festivo, Fiesta Nacional; 2 personas ausentes»): de otro modo, quien no ve
 * la pantalla oiría una lista de números sin saber qué tiene cada uno.
 *
 * Las ausencias rechazadas no se pintan: una rejilla llena de días que al final
 * no fueron ausencia confunde más que informa. Las pendientes, sí, con otro
 * tono: son las que hay que tener en cuenta al pedir las propias.
 */

import { useState } from 'react';

import { useSesion } from '../../api/useSesion';
import { Boton, Insignia } from '../../componentes/Basicos';
import { ErrorConReintento, Esqueleto, Vacio } from '../../componentes/Estados';
import { ausencias as A } from '../../i18n/es/ausencias';
import type { components } from '../../api/schema';
import { diaLargo, diasDelRango, fechaCorta, hoyEnEmpresa, lunesDe, mesYAnio, sumarDias, ultimoDeMes } from '../../util/fechas';
import { useMesDelCalendario } from './consultas';

const C = A.calendario;

type AusenciaDelCalendario = components['schemas']['CalendarAbsenceDTO'];
type Festivo = components['schemas']['HolidayResponse'];

function dosCifras(n: number): string {
  return String(n).padStart(2, '0');
}

/** Las semanas que se pintan: de lunes a domingo, con los días de los meses de al lado para completar. */
export function semanasDelMes(anio: number, mes: number): string[][] {
  const primero = `${anio}-${dosCifras(mes)}-01`;
  const ultimo = ultimoDeMes(primero);
  const desde = lunesDe(primero);
  const hasta = sumarDias(lunesDe(ultimo), 6);
  const dias = diasDelRango(desde, hasta);
  const semanas: string[][] = [];
  for (let i = 0; i < dias.length; i += 7) semanas.push(dias.slice(i, i + 7));
  return semanas;
}

/** Las ausencias vivas (no rechazadas) que tocan un día. */
export function ausenciasDelDia(lista: readonly AusenciaDelCalendario[], dia: string): AusenciaDelCalendario[] {
  return lista.filter(
    (a) => a.estado !== 'RECHAZADA' && (a.fechaInicio ?? '') <= dia && dia <= (a.fechaFin ?? a.fechaInicio ?? ''),
  );
}

function resumenDelDia(dia: string, festivo: Festivo | undefined, ausentes: number): string {
  const partes = [diaLargo(dia)];
  if (festivo !== undefined) partes.push(`${C.festivo}, ${festivo.descripcion ?? ''}`);
  if (ausentes > 0) partes.push(C.ausentes(ausentes));
  return partes.join('; ');
}

export function Calendario() {
  const { puede } = useSesion();
  const hoy = hoyEnEmpresa();
  const [anio, setAnio] = useState(Number(hoy.slice(0, 4)));
  const [mes, setMes] = useState(Number(hoy.slice(5, 7)));
  const [conEquipo, setConEquipo] = useState(false);
  const [elegido, setElegido] = useState<string>(hoy);
  const puedeVerEquipo = puede('ausencia:leer:equipo');

  const consulta = useMesDelCalendario(anio, mes, conEquipo && puedeVerEquipo);

  function moverMes(delta: number) {
    const total = anio * 12 + (mes - 1) + delta;
    const nuevoAnio = Math.floor(total / 12);
    const nuevoMes = (total % 12) + 1;
    setAnio(nuevoAnio);
    setMes(nuevoMes);
    setElegido(`${nuevoAnio}-${dosCifras(nuevoMes)}-01`);
  }

  function irAHoy() {
    setAnio(Number(hoy.slice(0, 4)));
    setMes(Number(hoy.slice(5, 7)));
    setElegido(hoy);
  }

  const prefijoDelMes = `${anio}-${dosCifras(mes)}-`;
  const festivos = (consulta.data?.festivos ?? []).filter((f) => (f.fecha ?? '').startsWith(prefijoDelMes));
  const festivoDe = new Map(festivos.map((f) => [f.fecha ?? '', f]));
  const lista = consulta.data?.ausencias ?? [];
  const delElegido = ausenciasDelDia(lista, elegido);

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{C.titulo}</h1>
      </header>

      <section className="nx-tarjeta">
        <div className="nx-calendario__barra">
          {/* Las flechas y el mes van juntos: separados en el móvil, «›» acababa en otro renglón. */}
          <div className="nx-calendario__navegacion">
            <Boton variante="texto" aria-label={C.anterior} onClick={() => moverMes(-1)}>
              ‹
            </Boton>
            <h2 className="nx-calendario__mes" aria-live="polite">
              {mesYAnio(anio, mes)}
            </h2>
            <Boton variante="texto" aria-label={C.siguiente} onClick={() => moverMes(1)}>
              ›
            </Boton>
          </div>
          <Boton variante="secundario" onClick={irAHoy}>
            {C.hoy}
          </Boton>
          {puedeVerEquipo && (
            <label className="nx-casilla nx-calendario__equipo">
              <input type="checkbox" checked={conEquipo} onChange={(e) => setConEquipo(e.target.checked)} />
              {C.verEquipo}
            </label>
          )}
        </div>

        {consulta.isError && consulta.data === undefined ? (
          <ErrorConReintento mensaje={consulta.error.message} alReintentar={() => void consulta.refetch()} />
        ) : consulta.data === undefined ? (
          <Esqueleto lineas={6} />
        ) : (
          <table className="nx-calendario">
            <caption className="nx-solo-lector">{mesYAnio(anio, mes)}</caption>
            <thead>
              <tr>
                {C.diasDeLaSemana.map((d, i) => (
                  <th key={d} scope="col" abbr={C.diasDeLaSemanaLargos[i]}>
                    {d}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {semanasDelMes(anio, mes).map((semana) => (
                <tr key={semana[0]}>
                  {semana.map((dia) => {
                    const delMes = dia.startsWith(prefijoDelMes);
                    const festivo = festivoDe.get(dia);
                    const ausentes = ausenciasDelDia(lista, dia);
                    const propia = ausentes.some((a) => a.propia === true);
                    const clases = [
                      'nx-calendario__dia',
                      delMes ? '' : 'nx-calendario__dia--fuera',
                      dia === hoy ? 'nx-calendario__dia--hoy' : '',
                      festivo !== undefined ? 'nx-calendario__dia--festivo' : '',
                      propia ? 'nx-calendario__dia--propia' : '',
                    ]
                      .filter((c) => c !== '')
                      .join(' ');
                    return (
                      <td key={dia}>
                        {delMes && (
                          <button
                            type="button"
                            className={clases}
                            aria-pressed={dia === elegido}
                            aria-label={resumenDelDia(dia, festivo, ausentes.length)}
                            onClick={() => setElegido(dia)}
                          >
                            <span className="nx-calendario__numero">{Number(dia.slice(8))}</span>
                            {festivo !== undefined && (
                              <span className="nx-calendario__festivo" aria-hidden="true">
                                {festivo.descripcion}
                              </span>
                            )}
                            {ausentes.length > 0 && (
                              <span className="nx-calendario__ausentes" aria-hidden="true">
                                {ausentes.length}
                              </span>
                            )}
                          </button>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <div className="nx-rejilla-dos">
        <section className="nx-tarjeta" aria-live="polite">
          <h2>{diaLargo(elegido)}</h2>
          {festivoDe.get(elegido) !== undefined && (
            <p>
              <Insignia tono="info">{C.festivo}</Insignia> {festivoDe.get(elegido)?.descripcion}
            </p>
          )}
          {delElegido.length === 0 ? (
            <p className="nx-sutil">{C.diaSinAusencias}</p>
          ) : (
            <ul className="nx-lista-simple">
              {delElegido.map((a) => (
                <li key={a.id}>
                  <span>
                    <strong>{a.propia === true ? C.propia : a.usuario}</strong> · {a.tipo ? A.tipos[a.tipo] : ''}
                    <span className="nx-sutil"> · {A.rango(fechaCorta(a.fechaInicio), fechaCorta(a.fechaFin))}</span>
                  </span>
                  {a.estado && (
                    <Insignia tono={a.estado === 'APROBADA' ? 'exito' : 'aviso'}>{A.estados[a.estado]}</Insignia>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="nx-tarjeta">
          <h2>{C.festivosDelMes}</h2>
          {festivos.length === 0 ? (
            <Vacio icono="festivo" titulo={C.sinFestivos} />
          ) : (
            <ul className="nx-lista-simple">
              {festivos.map((f) => (
                <li key={f.id ?? f.fecha}>
                  <span>
                    <strong>{fechaCorta(f.fecha)}</strong> · {f.descripcion}
                  </span>
                  <Insignia>{C.ambitos[f.ambito ?? ''] ?? f.ambito}</Insignia>
                </li>
              ))}
            </ul>
          )}
          <p className="nx-sutil">{C.nota}</p>
        </section>
      </div>
    </div>
  );
}
