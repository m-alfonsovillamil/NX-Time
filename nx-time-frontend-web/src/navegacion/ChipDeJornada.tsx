/**
 * El estado de mi jornada, siempre a la vista en la barra superior.
 *
 * Quien está en la plantilla o en un informe no sabe si sigue fichado sin
 * volver a «Mi jornada»; y olvidarse de fichar la salida es justo la
 * incidencia que más se repite. El chip lo dice con el color de
 * `ColoresJornada`, el mismo que usa la app para que se lea de lejos: verde
 * trabajando, ámbar en pausa. **Sin fichar no es una alarma** (es lo normal
 * un sábado), así que va en neutro.
 *
 * Usa la misma consulta y la misma caché que «Mi jornada»
 * (`useJornadaActiva`): no añade peticiones, y fichar en esa página lo
 * actualiza aquí en el acto. Avanza **cada medio minuto**, no cada segundo:
 * enseña horas y minutos, y repintar el marco entero cada segundo para eso
 * sería gastar batería.
 *
 * En «Mi jornada» no sale: el cronómetro grande ya está en la página.
 */

import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router';

import { T } from '../i18n/es';
import { segundosDeJornada, useJornadaActiva } from '../paginas/jornada/consultas';
import { minutos } from '../util/fechas';

const J = T.navegacion.jornada;

/** Repinta cada 30 s mientras `activo`. */
function useMedioMinuto(activo: boolean) {
  const [, setTic] = useState(0);
  useEffect(() => {
    if (!activo) return;
    const id = setInterval(() => setTic((n) => n + 1), 30_000);
    return () => clearInterval(id);
  }, [activo]);
}

export function ChipDeJornada() {
  const activa = useJornadaActiva();
  const enMiJornada = useLocation().pathname.replace(/\/+$/, '') === '/fichar';

  const jornada = activa.data ?? null;
  const trabajando = jornada !== null && jornada.enPausa !== true;
  useMedioMinuto(trabajando && !enMiJornada);

  // Mientras no se sabe (cargando, o un fallo): nada. Es un extra, y un chip
  // que dijera «Sin fichar» sin saberlo sería peor que no tenerlo.
  if (enMiJornada || activa.isPending || activa.isError) return null;

  const estado = jornada === null ? 'parado' : jornada.enPausa === true ? 'en-pausa' : 'trabajando';
  const texto = { parado: J.sinFichar, 'en-pausa': J.enPausa, trabajando: J.trabajando }[estado];
  const tiempo = jornada === null ? null : minutos(Math.floor(segundosDeJornada(jornada) / 60));

  return (
    <Link to="/fichar" className={`nx-chip-jornada nx-chip-jornada--${estado}`} aria-label={J.ir(texto, tiempo)}>
      <span className="nx-chip-jornada__punto" aria-hidden="true" />
      <span className="nx-chip-jornada__estado">{texto}</span>
      {tiempo !== null && <span className="nx-chip-jornada__tiempo">{tiempo}</span>}
    </Link>
  );
}
