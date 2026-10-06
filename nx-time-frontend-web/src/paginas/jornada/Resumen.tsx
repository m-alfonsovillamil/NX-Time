/**
 * Mi tiempo: los totales de hoy, la semana y el mes, las vacaciones, y el gráfico de horas.
 *
 * **Todo esto es un extra.** Si falla, no se enseña un error ni se bloquea
 * nada: fichar tiene que seguir funcionando aunque el resumen no cargue, igual
 * que en la app. Se ve un hueco, no un aviso rojo junto al botón de fichar.
 *
 * **La jornada abierta se suma aquí.** El servidor solo cuenta jornadas
 * cerradas (`hora_salida IS NOT NULL`); sin esta suma, quien lleva dos horas
 * fichado leería «Hoy: 0m» con el cronómetro corriendo encima. Lo mismo para
 * la barra de hoy en el gráfico.
 */

import { useState } from 'react';

import { Cifra, Cifras } from '../../componentes/Cifra';
import { Pestanas } from '../../componentes/Pestanas';
import { Esqueleto } from '../../componentes/Estados';
import { fichar } from '../../i18n/es/fichar';
import {
  diasDelRango,
  hoyEnEmpresa,
  inicialDelDia,
  lunesDe,
  minutos,
  primeroDeMes,
  sumarDias,
  ultimoDeMes,
} from '../../util/fechas';
import { useHorasPorDia, useResumen, type HorasDelDia } from './consultas';
import { GraficoDeHoras } from './GraficoDeHoras';

const R = fichar.resumen;
const H = fichar.horas;

type Periodo = 'semana' | 'mes';


/** Los días del periodo, con lo que lleva la jornada abierta sumado a hoy. */
export function conJornadaEnCurso(dias: readonly HorasDelDia[], hoy: string, minutosEnCurso: number): HorasDelDia[] {
  if (minutosEnCurso <= 0) return [...dias];
  return dias.map((d) =>
    d.fecha === hoy ? { ...d, minutosTrabajados: (d.minutosTrabajados ?? 0) + minutosEnCurso } : d,
  );
}

function rangoDe(periodo: Periodo, hoy: string): [string, string] {
  if (periodo === 'semana') {
    const lunes = lunesDe(hoy);
    return [lunes, sumarDias(lunes, 6)];
  }
  return [primeroDeMes(hoy), ultimoDeMes(hoy)];
}

function MisHoras({ minutosEnCurso }: { minutosEnCurso: number }) {
  const [periodo, setPeriodo] = useState<Periodo>('semana');
  const hoy = hoyEnEmpresa();
  const [desde, hasta] = rangoDe(periodo, hoy);
  const consulta = useHorasPorDia(desde, hasta);

  let contenido;
  if (consulta.isPending) {
    contenido = <Esqueleto lineas={4} />;
  } else if (consulta.isError) {
    // Un extra: se dice que no ha cargado, pero sin rojo ni botones.
    contenido = <p className="nx-sutil">{consulta.error.message}</p>;
  } else {
    // Si el servidor no devolviera algún día, el eje se quedaría sin él.
    const porFecha = new Map(consulta.data.map((d) => [d.fecha, d]));
    const dias = conJornadaEnCurso(
      diasDelRango(desde, hasta).map((fecha) => porFecha.get(fecha) ?? { fecha, minutosTrabajados: 0, minutosEsperados: 0 }),
      hoy,
      minutosEnCurso,
    );
    const trabajado = dias.reduce((s, d) => s + (d.minutosTrabajados ?? 0), 0);
    const esperado = dias.reduce((s, d) => s + (d.minutosEsperados ?? 0), 0);
    const total = H.total(minutos(trabajado), minutos(esperado));
    contenido = (
      <>
        <p className="nx-grafico__total">{total}</p>
        <GraficoDeHoras
          dias={dias}
          titulo={`${periodo === 'semana' ? H.semana : H.mes}: ${total}`}
          etiquetaDe={(dia, i) =>
            periodo === 'semana' ? inicialDelDia(dia) : i === 0 || (i + 1) % 5 === 0 ? String(i + 1) : ''
          }
        />
      </>
    );
  }

  return (
    <section className="nx-tarjeta">
      <h2>{H.titulo}</h2>
      <Pestanas
        etiqueta={H.pestanas}
        pestanas={[
          { clave: 'semana', texto: H.semana },
          { clave: 'mes', texto: H.mes },
        ]}
        activa={periodo}
        alCambiar={setPeriodo}
      >
        {contenido}
      </Pestanas>
    </section>
  );
}

export function Resumen({ minutosEnCurso }: { minutosEnCurso: number }) {
  const resumen = useResumen();
  const semana = (resumen.data?.minutosSemana ?? 0) + minutosEnCurso;
  const jornadaSemanal = resumen.data?.minutosJornadaSemanal ?? 0;

  return (
    <div className="nx-composicion nx-composicion--jornada">
      {resumen.data !== undefined && (
        <section className="nx-tarjeta" aria-labelledby="titulo-resumen">
          <h2 id="titulo-resumen">{R.titulo}</h2>
          <Cifras>
            <Cifra icono="reloj" etiqueta={R.hoy} valor={minutos((resumen.data.minutosHoy ?? 0) + minutosEnCurso)} />
            <Cifra
              icono="semana"
              etiqueta={R.semana}
              valor={minutos(semana)}
              {...(jornadaSemanal > 0
                ? { detalle: R.deJornada(minutos(jornadaSemanal)), progreso: semana / jornadaSemanal }
                : {})}
            />
            <Cifra icono="calendario" etiqueta={R.mes} valor={minutos((resumen.data.minutosMes ?? 0) + minutosEnCurso)} />
            {resumen.data.saldoVacaciones !== undefined && (
              <Cifra
                icono="vacaciones"
                etiqueta={R.vacaciones}
                valor={String(resumen.data.saldoVacaciones.diasDisponibles ?? 0)}
                detalle={R.vacacionesDetalle(
                  resumen.data.saldoVacaciones.diasDisponibles ?? 0,
                  resumen.data.saldoVacaciones.diasTotales ?? 0,
                )}
                {...((resumen.data.saldoVacaciones.diasTotales ?? 0) > 0
                  ? {
                      progreso:
                        (resumen.data.saldoVacaciones.diasDisponibles ?? 0) /
                        (resumen.data.saldoVacaciones.diasTotales ?? 1),
                    }
                  : {})}
              />
            )}
          </Cifras>
          {(resumen.data.ausenciasPendientes ?? 0) > 0 && (
            <p className="nx-sutil">{R.ausenciasPendientes(resumen.data.ausenciasPendientes ?? 0)}</p>
          )}
        </section>
      )}
      <MisHoras minutosEnCurso={minutosEnCurso} />
    </div>
  );
}
