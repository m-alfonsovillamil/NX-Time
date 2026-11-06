/**
 * Mi jornada: el cronómetro, los botones de fichar, el proyecto, y debajo mi tiempo.
 *
 * Es la `FicharScreen` de la app, con la hoja de detalle de tiempo ya abierta
 * debajo. Fichar va arriba y a todo el ancho, que es a lo que se viene; en una
 * pantalla ancha, las cifras y el gráfico se reparten la fila de debajo, y en
 * el móvil va todo en una columna.
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
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { EstadoDeConsulta } from '../../componentes/Estados';
import { fichar } from '../../i18n/es/fichar';
import { duracion, fechaLarga, hora, hoyEnEmpresa } from '../../util/fechas';
import {
  CLAVES,
  TRAS_CAMBIAR_TIEMPO,
  pedirEstadoDeHoy,
  segundosDeJornada,
  useCuadranteDelDia,
  useEstadoDeHoy,
  useJornadaActiva,
  useProyectosParaFichar,
  type EstadoDeHoy,
  type Jornada,
} from './consultas';
import { DialogoPausa, type JornadaParaPausa } from './DialogoPausa';
import {
  AvisoDeUbicacion,
  ConfirmarFin,
  ConfirmarNoLaborable,
  ElegirModalidad,
  ElegirProyecto,
  type Modalidad,
} from './DialogosDeFichar';
import { Resumen } from './Resumen';
import { recordarUbicacionInformada, tomarUbicacion, ubicacionInformada } from './ubicacion';

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
  const { data: dia } = useCuadranteDelDia(hoyEnEmpresa());
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
  /** Lo que dijo el servidor al comprobar el día: si hay que preguntar la modalidad (ADR 055). */
  const [preguntaModalidad, setPreguntaModalidad] = useState(false);
  const [eligiendoModalidad, setEligiendoModalidad] = useState(false);
  const [modalidad, setModalidad] = useState<Modalidad | undefined>(undefined);
  /** Lo que queda por hacer tras enseñar para qué se toma la ubicación (ADR 056). */
  const [trasInformar, setTrasInformar] = useState<(() => void) | null>(null);

  const jornada = activa.data ?? null;
  const trabajando = jornada !== null && jornada.enPausa !== true;
  useLatido(trabajando);
  const segundos = segundosDeJornada(jornada);

  // Tras fichar se vuelve a preguntar en vez de quedarse con la respuesta:
  // así la pantalla sigue siendo la del servidor. `useMutacion` no da la
  // escritura por terminada hasta haber invalidado, así que un segundo clic no
  // puede salir con el estado de antes (fichar la salida dos veces, por ejemplo).
  const fichar = useMutacion(
    // X-Cliente: queda dicho que se fichó desde la web (ADR 054).
    async ({ modalidad: elegida, ...peticion }: { tipo: Tipo; proyectoId?: number; modalidad?: Modalidad | undefined }) => {
      // Dentro de la escritura: mientras se toma, el botón sigue ocupado. Solo al
      // entrar y al salir, nunca en las pausas (ADR 056).
      const pedirla =
        (peticion.tipo === 'INICIO' || peticion.tipo === 'FIN') &&
        consultas.getQueryData<EstadoDeHoy>(CLAVES.hoy)?.pedirUbicacion === true;
      const ubicacion = pedirla ? await tomarUbicacion() : undefined;
      return pedir(
        cliente.POST('/api/v1/fichaje', {
          body: {
            ...peticion,
            ...(elegida !== undefined ? { modalidad: elegida } : {}),
            ...(ubicacion !== undefined ? { ubicacion } : {}),
          },
          headers: { 'X-Cliente': 'web' },
        }),
      );
    },
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
  function iniciarEligiendoProyecto(elegida: Modalidad | undefined) {
    setModalidad(elegida);
    if (disponibles.length >= 2) setEligiendo('empezar');
    else fichar.mutate({ tipo: 'INICIO', modalidad: elegida });
  }

  /** Si la empresa pregunta presencial o a distancia, antes del proyecto. */
  function iniciarEligiendoModalidad(preguntar: boolean) {
    if (preguntar) setEligiendoModalidad(true);
    else iniciarEligiendoProyecto(undefined);
  }

  /** La primera vez que se va a tomar la ubicación, se dice antes para qué. */
  function informandoAntes(pedirla: boolean, seguir: () => void) {
    if (pedirla && !ubicacionInformada(sesion?.accessToken)) setTrasInformar(() => seguir);
    else seguir();
  }

  /**
   * Antes de iniciar se pregunta si hoy es laborable, y en el momento: la
   * pestaña puede llevar abierta desde ayer. **Si la pregunta falla, se inicia
   * igual**: fichar no puede quedar bloqueado por una comprobación de cortesía.
   */
  async function iniciar() {
    setComprobandoDia(true);
    let motivo: string | null = null;
    let preguntar = false;
    let pedirla = false;
    try {
      const dia = await consultas.fetchQuery({ queryKey: CLAVES.hoy, queryFn: pedirEstadoDeHoy, staleTime: 0 });
      if (dia.laborable === false) motivo = dia.motivo ?? '';
      preguntar = dia.preguntarModalidad === true;
      pedirla = dia.pedirUbicacion === true;
    } catch {
      // Se sigue: ver arriba. Sin respuesta tampoco se pregunta la modalidad.
    } finally {
      setComprobandoDia(false);
    }
    setPreguntaModalidad(preguntar);
    informandoAntes(pedirla, () => {
      if (motivo !== null) setNoLaborable(motivo);
      else iniciarEligiendoModalidad(preguntar);
    });
  }

  function pulsarPrincipal(j: Jornada | null) {
    setAviso(null);
    fichar.reset();
    if (j === null) void iniciar();
    // Se sabe sin preguntar: responder en el acto es mejor que tras una ida y vuelta.
    else if (j.enPausa === true) setAviso(F.reanudaAntes);
    else informandoAntes(hoy.data?.pedirUbicacion === true, () => setConfirmandoFin(true));
  }

  const ocupado = fichar.isPending || comprobandoDia;
  const error = aviso ?? fichar.error?.message ?? cambiarProyecto.error?.message ?? null;

  return (
    <div className="nx-pagina nx-pagina--jornada">
      <CabeceraDePagina titulo={F.titulo} descripcion={sesion !== null ? F.saludo(sesion.nombre) : fechaLarga()} />

      {hoy.data?.laborable === false && <Aviso>{F.noLaborable(hoy.data.motivo ?? '')}</Aviso>}

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
          const puedeCambiar = j !== null && j.enPausa !== true && disponibles.some((p) => p.id !== enCurso?.id);

          return (
            <>
              {/* Fichar es lo que se viene a hacer: ocupa el ancho de la página,
                  con el cronómetro y los botones dentro. El resumen va debajo. */}
              <section className={`nx-tarjeta nx-jornada nx-jornada--${estado}`}>
                <p className="nx-jornada__estado">{texto}</p>
                <p className="nx-jornada__cronometro">{j === null ? '—' : duracion(segundos)}</p>
                <p className="nx-sutil">{desde}</p>
                {j !== null && (j.segundosPausaAcumulados ?? 0) > 0 && (
                  <p className="nx-sutil">{F.pausaAcumulada(duracion(j.segundosPausaAcumulados ?? 0))}</p>
                )}
                <AvisoDeCuadrante />
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

                <div className="nx-jornada__acciones">
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
                    {/* Del color de `ColoresJornada`, como el botón de la app: verde
                        para entrar, rojo para salir, ámbar si está en pausa. */}
                    <Boton
                      className={`nx-boton--fichar nx-boton--fichar-${estado}`}
                      ocupado={ocupado}
                      onClick={() => pulsarPrincipal(j)}
                    >
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
                </div>
              </section>

              <ConfirmarFin
                jornada={j}
                segundosTrabajados={segundos}
                abierto={confirmandoFin && j !== null}
                ocupado={fichar.isPending}
                alConfirmar={() => fichar.mutate({ tipo: 'FIN' })}
                alCancelar={() => setConfirmandoFin(false)}
              />
              <ElegirProyecto
                modo={eligiendo}
                proyectos={proyectos.data}
                ocupado={fichar.isPending || cambiarProyecto.isPending}
                alElegir={(proyectoId) => {
                  if (eligiendo === 'empezar') fichar.mutate({ tipo: 'INICIO', proyectoId, modalidad });
                  else if (j?.id !== undefined) cambiarProyecto.mutate({ fichajeId: j.id, proyectoId });
                }}
                alCancelar={() => setEligiendo(null)}
              />
            </>
          );
        }}
      </EstadoDeConsulta>

      <Resumen minutosEnCurso={jornada !== null ? Math.floor(segundos / 60) : 0} />

      <ConfirmarNoLaborable
        motivo={noLaborable}
        alConfirmar={() => {
          setNoLaborable(null);
          iniciarEligiendoModalidad(preguntaModalidad);
        }}
        alCancelar={() => setNoLaborable(null)}
      />
      <ElegirModalidad
        abierto={eligiendoModalidad}
        alElegir={(elegida) => {
          setEligiendoModalidad(false);
          iniciarEligiendoProyecto(elegida);
        }}
        alCancelar={() => setEligiendoModalidad(false)}
      />
      <AvisoDeUbicacion
        abierto={trasInformar !== null}
        alEntendido={() => {
          recordarUbicacionInformada(sesion?.accessToken);
          trasInformar?.();
          setTrasInformar(null);
        }}
        alCancelar={() => setTrasInformar(null)}
      />
      <DialogoPausa jornada={pausaDe} alCerrar={() => setPausaDe(null)} />
    </div>
  );
}
