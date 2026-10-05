/**
 * Correcciones de fichajes: las que esperan por mí y las que he pedido.
 *
 * Es la `CorreccionesScreen` de la app. «Esperan por ti» junta tres cosas que
 * llegan por el mismo aviso: las solicitudes del equipo (si puedo aprobar), las
 * que otra persona propone sobre **mis** fichajes (ADR 015: nadie corrige el
 * registro de otro sin que el afectado se entere) y las disputas que le tocan
 * a Recursos Humanos. Qué botones salen no se deduce aquí: lo dicen
 * `puedoResolver` y `puedoDisputar`, que calcula el servidor con la misma regla
 * que luego aplica.
 *
 * **Una solicitud que solo añade una pausa** lleva las mismas horas propuestas
 * que actuales: la línea «De … a …» solo sale si las horas cambian, para que no
 * parezca una corrección que no cambia nada y se apruebe a ciegas.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useListaPaginada, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Insignia, type Tono } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, ErrorConReintento, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { revisiones } from '../../i18n/es/revisiones';
import { diaEnEmpresa, fechaCorta, hora, horaDeSalida, horaEnEmpresa, minutos } from '../../util/fechas';

const C = revisiones.correcciones;

type Correccion = components['schemas']['CorrectionResponse'];

const TONO: Record<string, Tono> = { PENDIENTE: 'aviso', APROBADA: 'exito', RECHAZADA: 'error', EN_DISPUTA: 'info' };
const CLAVE = ['correcciones'] as const;

function tramo(entrada: string | undefined, salida: string | undefined): string {
  return `${horaEnEmpresa(entrada)}–${horaDeSalida(entrada, salida)}`;
}

/** Lo que cambia, línea a línea: las horas (si cambian), la pausa y el reparto. */
export function cambiosDe(c: Correccion): string[] {
  const lineas: string[] = [];
  if (c.horaEntradaActual !== c.horaEntradaPropuesta || c.horaSalidaActual !== c.horaSalidaPropuesta) {
    lineas.push(C.deA(tramo(c.horaEntradaActual, c.horaSalidaActual), tramo(c.horaEntradaPropuesta, c.horaSalidaPropuesta)));
  }
  if (c.pausaInicioPropuesta && c.pausaFinPropuesta) {
    lineas.push(C.soloPausa(hora(c.pausaInicioPropuesta), hora(c.pausaFinPropuesta)));
  }
  const reparto = c.repartoPropuesto ?? [];
  if (reparto.length > 0) {
    lineas.push(C.reparto(reparto.map((r) => `${r.codigo ?? ''} ${minutos(r.minutos ?? 0)}`).join(', ')));
  }
  return lineas;
}

/** Quién pide qué: «Marta propone corregir un fichaje de Javier» o «Javier pide corregir un fichaje suyo». */
function quien(c: Correccion): string {
  const solicitante = c.solicitante?.nombre ?? '';
  const empleado = c.empleado?.nombre ?? '';
  return solicitante === empleado ? C.pideElSuyo(solicitante) : C.quienPide(solicitante, empleado);
}

function TarjetaDeCorreccion({ c, acciones }: { c: Correccion; acciones?: ReactNode }) {
  return (
    <li className="nx-incidencia">
      <div className="nx-incidencia__cabecera">
        <strong>{fechaCorta(c.horaEntradaActual ? diaEnEmpresa(c.horaEntradaActual) : undefined)}</strong>
        {c.estado && <Insignia tono={TONO[c.estado] ?? 'neutro'}>{C.estados[c.estado] ?? c.estado}</Insignia>}
      </div>
      <span className="nx-sutil">{quien(c)}</span>
      {cambiosDe(c).map((linea) => (
        <span key={linea}>{linea}</span>
      ))}
      {c.motivo && <span className="nx-sutil">{C.motivo(c.motivo)}</span>}
      {c.motivoDisputa && <span>{C.noAcepta(c.motivoDisputa)}</span>}
      {c.comentarioResolucion && <span className="nx-sutil">{C.comentario(c.comentarioResolucion)}</span>}
      {acciones && <div className="nx-acciones-fila">{acciones}</div>}
    </li>
  );
}

type Texto = { c: Correccion; que: 'rechazar' | 'disputar' };

function EsperanPorTi() {
  const [pidiendoTexto, setPidiendoTexto] = useState<Texto | null>(null);
  const lista = useQuery({
    queryKey: [...CLAVE, 'pendientes'],
    queryFn: () => pedir(cliente.GET('/api/v1/correcciones/pendientes', {})),
  });

  const resolver = useMutacion(
    ({ id, aprobada, comentario }: { id: number; aprobada: boolean; comentario?: string }) =>
      pedir(
        cliente.PATCH('/api/v1/correcciones/{id}/estado', {
          params: { path: { id } },
          body: { aprobada, ...(comentario !== undefined ? { comentario } : {}) },
        }),
      ),
    {
      // Aprobar cambia el fichaje: el historial, los totales y la firma de ese
      // mes (que queda invalidada) también.
      invalida: [CLAVE, ['fichaje'], ['dashboard'], ['firmas']],
      exito: (_r, v) => (v.aprobada ? C.aprobada : C.rechazada),
      alTerminar: () => setPidiendoTexto(null),
    },
  );

  const disputar = useMutacion(
    ({ id, motivo }: { id: number; motivo: string }) =>
      pedir(cliente.POST('/api/v1/correcciones/{id}/disputa', { params: { path: { id } }, body: { motivo } })),
    { invalida: [CLAVE], exito: C.disputada, alTerminar: () => setPidiendoTexto(null) },
  );

  const enCurso = pidiendoTexto?.que === 'disputar' ? disputar : resolver;
  const errorFuera = pidiendoTexto === null ? (resolver.error ?? disputar.error) : null;

  return (
    <>
      {errorFuera !== null && <Aviso>{errorFuera.message}</Aviso>}
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
        {(correcciones) =>
          correcciones.length === 0 ? (
            <Vacio titulo={C.vacioEsperan} />
          ) : (
            <ul className="nx-lista-incidencias" aria-label={C.esperanPorTi}>
              {correcciones.map((c) => (
                <TarjetaDeCorreccion
                  key={c.id}
                  c={c}
                  acciones={
                    c.puedoResolver === true || c.puedoDisputar === true ? (
                      <>
                        {c.puedoResolver === true && (
                          <>
                            <Boton
                              variante="texto"
                              ocupado={resolver.isPending && resolver.variables?.id === c.id && resolver.variables.aprobada}
                              onClick={() => c.id !== undefined && resolver.mutate({ id: c.id, aprobada: true })}
                            >
                              {C.aprobar}
                            </Boton>
                            <Boton variante="texto" onClick={() => setPidiendoTexto({ c, que: 'rechazar' })}>
                              {C.rechazar}
                            </Boton>
                          </>
                        )}
                        {c.puedoDisputar === true && (
                          <Boton variante="texto" onClick={() => setPidiendoTexto({ c, que: 'disputar' })}>
                            {C.disputar}
                          </Boton>
                        )}
                      </>
                    ) : undefined
                  }
                />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <DialogoDeTexto
        abierto={pidiendoTexto !== null}
        titulo={pidiendoTexto?.que === 'disputar' ? C.disputarTitulo : C.rechazarTitulo}
        ayuda={pidiendoTexto?.que === 'disputar' ? C.disputarAyuda : C.rechazarAyuda}
        etiqueta={C.texto}
        vacio={C.textoVacio}
        boton={pidiendoTexto?.que === 'disputar' ? C.enviar : C.rechazar}
        peligro={pidiendoTexto?.que === 'rechazar'}
        maxLength={500}
        ocupado={enCurso.isPending}
        error={enCurso.error?.message ?? null}
        alEnviar={(texto) => {
          const id = pidiendoTexto?.c.id;
          if (id === undefined) return;
          if (pidiendoTexto?.que === 'disputar') disputar.mutate({ id, motivo: texto });
          else resolver.mutate({ id, aprobada: false, comentario: texto });
        }}
        alCerrar={() => {
          resolver.reset();
          disputar.reset();
          setPidiendoTexto(null);
        }}
      />
    </>
  );
}

function PedidasPorMi() {
  const lista = useListaPaginada([...CLAVE, 'mias'], (pagina) =>
    pedir(cliente.GET('/api/v1/correcciones/mias', { params: { query: { pagina, tamano: 30 } } })),
  );

  if (lista.isPending) return <Esqueleto lineas={4} />;
  if (lista.isError && lista.elementos.length === 0) {
    return <ErrorConReintento mensaje={lista.error.message} alReintentar={() => void lista.refetch()} />;
  }

  return (
    <>
      {lista.elementos.length === 0 ? (
        <Vacio titulo={C.vacioPedidas} />
      ) : (
        <ul className="nx-lista-incidencias" aria-label={C.pedidasPorMi}>
          {lista.elementos.map((c) => (
            <TarjetaDeCorreccion key={c.id} c={c} />
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

export function Correcciones() {
  const [pestana, setPestana] = useState<'esperan' | 'pedidas'>('esperan');

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={C.titulo} />
      <section className="nx-tarjeta">
        <Pestanas
          etiqueta={C.pestanas}
          pestanas={[
            { clave: 'esperan', texto: C.esperanPorTi },
            { clave: 'pedidas', texto: C.pedidasPorMi },
          ]}
          activa={pestana}
          alCambiar={setPestana}
        >
          {pestana === 'esperan' ? <EsperanPorTi /> : <PedidasPorMi />}
        </Pestanas>
      </section>
    </div>
  );
}
