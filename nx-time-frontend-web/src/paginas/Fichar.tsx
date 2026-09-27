/**
 * Mi jornada: el cronómetro y los cuatro botones de fichar.
 *
 * Es la pantalla que demuestra que la web habla con el mismo backend que la
 * app: lo que se ficha aquí sale en el historial del móvil.
 *
 * **El estado de la jornada no se deduce, se pregunta.** `GET /fichaje/activo`
 * devuelve la jornada abierta o un 204, y `enPausa` viene del servidor. La
 * tentación es llevar la cuenta en el cliente («he pulsado pausar, luego estoy
 * en pausa»), y eso se desincroniza en cuanto alguien fiche desde el móvil con
 * esta pestaña abierta — que es precisamente el caso que esta pantalla existe
 * para demostrar que funciona. Con TanStack Query, además, volver a la pestaña
 * vuelve a preguntar.
 */

import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { cliente } from '../api/cliente';
import { pedir, pedirOpcional, useMutacion } from '../api/consultas';
import { useSesion } from '../api/useSesion';
import { Aviso, Boton } from '../componentes/Basicos';
import { EstadoDeConsulta } from '../componentes/Estados';
import { T } from '../i18n/es';
import { duracion, fechaLarga, hora, segundosTrabajados } from '../util/fechas';

type Tipo = 'INICIO' | 'FIN' | 'PAUSA_INICIO' | 'PAUSA_FIN';

/** Todo lo de fichaje cuelga de `['fichaje']`: fichar invalida el árbol entero. */
export const CLAVES_FICHAJE = {
  todo: ['fichaje'] as const,
  activo: ['fichaje', 'activo'] as const,
  hoy: ['fichaje', 'hoy'] as const,
};

export function Fichar() {
  const { sesion } = useSesion();

  const activa = useQuery({
    queryKey: CLAVES_FICHAJE.activo,
    // 204 sin cuerpo: no hay jornada abierta. No es un error.
    queryFn: () => pedirOpcional(cliente.GET('/api/v1/fichaje/activo', {})),
  });
  const hoy = useQuery({
    queryKey: CLAVES_FICHAJE.hoy,
    queryFn: () => pedir(cliente.GET('/api/v1/fichaje/hoy', {})),
  });

  // Tras fichar se vuelve a preguntar en vez de quedarse con la respuesta:
  // así el estado de la pantalla sigue siendo el del servidor y no una copia
  // nuestra. Al fichar la SALIDA, por ejemplo, el servidor devuelve la
  // jornada ya cerrada, y lo que hay que enseñar es que no hay ninguna abierta.
  const fichar = useMutacion(
    (tipo: Tipo) => pedir(cliente.POST('/api/v1/fichaje', { body: { tipo } })),
    { invalida: [CLAVES_FICHAJE.todo] },
  );

  /** Redibuja el cronómetro. Solo mientras corre: en pausa no avanza. */
  const [, setTic] = useState(0);
  const jornada = activa.data ?? null;
  const corriendo = jornada !== null && jornada.horaSalida === undefined && jornada.enPausa !== true;
  useEffect(() => {
    if (!corriendo) return;
    const id = setInterval(() => setTic((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, [corriendo]);

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <div>
          <h1>{T.fichar.titulo}</h1>
          <p className="nx-sutil">{sesion !== null ? T.fichar.saludo(sesion.nombre) : fechaLarga()}</p>
        </div>
      </header>

      {hoy.data?.laborable === false && <Aviso>{T.fichar.noLaborable(hoy.data.motivo ?? '')}</Aviso>}

      <EstadoDeConsulta consulta={activa}>
        {(j) => {
          const segundos = segundosTrabajados(j?.horaEntrada, j?.horaSalida, j?.segundosPausaAcumulados ?? 0);
          const estado = j === null ? 'parado' : j.enPausa === true ? 'en-pausa' : 'trabajando';
          const texto = { parado: T.fichar.parado, 'en-pausa': T.fichar.enPausa, trabajando: T.fichar.trabajando }[
            estado
          ];
          // `useMutacion` no da la escritura por terminada hasta que ha vuelto
          // a pedir la jornada, así que un segundo clic no puede salir con el
          // estado de antes (fichar la salida dos veces, por ejemplo).
          const ocupado = fichar.isPending;

          return (
            <>
              <section className={`nx-tarjeta nx-jornada nx-jornada--${estado}`}>
                <p className="nx-jornada__estado">{texto}</p>
                <p className="nx-jornada__cronometro">{j === null ? '—' : duracion(segundos)}</p>
                <p className="nx-sutil">{j === null ? T.fichar.sinJornada : T.fichar.desde(hora(j.horaEntrada))}</p>
                {j !== null && (j.segundosPausaAcumulados ?? 0) > 0 && (
                  <p className="nx-sutil">{T.fichar.pausaAcumulada(duracion(j.segundosPausaAcumulados ?? 0))}</p>
                )}
              </section>

              {fichar.error !== null && <Aviso>{fichar.error.message}</Aviso>}

              <div className="nx-acciones">
                {j === null ? (
                  <Boton ocupado={ocupado} onClick={() => fichar.mutate('INICIO')}>
                    {T.fichar.entrar}
                  </Boton>
                ) : (
                  <>
                    <Boton
                      variante="secundario"
                      ocupado={ocupado}
                      onClick={() => fichar.mutate(j.enPausa === true ? 'PAUSA_FIN' : 'PAUSA_INICIO')}
                    >
                      {j.enPausa === true ? T.fichar.reanudar : T.fichar.pausar}
                    </Boton>
                    <Boton ocupado={ocupado} onClick={() => fichar.mutate('FIN')}>
                      {T.fichar.salir}
                    </Boton>
                  </>
                )}
              </div>
            </>
          );
        }}
      </EstadoDeConsulta>
    </div>
  );
}
