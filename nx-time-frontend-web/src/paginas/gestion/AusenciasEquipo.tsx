/**
 * Las ausencias del equipo: las que esperan respuesta y las ya resueltas. Es
 * la `AusenciasEquipoScreen` de la app, y las dos rutas (`pendientes`, destino
 * del aviso de una solicitud nueva, y `resueltas`) son esta página con una
 * pestaña u otra abierta.
 *
 * **Rechazar pide motivo** (el servidor lo exige): la persona lo lee junto a su
 * solicitud. Aprobar va directo. Los días hábiles salen al lado de las fechas
 * porque «del 3 al 14» no dice cuántos días de vacaciones consume: festivos y
 * fines de semana no cuentan, y es lo que se está aprobando.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useListaPaginada, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Insignia, type Tono } from '../../componentes/Basicos';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, ErrorConReintento, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { ausencias } from '../../i18n/es/ausencias';
import { gestion } from '../../i18n/es/gestion';
import { diaEnEmpresa, fechaCorta } from '../../util/fechas';
import { CLAVE_PENDIENTES } from './claves';

const G = gestion.ausencias;

type Ausencia = components['schemas']['AbsenceResponse'];

const CLAVE = ['ausencias-equipo'] as const;
const TONO: Record<string, Tono> = { PENDIENTE: 'aviso', APROBADA: 'exito', RECHAZADA: 'error' };

function TarjetaDeAusencia({ a, acciones }: { a: Ausencia; acciones?: ReactNode }) {
  const quien = a.usuario?.nombre ?? '';
  return (
    <li className="nx-incidencia">
      <div className="nx-incidencia__cabecera">
        <strong>
          {quien} · {a.tipo ? ausencias.tipos[a.tipo] : ''}
        </strong>
        {a.estado && <Insignia tono={TONO[a.estado] ?? 'neutro'}>{ausencias.estados[a.estado]}</Insignia>}
      </div>
      <span>
        {ausencias.rango(fechaCorta(a.fechaInicio), fechaCorta(a.fechaFin))}
        {a.diasHabiles !== undefined ? ` · ${ausencias.diasHabiles(a.diasHabiles)}` : ''}
      </span>
      {a.motivo && <span className="nx-sutil">{G.motivo(a.motivo)}</span>}
      {a.aprobadoPor?.nombre && a.fechaResolucion && (
        <span className="nx-sutil">{G.resueltaPor(a.aprobadoPor.nombre, fechaCorta(diaEnEmpresa(a.fechaResolucion)))}</span>
      )}
      {a.comentarioResolucion && <span>{G.respuesta(a.comentarioResolucion)}</span>}
      {acciones && (
        <div className="nx-acciones-fila" role="group" aria-label={G.accionesDe(quien)}>
          {acciones}
        </div>
      )}
    </li>
  );
}

function Pendientes() {
  const [rechazando, setRechazando] = useState<Ausencia | null>(null);
  const lista = useQuery({
    queryKey: [...CLAVE, 'pendientes'],
    queryFn: () => pedir(cliente.GET('/api/v1/ausencias/gestor/pendientes', {})),
  });

  const resolver = useMutacion(
    ({ id, aprobar, comentario }: { id: number; aprobar: boolean; comentario?: string }) =>
      pedir(
        cliente.PATCH('/api/v1/ausencias/{id}/estado', {
          params: { path: { id } },
          body: { estado: aprobar ? 'APROBADA' : 'RECHAZADA', ...(comentario !== undefined ? { comentario } : {}) },
        }),
      ),
    {
      // Aprobar mueve el calendario del equipo y el saldo de vacaciones.
      invalida: [CLAVE, CLAVE_PENDIENTES, ['ausencias'], ['calendario']],
      exito: (_r, v) => (v.aprobar ? G.aprobada : G.rechazada),
      alTerminar: () => setRechazando(null),
    },
  );

  return (
    <>
      {resolver.error !== null && rechazando === null && <Aviso>{resolver.error.message}</Aviso>}
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
        {(solicitudes) =>
          solicitudes.length === 0 ? (
            <Vacio titulo={G.vacioPendientesTitulo} detalle={G.vacioPendientesTexto} />
          ) : (
            <ul className="nx-lista-incidencias" aria-label={G.pendientes}>
              {solicitudes.map((a) => (
                <TarjetaDeAusencia
                  key={a.id}
                  a={a}
                  acciones={
                    <>
                      <Boton
                        variante="texto"
                        ocupado={resolver.isPending && resolver.variables?.id === a.id && resolver.variables.aprobar}
                        onClick={() => a.id !== undefined && resolver.mutate({ id: a.id, aprobar: true })}
                      >
                        {G.aprobar}
                      </Boton>
                      <Boton variante="texto" onClick={() => setRechazando(a)}>
                        {G.rechazar}
                      </Boton>
                    </>
                  }
                />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <DialogoDeTexto
        abierto={rechazando !== null}
        titulo={G.rechazarTitulo(rechazando?.usuario?.nombre ?? '')}
        ayuda={G.rechazarAyuda}
        etiqueta={G.comentario}
        vacio={G.comentarioVacio}
        boton={G.rechazar}
        peligro
        maxLength={500}
        ocupado={resolver.isPending}
        error={resolver.error?.message ?? null}
        alEnviar={(comentario) => rechazando?.id !== undefined && resolver.mutate({ id: rechazando.id, aprobar: false, comentario })}
        alCerrar={() => {
          resolver.reset();
          setRechazando(null);
        }}
      />
    </>
  );
}

function Resueltas() {
  const lista = useListaPaginada([...CLAVE, 'resueltas'], (pagina) =>
    pedir(cliente.GET('/api/v1/gestor/ausencias-historial', { params: { query: { pagina, tamano: 30 } } })),
  );

  if (lista.isPending) return <Esqueleto lineas={4} />;
  if (lista.isError && lista.elementos.length === 0) {
    return <ErrorConReintento mensaje={lista.error.message} alReintentar={() => void lista.refetch()} />;
  }
  return (
    <>
      {lista.elementos.length === 0 ? (
        <Vacio titulo={G.vacioResueltasTitulo} detalle={G.vacioResueltasTexto} />
      ) : (
        <ul className="nx-lista-incidencias" aria-label={G.resueltas}>
          {lista.elementos.map((a) => (
            <TarjetaDeAusencia key={a.id} a={a} />
          ))}
        </ul>
      )}
      <FinDeLista
        hayMas={lista.hasNextPage}
        cargando={lista.isFetchingNextPage}
        fallo={lista.isFetchNextPageError}
        alPedirMas={() => void lista.fetchNextPage()}
      />
    </>
  );
}

type Pestana = 'pendientes' | 'resueltas';

export function AusenciasEquipo({ pestanaInicial = 'pendientes' }: { pestanaInicial?: Pestana }) {
  const [pestana, setPestana] = useState<Pestana>(pestanaInicial);
  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{G.titulo}</h1>
      </header>
      <section className="nx-tarjeta">
        <Pestanas
          etiqueta={G.pestanas}
          pestanas={[
            { clave: 'pendientes', texto: G.pendientes },
            { clave: 'resueltas', texto: G.resueltas },
          ]}
          activa={pestana}
          alCambiar={setPestana}
        >
          {pestana === 'pendientes' ? <Pendientes /> : <Resueltas />}
        </Pestanas>
      </section>
    </div>
  );
}

export function PaginaAusenciasResueltas() {
  return <AusenciasEquipo pestanaInicial="resueltas" />;
}
