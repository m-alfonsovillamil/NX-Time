/**
 * Mi jornada: el cronómetro, los botones de fichar, el proyecto, y al lado mi tiempo.
 *
 * Es la `FicharScreen` de la app, con la hoja de detalle de tiempo ya abierta
 * al lado: en una pantalla ancha hay sitio para el cronómetro y el gráfico a
 * la vez, y en el móvil van uno debajo del otro.
 *
 * **El estado de la jornada no se deduce, se pregunta.** `GET /fichaje/activo`
 * devuelve la jornada abierta o un 204, y `enPausa` viene del servidor. Llevar
 * la cuenta en el cliente («he pulsado pausar, luego estoy en pausa») se
 * desincroniza en cuanto alguien fiche desde el móvil con esta pestaña
 * abierta, que es justo el caso que esta pantalla existe para demostrar. Con
 * TanStack Query, además, volver a la pestaña vuelve a preguntar.
 *
 * **Lo accesorio no bloquea fichar**: si fallan el resumen, los proyectos o el
 * cuadrante, se ficha igual. Solo la jornada activa es imprescindible.
 */

import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { useSesion } from '../../api/useSesion';
import { Aviso, Boton } from '../../componentes/Basicos';
import { EstadoDeConsulta } from '../../componentes/Estados';
import { fichar } from '../../i18n/es/fichar';
import { duracion, fechaLarga, hora, hoyEnEspana } from '../../util/fechas';
import {
  CLAVES,
  TRAS_CAMBIAR_TIEMPO,
  pedirEstadoDeHoy,
  segundosDeJornada,
  useCuadranteDelDia,
  useEstadoDeHoy,
  useJornadaActiva,
  useProyectosParaFichar,
  type Jornada,
} from './consultas';
import { DialogoPausa, type JornadaParaPausa } from './DialogoPausa';
import { ConfirmarFin, ConfirmarNoLaborable, ElegirProyecto } from './DialogosDeFichar';
import { Resumen } from './Resumen';

const F = fichar;

type Tipo = 'INICIO' | 'FIN' | 'PAUSA_INICIO' | 'PAUSA_FIN';

/** Redibuja cada segundo mientras `activo`: el cronómetro, y lo que suma con él. */
function useLatido(activo: boolean) {
  const [, setTic] = useState(0);
  useEffect(() => {
    if (!activo) return;
    const id = setInterval(() => setTic((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, [activo]);
}

/**
 * Lo que la pantalla dice del cuadrante de hoy, o nada.
 *
 * Solo se habla cuando HAY cuadrante: sin él no se inventa un horario
 * repartiendo la jornada contratada, y un festivo ya lo avisa el día no
 * laborable.
 */
function AvisoDeCuadrante() {
  const { data: dia } = useCuadranteDelDia(hoyEnEspana());
  if (dia === undefined || dia === null) return null;
  if (dia.origen !== 'CUADRANTE' && dia.origen !== 'EXCEPCION') return null;
  return <p className="nx-sutil">{dia.entrada ? F.entradaPrevista(dia.entrada) : F.sinTurnoHoy}</p>;
}

export function Fichar() {
  const { sesion } = useSesion();
  const consultas = useQueryClient();

  const activa = useJornadaActiva();
  const hoy = useEstadoDeHoy();
  const proyectos = useProyectosParaFichar();

  const [confirmandoFin, setConfirmandoFin] = useState(false);
  const [noLaborable, setNoLaborable] = useState<string | null>(null);
  const [eligiendo, setEligiendo] = useState<'empezar' | 'cambiar' | null>(null);
  const [pausaDe, setPausaDe] = useState<JornadaParaPausa | null>(null);
  /** Lo que la pantalla sabe sin preguntar («reanuda antes de terminar»). */
  const [aviso, setAviso] = useState<string | null>(null);
  const [comprobandoDia, setComprobandoDia] = useState(false);

  const jornada = activa.data ?? null;
  const trabajando = jornada !== null && jornada.enPausa !== true;
  useLatido(trabajando);
  const segundos = segundosDeJornada(jornada);

  // Tras fichar se vuelve a preguntar en vez de quedarse con la respuesta:
  // así la pantalla sigue siendo la del servidor. `useMutacion` no da la
  // escritura por terminada hasta haber invalidado, así que un segundo clic no
  // puede salir con el estado de antes (fichar la salida dos veces, por ejemplo).
  const fichar = useMutacion(
    (peticion: { tipo: Tipo; proyectoId?: number }) => pedir(cliente.POST('/api/v1/fichaje', { body: peticion })),
    {
      invalida: TRAS_CAMBIAR_TIEMPO,
      alTerminar: () => {
        setConfirmandoFin(false);
        setEligiendo(null);
      },
    },
  );

  const cambiarProyecto = useMutacion(
    ({ fichajeId, proyectoId }: { fichajeId: number; proyectoId: number }) =>
      pedir(cliente.POST('/api/v1/fichaje/{id}/proyecto', { params: { path: { id: fichajeId } }, body: { proyectoId } })),
    {
      alTerminar: (respuesta) => {
        consultas.setQueryData(CLAVES.proyectos, respuesta);
        setEligiendo(null);
      },
    },
  );

  const disponibles = proyectos.data?.disponibles ?? [];
  const enCurso = proyectos.data?.enCurso;

  /** Con dos o más proyectos se pregunta; con uno o ninguno decide el servidor (ADR 017). */
  function iniciarEligiendoProyecto() {
    if (disponibles.length >= 2) setEligiendo('empezar');
    else fichar.mutate({ tipo: 'INICIO' });
  }

  /**
   * Antes de iniciar se pregunta si hoy es laborable, y en el momento: la
   * pestaña puede llevar abierta desde ayer. **Si la pregunta falla, se inicia
   * igual**: fichar no puede quedar bloqueado por una comprobación de cortesía.
   */
  async function iniciar() {
    setComprobandoDia(true);
    let motivo: string | null = null;
    try {
      const dia = await consultas.fetchQuery({ queryKey: CLAVES.hoy, queryFn: pedirEstadoDeHoy, staleTime: 0 });
      if (dia.laborable === false) motivo = dia.motivo ?? '';
    } catch {
      // Se sigue: ver arriba.
    } finally {
      setComprobandoDia(false);
    }
    if (motivo !== null) setNoLaborable(motivo);
    else iniciarEligiendoProyecto();
  }

  function pulsarPrincipal(j: Jornada | null) {
    setAviso(null);
    fichar.reset();
    if (j === null) void iniciar();
    // Se sabe sin preguntar: responder en el acto es mejor que tras una ida y vuelta.
    else if (j.enPausa === true) setAviso(F.reanudaAntes);
    else setConfirmandoFin(true);
  }

  const ocupado = fichar.isPending || comprobandoDia;
  const error = aviso ?? fichar.error?.message ?? cambiarProyecto.error?.message ?? null;

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <div>
          <h1>{F.titulo}</h1>
          <p className="nx-sutil">{sesion !== null ? F.saludo(sesion.nombre) : fechaLarga()}</p>
        </div>
      </header>

      {hoy.data?.laborable === false && <Aviso>{F.noLaborable(hoy.data.motivo ?? '')}</Aviso>}

      <div className="nx-rejilla-jornada">
        <div className="nx-columna">
          <AvisoDeCuadrante />
          <EstadoDeConsulta consulta={activa}>
            {(j) => {
              const estado = j === null ? 'parado' : j.enPausa === true ? 'en-pausa' : 'trabajando';
              const texto = { parado: F.parado, 'en-pausa': F.enPausa, trabajando: F.trabajando }[estado];
              const desde =
                j === null
                  ? F.sinJornada
                  : j.enPausa === true && j.inicioPausaActual !== undefined
                    ? F.enPausaDesde(hora(j.inicioPausaActual))
                    : F.desde(hora(j.horaEntrada));
              const puedeCambiar =
                j !== null && j.enPausa !== true && disponibles.some((p) => p.id !== enCurso?.id);

              return (
                <>
                  <section className={`nx-tarjeta nx-jornada nx-jornada--${estado}`}>
                    <p className="nx-jornada__estado">{texto}</p>
                    <p className="nx-jornada__cronometro">{j === null ? '—' : duracion(segundos)}</p>
                    <p className="nx-sutil">{desde}</p>
                    {j !== null && (j.segundosPausaAcumulados ?? 0) > 0 && (
                      <p className="nx-sutil">{F.pausaAcumulada(duracion(j.segundosPausaAcumulados ?? 0))}</p>
                    )}
                    {j !== null && (enCurso !== undefined || disponibles.length > 0) && (
                      <div className="nx-jornada__proyecto">
                        <span>
                          {enCurso !== undefined
                            ? F.proyecto.enCurso(enCurso.codigo ?? '', enCurso.nombre ?? '')
                            : F.proyecto.sin}
                        </span>
                        {puedeCambiar && (
                          <Boton variante="texto" onClick={() => setEligiendo('cambiar')}>
                            {F.proyecto.cambiar}
                          </Boton>
                        )}
                      </div>
                    )}
                  </section>

                  {error !== null && <Aviso>{error}</Aviso>}

                  <div className="nx-acciones">
                    {j !== null && (
                      <Boton
                        variante="secundario"
                        ocupado={ocupado}
                        onClick={() => {
                          setAviso(null);
                          fichar.mutate({ tipo: j.enPausa === true ? 'PAUSA_FIN' : 'PAUSA_INICIO' });
                        }}
                      >
                        {j.enPausa === true ? F.reanudar : F.pausar}
                      </Boton>
                    )}
                    <Boton ocupado={ocupado} onClick={() => pulsarPrincipal(j)}>
                      {j === null ? F.entrar : F.salir}
                    </Boton>
                  </div>

                  {j !== null && j.id !== undefined && j.horaEntrada !== undefined && (
                    <Boton
                      variante="texto"
                      className="nx-accion-secundaria"
                      onClick={() => setPausaDe({ id: j.id ?? 0, horaEntrada: j.horaEntrada ?? '' })}
                    >
                      {F.pausa.boton}
                    </Boton>
                  )}

                  <ConfirmarFin
                    jornada={j}
                    segundosTrabajados={segundos}
                    abierto={confirmandoFin && j !== null}
                    alConfirmar={() => fichar.mutate({ tipo: 'FIN' })}
                    alCancelar={() => setConfirmandoFin(false)}
                  />
                  <ElegirProyecto
                    modo={eligiendo}
                    proyectos={proyectos.data}
                    ocupado={fichar.isPending || cambiarProyecto.isPending}
                    alElegir={(proyectoId) => {
                      if (eligiendo === 'empezar') fichar.mutate({ tipo: 'INICIO', proyectoId });
                      else if (j?.id !== undefined) cambiarProyecto.mutate({ fichajeId: j.id, proyectoId });
                    }}
                    alCancelar={() => setEligiendo(null)}
                  />
                </>
              );
            }}
          </EstadoDeConsulta>
        </div>

        <Resumen minutosEnCurso={jornada !== null ? Math.floor(segundos / 60) : 0} />
      </div>

      <ConfirmarNoLaborable
        motivo={noLaborable}
        alConfirmar={() => {
          setNoLaborable(null);
          iniciarEligiendoProyecto();
        }}
        alCancelar={() => setNoLaborable(null)}
      />
      <DialogoPausa jornada={pausaDe} alCerrar={() => setPausaDe(null)} />
    </div>
  );
}
