/**
 * Horas extra (art. 35 ET): mis excesos de jornada con la bolsa anual y, con
 * `horasextra:revisar`, los del equipo para decidir si computan.
 *
 * Es la `HorasExtraScreen` de la app. El sistema detecta que una jornada o
 * una semana pasó del listón, pero no sabe si fue una intensiva pactada o un
 * fichaje mal cerrado: por eso cada aviso enseña el exceso **y** el listón con
 * el que se comparó. En una semana con un puente el listón son 30 h y no 37,5,
 * y «3 h de más» a secas no se puede juzgar.
 *
 * **Nadie revisa sus propias horas extra**, tenga el permiso que tenga: el
 * servidor ya deja los avisos propios fuera de la bandeja del equipo, y en los
 * míos no hay botones. No computar exige motivo: es lo que queda archivado.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { useSesion } from '../../api/useSesion';
import { Aviso, Boton, Insignia, Selector, type Tono } from '../../componentes/Basicos';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { revisiones } from '../../i18n/es/revisiones';
import { fechaCorta, hoyEnEspana, minutos } from '../../util/fechas';

const H = revisiones.horasExtra;

type AvisoDeHorasExtra = components['schemas']['OvertimeAlertResponse'];
type Bolsa = components['schemas']['OvertimeBalanceResponse'];

const TONO: Record<string, Tono> = { ABIERTO: 'aviso', JUSTIFICADO: 'info', ACEPTADO: 'exito' };
const CLAVE = ['horas-extra'] as const;

/**
 * «El lun, 21 sept» o «Semana del lun, 14 sept al dom, 20 sept». Un aviso
 * semanal lleva el lunes en `fecha`: enseñarla sola se leería como si el
 * exceso fuera de ese día.
 */
export function periodoDe(a: AvisoDeHorasExtra): string {
  if (a.tipo === 'SEMANAL') return H.periodoSemana(fechaCorta(a.fecha), fechaCorta(a.fechaFin ?? a.fecha));
  return H.periodoDia(fechaCorta(a.fecha));
}

function TarjetaDeBolsa({ bolsa }: { bolsa: Bolsa }) {
  const tope = bolsa.minutosTope ?? 0;
  const consumidos = bolsa.minutosConsumidos ?? 0;
  // Se acota a 100 %: pasarse del tope legal pasa en la vida real, y una barra
  // que se sale de su carril solo parece un fallo de la pantalla.
  const proporcion = tope === 0 ? 0 : Math.min(1, consumidos / tope);
  const alLimite = bolsa.alLimite === true;

  return (
    <section className="nx-bolsa" aria-labelledby="bolsa-titulo">
      <div className="nx-incidencia__cabecera">
        <h2 id="bolsa-titulo">{H.bolsa(bolsa.anio ?? 0)}</h2>
        <strong className={alLimite ? 'nx-texto-error' : undefined}>{H.consumido(minutos(consumidos), minutos(tope))}</strong>
      </div>
      <div
        className="nx-bolsa__carril"
        role="meter"
        aria-labelledby="bolsa-titulo"
        aria-valuemin={0}
        aria-valuemax={tope}
        aria-valuenow={Math.min(consumidos, tope)}
        aria-valuetext={H.consumido(minutos(consumidos), minutos(tope))}
      >
        <div
          className={`nx-bolsa__relleno${alLimite ? ' nx-bolsa__relleno--limite' : ''}`}
          style={{ width: `${proporcion * 100}%` }}
        />
      </div>
      <p className="nx-sutil">{H.bolsaDetalle(minutos(bolsa.minutosDisponibles ?? 0), Number(bolsa.avisosAbiertos ?? 0))}</p>
      {alLimite && <p className="nx-texto-error">{H.alLimite}</p>}
    </section>
  );
}

function TarjetaDeAviso({
  a,
  delEquipo,
  ocupado,
  alAceptar,
  alJustificar,
}: {
  a: AvisoDeHorasExtra;
  delEquipo: boolean;
  ocupado?: boolean;
  alAceptar?: () => void;
  alJustificar?: () => void;
}) {
  const esperaDecision = a.estado === 'ABIERTO';
  return (
    <li className="nx-incidencia">
      <div className="nx-incidencia__cabecera">
        <strong>
          {delEquipo && a.usuario ? `${a.usuario} · ` : ''}
          {periodoDe(a)}
        </strong>
        {a.estado && <Insignia tono={TONO[a.estado] ?? 'neutro'}>{H.estados[a.estado] ?? a.estado}</Insignia>}
      </div>
      <span>{H.exceso(minutos(a.minutosExtra ?? 0), minutos(a.minutosEsperados ?? 0))}</span>
      {a.justificacion && <span className="nx-sutil">{H.justificacion(a.justificacion)}</span>}
      {a.revisadoPor && <span className="nx-sutil">{H.revisadoPor(a.revisadoPor)}</span>}
      {delEquipo && esperaDecision && alAceptar && alJustificar && (
        <div className="nx-acciones-fila">
          <Boton variante="texto" ocupado={ocupado === true} onClick={alAceptar}>
            {H.aceptar}
          </Boton>
          <Boton variante="texto" onClick={alJustificar}>
            {H.justificar}
          </Boton>
        </div>
      )}
    </li>
  );
}

function Mios({ anio }: { anio: number }) {
  const bolsa = useQuery({
    queryKey: [...CLAVE, 'bolsa', anio],
    queryFn: () => pedir(cliente.GET('/api/v1/horas-extra/bolsa', { params: { query: { anio } } })),
  });
  const lista = useQuery({
    queryKey: [...CLAVE, 'mios', anio],
    queryFn: () => pedir(cliente.GET('/api/v1/horas-extra', { params: { query: { anio } } })),
  });

  return (
    <>
      {bolsa.data && <TarjetaDeBolsa bolsa={bolsa.data} />}
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
        {(avisos) =>
          avisos.length === 0 ? (
            <Vacio titulo={H.vacio} />
          ) : (
            <ul className="nx-lista-incidencias" aria-label={H.mias}>
              {avisos.map((a) => (
                <TarjetaDeAviso key={a.id} a={a} delEquipo={false} />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>
    </>
  );
}

function DelEquipo({ anio }: { anio: number }) {
  const [justificando, setJustificando] = useState<AvisoDeHorasExtra | null>(null);
  const lista = useQuery({
    queryKey: [...CLAVE, 'equipo', anio],
    queryFn: () => pedir(cliente.GET('/api/v1/horas-extra/equipo', { params: { query: { anio } } })),
  });

  const revisar = useMutacion(
    ({ id, aceptar, justificacion }: { id: number; aceptar: boolean; justificacion?: string }) =>
      pedir(
        cliente.PATCH('/api/v1/horas-extra/{id}', {
          params: { path: { id } },
          body: { aceptar, ...(justificacion !== undefined ? { justificacion } : {}) },
        }),
      ),
    {
      invalida: [CLAVE, ['dashboard']],
      exito: (_r, v) => (v.aceptar ? H.aceptada : H.justificada),
      alTerminar: () => setJustificando(null),
    },
  );

  return (
    <>
      {revisar.error !== null && justificando === null && <Aviso>{revisar.error.message}</Aviso>}
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
        {(avisos) =>
          avisos.length === 0 ? (
            <Vacio titulo={H.equipoVacio} />
          ) : (
            <ul className="nx-lista-incidencias" aria-label={H.delEquipo}>
              {avisos.map((a) => (
                <TarjetaDeAviso
                  key={a.id}
                  a={a}
                  delEquipo
                  ocupado={revisar.isPending && revisar.variables?.id === a.id && revisar.variables.aceptar}
                  alAceptar={() => a.id !== undefined && revisar.mutate({ id: a.id, aceptar: true })}
                  alJustificar={() => setJustificando(a)}
                />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <DialogoDeTexto
        abierto={justificando !== null}
        titulo={H.justificarTitulo}
        ayuda={H.justificarAyuda}
        etiqueta={H.motivo}
        vacio={H.motivoVacio}
        boton={H.justificar}
        maxLength={500}
        ocupado={revisar.isPending}
        error={revisar.error?.message ?? null}
        alEnviar={(justificacion) =>
          justificando?.id !== undefined && revisar.mutate({ id: justificando.id, aceptar: false, justificacion })
        }
        alCerrar={() => {
          revisar.reset();
          setJustificando(null);
        }}
      />
    </>
  );
}

export function HorasExtra() {
  const { puede } = useSesion();
  const puedeRevisar = puede('horasextra:revisar');
  const anioActual = Number(hoyEnEspana().slice(0, 4));
  const [anio, setAnio] = useState(anioActual);
  const [pestana, setPestana] = useState<'mios' | 'equipo'>('mios');

  const selectorDeAnio = (
    <div className="nx-filtros">
      <Selector
        id="horas-extra-anio"
        etiqueta={H.anio}
        value={String(anio)}
        onChange={(e) => setAnio(Number(e.target.value))}
        opciones={[anioActual, anioActual - 1].map((a) => ({ valor: String(a), texto: String(a) }))}
      />
    </div>
  );

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{H.titulo}</h1>
      </header>
      {puedeRevisar ? (
        <section className="nx-tarjeta">
          <Pestanas
            etiqueta={H.pestanas}
            pestanas={[
              { clave: 'mios', texto: H.mias },
              { clave: 'equipo', texto: H.delEquipo },
            ]}
            activa={pestana}
            alCambiar={setPestana}
          >
            {selectorDeAnio}
            {pestana === 'mios' ? <Mios anio={anio} /> : <DelEquipo anio={anio} />}
          </Pestanas>
        </section>
      ) : (
        <section className="nx-tarjeta">
          {selectorDeAnio}
          <Mios anio={anio} />
        </section>
      )}
    </div>
  );
}
