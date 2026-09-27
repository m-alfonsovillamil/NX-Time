/**
 * Mi historial: las jornadas recientes o las de un periodo, y lo que se puede hacer con cada una.
 *
 * **Recientes y periodo se piden distinto, a propósito** (ADR 027). Los
 * recientes van por páginas, y la siguiente llega al bajar. Un periodo se pide
 * **entero**, porque la cabecera suma sus horas, y un total hecho con la
 * primera página sería un total falso sin avisar. El servidor limita el
 * periodo a un año, así que son pocas peticiones de 200.
 *
 * El total solo suma jornadas **cerradas**: la abierta cambia cada segundo, y
 * la cabecera diría una cifra que ya no es verdad al leerla.
 *
 * Sobre una jornada cerrada se puede pedir corrección, añadir una pausa
 * olvidada o repartirla por proyectos. Son tres cosas distintas con tres
 * reglas distintas en el servidor, y por eso tres botones y no uno.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, todasLasPaginas, useListaPaginada } from '../../api/consultas';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { EstadoDeConsulta, ErrorConReintento, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { fichar } from '../../i18n/es/fichar';
import { historial } from '../../i18n/es/historial';
import {
  diasEntre,
  duracion,
  fechaCorta,
  fechaLarga,
  hora,
  horaDeSalida,
  hoyEnEspana,
  lunesDe,
  minutos,
  primeroDeMes,
  sumarDias,
  ultimoDeMes,
} from '../../util/fechas';
import { CLAVES, segundosDeJornada, type Jornada } from '../jornada/consultas';
import { DialogoPausa, type JornadaParaPausa } from '../jornada/DialogoPausa';
import { DialogoCorreccion, type JornadaCerrada } from './DialogoCorreccion';
import { DialogoReparto } from './DialogoReparto';

const H = historial;

type Periodo = 'recientes' | 'semana' | 'mes' | 'mes-anterior' | 'elegir';

const TAMANO_RECIENTES = 50;
const TAMANO_PERIODO = 200;

/** Los dos días (incluidos) de cada periodo. La semana empieza en lunes. */
export function rangoDe(periodo: Exclude<Periodo, 'recientes' | 'elegir'>, hoy: string): [string, string] {
  switch (periodo) {
    case 'semana': {
      const lunes = lunesDe(hoy);
      return [lunes, sumarDias(lunes, 6)];
    }
    case 'mes':
      return [primeroDeMes(hoy), ultimoDeMes(hoy)];
    case 'mes-anterior': {
      const delAnterior = sumarDias(primeroDeMes(hoy), -1);
      return [primeroDeMes(delAnterior), delAnterior];
    }
  }
}

/** Minutos netos de las jornadas cerradas. La abierta no suma: ver arriba. */
export function minutosCerrados(jornadas: readonly Jornada[]): number {
  return jornadas.reduce((s, j) => (j.horaSalida === undefined ? s : s + Math.floor(segundosDeJornada(j) / 60)), 0);
}

interface Acciones {
  alCorregir: (j: JornadaCerrada) => void;
  alAnadirPausa: (j: JornadaParaPausa) => void;
  alRepartir: (fichajeId: number) => void;
}

function columnas({ alCorregir, alAnadirPausa, alRepartir }: Acciones): Columna<Jornada>[] {
  return [
    { clave: 'dia', cabecera: H.dia, celda: (j) => (j.horaEntrada !== undefined ? fechaLarga(new Date(j.horaEntrada)) : '') },
    { clave: 'entrada', cabecera: H.entrada, celda: (j) => hora(j.horaEntrada) },
    {
      clave: 'salida',
      cabecera: H.salida,
      celda: (j) => (j.horaSalida === undefined ? H.enCurso : horaDeSalida(j.horaEntrada, j.horaSalida)),
    },
    {
      clave: 'pausa',
      cabecera: H.pausa,
      // Sin pausa, nada: una columna de «0m» en cada fila solo añade ruido.
      celda: (j) => ((j.minutosPausaAcumulados ?? 0) > 0 ? minutos(j.minutosPausaAcumulados ?? 0) : ''),
      numerica: true,
    },
    {
      clave: 'total',
      cabecera: H.totalColumna,
      celda: (j) => (j.horaSalida === undefined ? '—' : duracion(segundosDeJornada(j))),
      numerica: true,
    },
    {
      clave: 'acciones',
      cabecera: H.acciones,
      celda: (j) => {
        // Una jornada abierta se cierra fichando, no corrigiendo: el servidor la rechaza.
        if (j.id === undefined || j.horaEntrada === undefined || j.horaSalida === undefined) return null;
        const cerrada = { id: j.id, horaEntrada: j.horaEntrada, horaSalida: j.horaSalida };
        return (
          <div className="nx-acciones-fila" role="group" aria-label={H.accionesDe(fechaLarga(new Date(j.horaEntrada)))}>
            <Boton variante="texto" onClick={() => alRepartir(cerrada.id)}>
              {H.reparto.boton}
            </Boton>
            <Boton variante="texto" onClick={() => alAnadirPausa(cerrada)}>
              {fichar.pausa.boton}
            </Boton>
            <Boton variante="texto" onClick={() => alCorregir(cerrada)}>
              {H.correccion.boton}
            </Boton>
          </div>
        );
      },
    },
  ];
}

function Recientes({ acciones }: { acciones: Acciones }) {
  const lista = useListaPaginada([...CLAVES.historial, 'recientes'], (pagina) =>
    pedir(cliente.GET('/api/v1/fichaje/historial', { params: { query: { pagina, tamano: TAMANO_RECIENTES } } })),
  );

  if (lista.isPending) return <Esqueleto lineas={5} />;
  if (lista.isError && lista.elementos.length === 0) {
    return <ErrorConReintento mensaje={lista.error.message} alReintentar={() => void lista.refetch()} />;
  }
  if (lista.elementos.length === 0) return <Vacio titulo={H.vacioTitulo} detalle={H.vacioTexto} />;

  return (
    <>
      <Tabla titulo={H.tablaTitulo} columnas={columnas(acciones)} filas={lista.elementos} claveDeFila={(j) => j.id ?? 0} />
      <FinDeLista
        hayMas={lista.hasNextPage}
        cargando={lista.isFetchingNextPage}
        fallo={lista.isFetchNextPageError}
        alPedirMas={() => void lista.fetchNextPage()}
      />
    </>
  );
}

function DelPeriodo({ desde, hasta, acciones }: { desde: string; hasta: string; acciones: Acciones }) {
  const periodo = useQuery({
    queryKey: [...CLAVES.historial, desde, hasta],
    queryFn: () =>
      todasLasPaginas((pagina) =>
        pedir(
          cliente.GET('/api/v1/fichaje/historial', {
            params: { query: { desde, hasta, pagina, tamano: TAMANO_PERIODO } },
          }),
        ),
      ),
  });

  return (
    <EstadoDeConsulta consulta={periodo} cargando={<Esqueleto lineas={5} />}>
      {(jornadas) => (
        <>
          <p className="nx-grafico__total">{H.total(fechaCorta(desde), fechaCorta(hasta), minutos(minutosCerrados(jornadas)))}</p>
          {jornadas.length === 0 ? (
            <Vacio titulo={H.periodoVacioTitulo} detalle={H.periodoVacioTexto} />
          ) : (
            <Tabla titulo={H.tablaTitulo} columnas={columnas(acciones)} filas={jornadas} claveDeFila={(j) => j.id ?? 0} />
          )}
        </>
      )}
    </EstadoDeConsulta>
  );
}

/** «Elegir fechas»: dos días y un botón, con el límite del servidor comprobado antes. */
function ElegirFechas({ alElegir }: { alElegir: (desde: string, hasta: string) => void }) {
  const hoy = hoyEnEspana();
  const [desde, setDesde] = useState(primeroDeMes(hoy));
  const [hasta, setHasta] = useState(hoy);
  const [error, setError] = useState<string | null>(null);

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (desde === '' || hasta === '') return;
    if (hasta < desde) return setError(H.alReves);
    if (diasEntre(desde, hasta) > 365) return setError(H.maxUnAnio);
    setError(null);
    alElegir(desde, hasta);
  }

  return (
    <form className="nx-filtro-fechas" onSubmit={enviar} noValidate>
      <Campo id="historial-desde" etiqueta={H.desde} type="date" value={desde} max={hoy} onChange={(e) => setDesde(e.target.value)} />
      <Campo id="historial-hasta" etiqueta={H.hasta} type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
      <Boton type="submit" variante="secundario">
        {H.ver}
      </Boton>
      {error !== null && <Aviso>{error}</Aviso>}
    </form>
  );
}

export function Historial() {
  const [periodo, setPeriodo] = useState<Periodo>('recientes');
  const [elegido, setElegido] = useState<[string, string] | null>(null);
  const [corregir, setCorregir] = useState<JornadaCerrada | null>(null);
  const [pausaDe, setPausaDe] = useState<JornadaParaPausa | null>(null);
  const [repartir, setRepartir] = useState<number | null>(null);

  const acciones: Acciones = { alCorregir: setCorregir, alAnadirPausa: setPausaDe, alRepartir: setRepartir };
  // «Esta semana» se recalcula en cada pintado: la pestaña puede quedarse abierta de un lunes a otro.
  const hoy = hoyEnEspana();

  let contenido;
  if (periodo === 'recientes') {
    contenido = <Recientes acciones={acciones} />;
  } else if (periodo === 'elegir') {
    contenido = (
      <>
        <ElegirFechas alElegir={(desde, hasta) => setElegido([desde, hasta])} />
        {elegido !== null && <DelPeriodo desde={elegido[0]} hasta={elegido[1]} acciones={acciones} />}
      </>
    );
  } else {
    const [desde, hasta] = rangoDe(periodo, hoy);
    contenido = <DelPeriodo desde={desde} hasta={hasta} acciones={acciones} />;
  }

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{H.titulo}</h1>
      </header>

      <section className="nx-tarjeta">
        <Pestanas
          etiqueta={H.periodos}
          pestanas={[
            { clave: 'recientes', texto: H.recientes },
            { clave: 'semana', texto: H.semana },
            { clave: 'mes', texto: H.mes },
            { clave: 'mes-anterior', texto: H.mesAnterior },
            { clave: 'elegir', texto: H.elegir },
          ]}
          activa={periodo}
          alCambiar={setPeriodo}
        >
          {contenido}
        </Pestanas>
      </section>

      <DialogoCorreccion jornada={corregir} alCerrar={() => setCorregir(null)} />
      <DialogoPausa jornada={pausaDe} alCerrar={() => setPausaDe(null)} />
      <DialogoReparto fichajeId={repartir} alCerrar={() => setRepartir(null)} />
    </div>
  );
}
