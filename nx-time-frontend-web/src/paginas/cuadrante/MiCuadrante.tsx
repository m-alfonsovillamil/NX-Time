/**
 * Mi cuadrante, semana a semana: a qué horas me toca, día por día.
 *
 * Es la `MiCuadranteScreen` de la app, en solo lectura: el cuadrante lo pone
 * quien planifica, y el editor es de la web (fase W7). Cada día dice de dónde
 * sale lo que se ve, porque no es lo mismo: un festivo o una ausencia aprobada
 * mandan sobre el cuadrante (NO_LABORABLE), una excepción cambia solo ese día
 * (EXCEPCION), y sin cuadrante no se inventa un horario repartiendo la jornada
 * contratada (SIN_CUADRANTE).
 *
 * **Los turnos de noche** llevan tramos que acaban pasada la medianoche
 * (`cruzaMedianoche`): se escriben con «(+1 d)» para que 22:00–06:00 no se
 * lea como un turno de menos de nada.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Boton, Insignia } from '../../componentes/Basicos';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { cuadrante } from '../../i18n/es/cuadrante';
import { diaLargo, fechaCorta, hoyEnEmpresa, lunesDe, minutos, sumarDias } from '../../util/fechas';

const C = cuadrante.cuadrante;

type DiaTeorico = components['schemas']['TheoreticalDayResponse'];
type Tramo = components['schemas']['Tramo'];

/** `09:00–14:00`, o `22:00–06:00 (+1 d)` si acaba al día siguiente. */
export function textoDelTramo(t: Tramo): string {
  return `${t.horaInicio ?? ''}–${t.horaFin ?? ''}${t.cruzaMedianoche === true ? ` ${C.otroDia}` : ''}`;
}

/** Un día del horario teórico. Lo usa también el editor de cuadrantes. */
export function Dia({ dia, esHoy }: { dia: DiaTeorico; esHoy: boolean }) {
  const tramos = dia.tramos ?? [];
  let contenido;
  if (dia.origen === 'NO_LABORABLE') {
    contenido = (
      <>
        <Insignia tono="info">{C.noLaborable}</Insignia>
        {dia.motivo && <span className="nx-sutil">{dia.motivo}</span>}
      </>
    );
  } else if (dia.origen === 'SIN_CUADRANTE') {
    contenido = <span className="nx-sutil">{C.sinCuadrante}</span>;
  } else if (tramos.length === 0) {
    contenido = <span>{C.libre}</span>;
  } else {
    contenido = <span className="nx-tramos">{tramos.map(textoDelTramo).join(' · ')}</span>;
  }

  return (
    <li className={`nx-dia-cuadrante${esHoy ? ' nx-dia-cuadrante--hoy' : ''}`}>
      <span className="nx-dia-cuadrante__fecha">{diaLargo(dia.fecha ?? '')}</span>
      <span className="nx-dia-cuadrante__horario">
        {contenido}
        {dia.origen === 'EXCEPCION' && (
          <Insignia tono="aviso">
            {C.excepcion}
            {dia.motivo ? `: ${dia.motivo}` : ''}
          </Insignia>
        )}
      </span>
      <span className="nx-dia-cuadrante__minutos">{(dia.minutos ?? 0) > 0 ? minutos(dia.minutos ?? 0) : ''}</span>
    </li>
  );
}

export function MiCuadrante() {
  const hoy = hoyEnEmpresa();
  const [lunes, setLunes] = useState(lunesDe(hoy));
  const domingo = sumarDias(lunes, 6);

  const semana = useQuery({
    queryKey: ['cuadrantes', 'mio', lunes, domingo],
    queryFn: () =>
      pedir(cliente.GET('/api/v1/cuadrantes/mio', { params: { query: { desde: lunes, hasta: domingo } } })),
    placeholderData: (anterior) => anterior,
  });

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{C.titulo}</h1>
      </header>

      <section className="nx-tarjeta">
        <div className="nx-calendario__barra">
          <div className="nx-calendario__navegacion">
            <Boton variante="texto" aria-label={C.anterior} onClick={() => setLunes(sumarDias(lunes, -7))}>
              ‹
            </Boton>
            <h2 className="nx-calendario__mes" aria-live="polite">
              {C.semana(fechaCorta(lunes), fechaCorta(domingo))}
            </h2>
            <Boton variante="texto" aria-label={C.siguiente} onClick={() => setLunes(sumarDias(lunes, 7))}>
              ›
            </Boton>
          </div>
          {lunes !== lunesDe(hoy) && (
            <Boton variante="secundario" onClick={() => setLunes(lunesDe(hoy))}>
              {C.estaSemana}
            </Boton>
          )}
        </div>

        <EstadoDeConsulta consulta={semana} cargando={<Esqueleto lineas={7} />}>
          {(dias) => {
            if (dias.every((d) => d.origen === 'SIN_CUADRANTE')) return <Vacio titulo={C.vacio} />;
            const total = dias.reduce((s, d) => s + (d.minutos ?? 0), 0);
            const plantilla = dias.find((d) => d.plantilla)?.plantilla;
            return (
              <>
                <ul className="nx-lista-cuadrante">
                  {dias.map((d) => (
                    <Dia key={d.fecha} dia={d} esHoy={d.fecha === hoy} />
                  ))}
                </ul>
                <p className="nx-grafico__total">{C.total(minutos(total))}</p>
                {plantilla && <p className="nx-sutil">{C.plantilla(plantilla)}</p>}
              </>
            );
          }}
        </EstadoDeConsulta>
      </section>

      <Link className="nx-enlace" to="/incidencias">
        {C.verIncidencias}
      </Link>
    </div>
  );
}
