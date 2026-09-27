/**
 * Incidencias de cuadrante (ADR 024): las mías para explicarlas y, con
 * `cuadrante:incidencias:revisar`, las del equipo para decidir.
 *
 * Es la `IncidenciasScreen` de la app: una sola pantalla con las dos partes,
 * porque los dos avisos que llevan aquí (la incidencia a quien la tiene, el
 * resumen de la noche a quien revisa) tienen el mismo destino, `incidencias`.
 *
 * **Se detectan, no se imputan**: una incidencia no descuenta nada. Explicarla
 * se puede rehacer mientras nadie haya decidido. Decidir sobre las propias no
 * se puede, tenga el permiso que tenga: el servidor ya las deja fuera de la
 * bandeja, y aquí no se ofrecen.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useListaPaginada, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { useSesion } from '../../api/useSesion';
import { Aviso, Boton, Insignia, Selector, type Tono } from '../../componentes/Basicos';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, ErrorConReintento, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { cuadrante } from '../../i18n/es/cuadrante';
import { fechaCorta, hora, hoyEnEspana, minutos } from '../../util/fechas';

const I = cuadrante.incidencias;

type Incidencia = components['schemas']['ScheduleIncidentResponse'];

const TONO: Record<string, Tono> = { PENDIENTE: 'aviso', JUSTIFICADA: 'info', ACEPTADA: 'exito', RECHAZADA: 'error' };
const CLAVES = { todas: ['incidencias'] as const };

/** Qué pasó, en una frase: la hora real frente a la prevista. */
export function detalleDe(i: Incidencia): string {
  const cuanto = minutos(i.minutos ?? 0);
  const prevista = i.horaPrevista ?? '';
  switch (i.tipo) {
    case 'RETRASO':
      return I.detalleRetraso(hora(i.horaReal).replace(' h', ''), prevista, cuanto);
    case 'SALIDA_ANTICIPADA':
      return I.detalleSalida(hora(i.horaReal).replace(' h', ''), prevista, cuanto);
    case 'AUSENCIA':
      return I.detalleAusencia(cuanto, prevista);
    default:
      return prevista;
  }
}

function decidida(i: Incidencia): boolean {
  return i.estado === 'ACEPTADA' || i.estado === 'RECHAZADA';
}

function TarjetaDeIncidencia({ i, delEquipo, acciones }: { i: Incidencia; delEquipo: boolean; acciones: ReactNode }) {
  return (
    <li className="nx-incidencia">
      <div className="nx-incidencia__cabecera">
        <strong>
          {delEquipo && i.usuario ? `${i.usuario} · ` : ''}
          {I.tipos[i.tipo ?? ''] ?? i.tipo} · {fechaCorta(i.fecha)}
        </strong>
        {i.estado && <Insignia tono={TONO[i.estado] ?? 'neutro'}>{I.estados[i.estado] ?? i.estado}</Insignia>}
      </div>
      <span>{detalleDe(i)}</span>
      {i.justificacion && <span className="nx-sutil">{I.justificacion(i.justificacion)}</span>}
      {i.resueltaPor && (
        <span className="nx-sutil">
          {i.comentarioResolucion ? I.resueltaConComentario(i.resueltaPor, i.comentarioResolucion) : I.resueltaPor(i.resueltaPor)}
        </span>
      )}
      {acciones !== null && <div className="nx-acciones-fila">{acciones}</div>}
    </li>
  );
}

function Mias() {
  const anioActual = Number(hoyEnEspana().slice(0, 4));
  const [anio, setAnio] = useState(anioActual);
  const [explicando, setExplicando] = useState<Incidencia | null>(null);

  const lista = useQuery({
    queryKey: [...CLAVES.todas, 'mias', anio],
    queryFn: () => pedir(cliente.GET('/api/v1/incidencias/mias', { params: { query: { anio } } })),
  });

  const justificar = useMutacion(
    ({ id, texto }: { id: number; texto: string }) =>
      pedir(cliente.POST('/api/v1/incidencias/{id}/justificacion', { params: { path: { id } }, body: { texto } })),
    { invalida: [CLAVES.todas], exito: I.justificada, alTerminar: () => setExplicando(null) },
  );

  return (
    <>
      <div className="nx-filtros">
        <Selector
          id="incidencias-anio"
          etiqueta={I.anio}
          value={String(anio)}
          onChange={(e) => setAnio(Number(e.target.value))}
          opciones={[anioActual, anioActual - 1].map((a) => ({ valor: String(a), texto: String(a) }))}
        />
      </div>
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
        {(incidencias) =>
          incidencias.length === 0 ? (
            <Vacio titulo={I.vacio} />
          ) : (
            <ul className="nx-lista-incidencias" aria-label={I.tablaMias}>
              {incidencias.map((i) => (
                <TarjetaDeIncidencia
                  key={i.id}
                  i={i}
                  delEquipo={false}
                  acciones={
                    decidida(i) ? null : (
                      <Boton variante="texto" onClick={() => setExplicando(i)}>
                        {i.estado === 'JUSTIFICADA' ? I.cambiarJustificacion : I.justificar}
                      </Boton>
                    )
                  }
                />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <DialogoDeTexto
        abierto={explicando !== null}
        titulo={I.justificarTitulo}
        ayuda={I.justificarAyuda}
        etiqueta={I.texto}
        vacio={I.textoVacio}
        boton={I.enviar}
        ocupado={justificar.isPending}
        error={justificar.error?.message ?? null}
        inicial={explicando?.justificacion ?? ''}
        alEnviar={(texto) => explicando?.id !== undefined && justificar.mutate({ id: explicando.id, texto })}
        alCerrar={() => {
          justificar.reset();
          setExplicando(null);
        }}
      />
    </>
  );
}

function DelEquipo() {
  const [rechazando, setRechazando] = useState<Incidencia | null>(null);
  const lista = useListaPaginada([...CLAVES.todas, 'equipo'], (pagina) =>
    pedir(cliente.GET('/api/v1/incidencias/equipo', { params: { query: { resueltas: false, pagina, tamano: 50 } } })),
  );

  const decidir = useMutacion(
    ({ id, aceptar, comentario }: { id: number; aceptar: boolean; comentario?: string }) =>
      pedir(
        cliente.POST('/api/v1/incidencias/{id}/resolucion', {
          params: { path: { id } },
          body: { aceptar, ...(comentario !== undefined ? { comentario } : {}) },
        }),
      ),
    {
      invalida: [CLAVES.todas],
      exito: (_r, v) => (v.aceptar ? I.aceptada : I.rechazada),
      alTerminar: () => setRechazando(null),
    },
  );

  if (lista.isPending) return <Esqueleto lineas={4} />;
  if (lista.isError && lista.elementos.length === 0) {
    return <ErrorConReintento mensaje={lista.error.message} alReintentar={() => void lista.refetch()} />;
  }

  return (
    <>
      {decidir.error !== null && rechazando === null && <Aviso>{decidir.error.message}</Aviso>}
      {lista.elementos.length === 0 ? (
        <Vacio titulo={I.equipoVacio} />
      ) : (
        <ul className="nx-lista-incidencias" aria-label={I.tablaEquipo}>
          {lista.elementos.map((i) => (
            <TarjetaDeIncidencia
              key={i.id}
              i={i}
              delEquipo
              acciones={
                <>
                  <Boton
                    variante="texto"
                    ocupado={decidir.isPending && decidir.variables?.id === i.id && decidir.variables.aceptar}
                    onClick={() => i.id !== undefined && decidir.mutate({ id: i.id, aceptar: true })}
                  >
                    {I.aceptar}
                  </Boton>
                  <Boton variante="texto" onClick={() => setRechazando(i)}>
                    {I.rechazar}
                  </Boton>
                </>
              }
            />
          ))}
        </ul>
      )}
      <FinDeLista
        hayMas={lista.hasNextPage}
        cargando={lista.isFetchingNextPage}
        fallo={lista.isFetchNextPageError}
        alPedirMas={() => void lista.fetchNextPage()}
      />

      <DialogoDeTexto
        abierto={rechazando !== null}
        titulo={I.rechazarTitulo}
        ayuda={I.rechazarAyuda}
        etiqueta={I.comentario}
        vacio={I.comentarioVacio}
        boton={I.rechazar}
        peligro
        ocupado={decidir.isPending}
        error={decidir.error?.message ?? null}
        alEnviar={(comentario) =>
          rechazando?.id !== undefined && decidir.mutate({ id: rechazando.id, aceptar: false, comentario })
        }
        alCerrar={() => {
          decidir.reset();
          setRechazando(null);
        }}
      />
    </>
  );
}

export function Incidencias() {
  const { puede } = useSesion();
  const puedeRevisar = puede('cuadrante:incidencias:revisar');
  const [pestana, setPestana] = useState<'mias' | 'equipo'>('mias');

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{I.titulo}</h1>
      </header>
      <p className="nx-sutil">{I.explicacion}</p>
      <section className="nx-tarjeta">
        {puedeRevisar ? (
          <Pestanas
            etiqueta={I.pestanas}
            pestanas={[
              { clave: 'mias', texto: I.mias },
              { clave: 'equipo', texto: I.delEquipo },
            ]}
            activa={pestana}
            alCambiar={setPestana}
          >
            {pestana === 'mias' ? <Mias /> : <DelEquipo />}
          </Pestanas>
        ) : (
          <Mias />
        )}
      </section>
    </div>
  );
}
