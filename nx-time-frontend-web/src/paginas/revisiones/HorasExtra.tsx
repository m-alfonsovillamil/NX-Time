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
import { Aviso, Boton, Campo, Insignia, Selector, type Tono } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Dialogo } from '../../componentes/Dialogo';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { NotaDeRevision, TarjetaDeRevision } from '../../componentes/Revision';
import { revisiones } from '../../i18n/es/revisiones';
import { fechaCorta, hoyEnEmpresa, minutos } from '../../util/fechas';

const H = revisiones.horasExtra;

type AvisoDeHorasExtra = components['schemas']['OvertimeAlertResponse'];
type Bolsa = components['schemas']['OvertimeBalanceResponse'];

const TONO: Record<string, Tono> = { ABIERTO: 'aviso', JUSTIFICADO: 'info', ACEPTADO: 'exito' };
const CLAVE = ['horas-extra'] as const;
type Compensacion = 'PAGO' | 'DESCANSO';

/**
 * Cómo se compensan unas aceptadas (ADR 061). Las aceptadas antes de poder
 * elegir llegan sin compensación y cuentan como pagadas: no se dice nada.
 */
export function textoDeCompensacion(a: AvisoDeHorasExtra, hoy: string): string | null {
  if (a.estado !== 'ACEPTADO' || a.compensacion === undefined || a.compensacion === null) return null;
  if (a.compensacion === 'PAGO') return H.sePagan;
  if (a.descansoDisfrutadoEn) return H.descansadas(fechaCorta(a.descansoDisfrutadoEn));
  const antesDe = a.descansoAntesDe ?? a.fecha ?? hoy;
  return antesDe < hoy ? H.caducadas(fechaCorta(antesDe)) : H.seCompensan(fechaCorta(antesDe));
}

/** Apuntar el día en que se descansaron (por defecto hoy, o el último día que vale). */
function ApuntarDescanso({ a, hoy }: { a: AvisoDeHorasExtra; hoy: string }) {
  const tope = a.descansoAntesDe !== undefined && a.descansoAntesDe < hoy ? a.descansoAntesDe : hoy;
  const [dia, setDia] = useState(tope);
  const apuntar = useMutacion(
    (fecha: string) =>
      pedir(cliente.POST('/api/v1/horas-extra/{id}/descanso', { params: { path: { id: a.id ?? 0 } }, body: { fecha } })),
    { invalida: [CLAVE, ['dashboard']], exito: H.descansoApuntado },
  );
  return (
    <div className="nx-acciones-fila nx-acciones-fila--con-aire">
      <Campo
        id={`descanso-${a.id}`}
        etiqueta={H.diaDelDescanso}
        type="date"
        min={a.fecha}
        max={tope}
        value={dia}
        onChange={(e) => setDia(e.target.value)}
      />
      <Boton variante="secundario" ocupado={apuntar.isPending} onClick={() => dia !== '' && apuntar.mutate(dia)}>
        {H.apuntarDescanso}
      </Boton>
      {apuntar.error !== null && <Aviso>{apuntar.error.message}</Aviso>}
    </div>
  );
}

/**
 * «El lun, 21 sept» o «Semana del lun, 14 sept al dom, 20 sept». Un aviso
 * semanal lleva el lunes en `fecha`: enseñarla sola se leería como si el
 * exceso fuera de ese día.
 */
/** «Semana del…» dentro de una frase: con la inicial en minúscula. */
function minuscula(texto: string): string {
  return texto.charAt(0).toLowerCase() + texto.slice(1);
}

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
  ocupado?: Compensacion | null;
  alAceptar?: (compensacion: Compensacion) => void;
  alJustificar?: () => void;
}) {
  const esperaDecision = a.estado === 'ABIERTO';
  const hoy = hoyEnEmpresa();
  const compensacion = textoDeCompensacion(a, hoy);
  const descansoPendiente =
    a.estado === 'ACEPTADO' && a.compensacion === 'DESCANSO' && !a.descansoDisfrutadoEn && (a.fecha ?? hoy) <= hoy;
  const cuando = periodoDe(a);
  const deOtro = delEquipo && a.usuario !== undefined && a.usuario !== null && a.usuario !== '';
  return (
    <TarjetaDeRevision
      // La de otra persona empieza por su nombre: con veinte pendientes es lo
      // que se busca primero. La propia, por el día o la semana.
      titulo={deOtro ? a.usuario : cuando}
      subtitulo={deOtro ? cuando : undefined}
      estado={a.estado && <Insignia tono={TONO[a.estado] ?? 'neutro'}>{H.estados[a.estado] ?? a.estado}</Insignia>}
      // El exceso Y el listón, siempre juntos: «3 h de más» a secas no se
      // puede juzgar, en una semana con un puente el listón son 30 h.
      cifra={`+${minutos(a.minutosExtra ?? 0)}`}
      cifraDetalle={H.sobre(minutos(a.minutosEsperados ?? 0))}
      acciones={
        delEquipo && esperaDecision && alAceptar && alJustificar ? (
          <>
            <Boton variante="contorno" onClick={alJustificar}>
              {H.justificar}
            </Boton>
            <Boton variante="secundario" ocupado={ocupado === 'PAGO'} onClick={() => alAceptar('PAGO')}>
              {H.aceptar}
            </Boton>
            <Boton variante="secundario" ocupado={ocupado === 'DESCANSO'} onClick={() => alAceptar('DESCANSO')}>
              {H.botonDescanso}
            </Boton>
          </>
        ) : undefined
      }
    >
      {a.justificacion && <NotaDeRevision etiqueta={H.notaJustificacion}>{a.justificacion}</NotaDeRevision>}
      {compensacion !== null && <span className="nx-sutil">{compensacion}</span>}
      {a.revisadoPor && <span className="nx-sutil">{H.revisadoPor(a.revisadoPor)}</span>}
      {delEquipo && descansoPendiente && <ApuntarDescanso a={a} hoy={hoy} />}
    </TarjetaDeRevision>
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
  // Pagar o compensar no tiene vuelta atrás (el servidor da 409 a la segunda
  // decisión), y los tres botones van juntos: un toque en el de al lado
  // decidiría otra cosa. Se confirma diciendo qué pasa con la bolsa.
  const [confirmando, setConfirmando] = useState<{ a: AvisoDeHorasExtra; compensacion: Compensacion } | null>(null);
  const lista = useQuery({
    queryKey: [...CLAVE, 'equipo', anio],
    queryFn: () => pedir(cliente.GET('/api/v1/horas-extra/equipo', { params: { query: { anio } } })),
  });

  const revisar = useMutacion(
    ({
      id,
      aceptar,
      justificacion,
      compensacion,
    }: {
      id: number;
      aceptar: boolean;
      justificacion?: string;
      compensacion?: Compensacion;
    }) =>
      pedir(
        cliente.PATCH('/api/v1/horas-extra/{id}', {
          params: { path: { id } },
          body: {
            aceptar,
            ...(justificacion !== undefined ? { justificacion } : {}),
            ...(compensacion !== undefined ? { compensacion } : {}),
          },
        }),
      ),
    {
      invalida: [CLAVE, ['dashboard']],
      exito: (_r, v) => (!v.aceptar ? H.justificada : v.compensacion === 'DESCANSO' ? H.compensada : H.aceptada),
      alTerminar: () => {
        setJustificando(null);
        setConfirmando(null);
      },
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
                  ocupado={
                    revisar.isPending && revisar.variables?.id === a.id && revisar.variables.aceptar
                      ? (revisar.variables.compensacion ?? 'PAGO')
                      : null
                  }
                  alAceptar={(compensacion) => setConfirmando({ a, compensacion })}
                  alJustificar={() => setJustificando(a)}
                />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <Dialogo
        abierto={confirmando !== null}
        titulo={confirmando?.compensacion === 'DESCANSO' ? H.confirmarDescansoTitulo : H.confirmarPagoTitulo}
        alCerrar={() => setConfirmando(null)}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setConfirmando(null)}>
              {H.cancelar}
            </Boton>
            <Boton
              ocupado={revisar.isPending}
              onClick={() =>
                confirmando?.a.id !== undefined &&
                revisar.mutate({ id: confirmando.a.id, aceptar: true, compensacion: confirmando.compensacion })
              }
            >
              {confirmando?.compensacion === 'DESCANSO' ? H.botonDescanso : H.aceptar}
            </Boton>
          </>
        }
      >
        {confirmando !== null && (
          <p>
            {(confirmando.compensacion === 'DESCANSO' ? H.confirmarDescansoTexto : H.confirmarPagoTexto)(
              minutos(confirmando.a.minutosExtra ?? 0),
              confirmando.a.usuario ?? '',
              minuscula(periodoDe(confirmando.a)),
            )}
          </p>
        )}
      </Dialogo>

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
  const anioActual = Number(hoyEnEmpresa().slice(0, 4));
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
    <div className="nx-pagina">
      <CabeceraDePagina titulo={H.titulo} />
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
