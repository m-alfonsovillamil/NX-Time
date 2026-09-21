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
 * para demostrar que funciona.
 */

import { useCallback, useEffect, useState } from 'react';

import { cliente } from '../api/cliente';
import { cerrarSesion, sesionActual } from '../api/sesion';
import { Aviso, Boton, Cargando } from '../componentes/Basicos';
import { T } from '../i18n/es';
import { mensajeDeError, mensajeDeRed } from '../util/errores';
import { duracion, fechaLarga, hora, segundosTrabajados } from '../util/fechas';

type Jornada = {
  id?: number;
  horaEntrada?: string;
  horaSalida?: string;
  enPausa?: boolean;
  minutosPausaAcumulados?: number;
  segundosPausaAcumulados?: number;
};

type Tipo = 'INICIO' | 'FIN' | 'PAUSA_INICIO' | 'PAUSA_FIN';

export function Fichar() {
  const sesion = sesionActual();
  const [jornada, setJornada] = useState<Jornada | null>(null);
  const [hoyLaborable, setHoyLaborable] = useState<{ laborable: boolean; motivo?: string } | null>(null);
  const [cargando, setCargando] = useState(true);
  const [fichando, setFichando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Redibuja el cronómetro. Solo mientras corre: en pausa no avanza. */
  const [, setTic] = useState(0);
  const corriendo = jornada !== null && jornada.horaSalida === undefined && jornada.enPausa !== true;
  useEffect(() => {
    if (!corriendo) return;
    const id = setInterval(() => setTic((n) => n + 1), 1000);
    return () => clearInterval(id);
  }, [corriendo]);

  const cargar = useCallback(async () => {
    setError(null);
    try {
      const activa = await cliente.GET('/api/v1/fichaje/activo', {});
      // 204 sin cuerpo: no hay jornada abierta. No es un error.
      setJornada(activa.response.status === 204 ? null : (activa.data ?? null));

      const hoy = await cliente.GET('/api/v1/fichaje/hoy', {});
      if (hoy.data) {
        setHoyLaborable({
          laborable: hoy.data.laborable ?? true,
          ...(hoy.data.motivo !== undefined ? { motivo: hoy.data.motivo } : {}),
        });
      }
    } catch (fallo) {
      setError(mensajeDeRed(fallo));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  async function fichar(tipo: Tipo) {
    if (fichando) return;
    setFichando(true);
    setError(null);
    try {
      const { data, error: fallo, response } = await cliente.POST('/api/v1/fichaje', {
        body: { tipo },
      });
      if (!data) {
        setError(mensajeDeError(fallo, response.status));
        return;
      }
      // Al fichar la SALIDA el servidor devuelve la jornada ya cerrada; se
      // vuelve a preguntar en vez de quedársela, para que el estado de la
      // pantalla siga siendo el del servidor y no una copia nuestra.
      if (tipo === 'FIN') {
        setJornada(null);
      } else {
        setJornada(data);
      }
    } catch (fallo) {
      setError(mensajeDeRed(fallo));
    } finally {
      setFichando(false);
    }
  }

  function salir() {
    const refreshToken = sesionActual()?.refreshToken;
    // Se avisa al servidor para que revoque la familia entera, pero no se
    // espera: la sesión local se cierra igual. Si la petición falla, el token
    // caduca solo en 12 horas; dejar a alguien "dentro" porque el logout no
    // llegó sería peor.
    if (refreshToken !== undefined) {
      void cliente.POST('/auth/logout', { body: { refreshToken } }).catch(() => undefined);
    }
    cerrarSesion();
  }

  if (cargando) return <Cargando />;

  const segundos = segundosTrabajados(
    jornada?.horaEntrada,
    jornada?.horaSalida,
    jornada?.segundosPausaAcumulados ?? 0,
  );
  const estado = jornada === null ? T.fichar.parado : jornada.enPausa === true ? T.fichar.enPausa : T.fichar.trabajando;
  const claseEstado = jornada === null ? 'parado' : jornada.enPausa === true ? 'en-pausa' : 'trabajando';

  return (
    <main className="nx-pagina">
      <header className="nx-cabecera">
        <div>
          <h1>{T.fichar.titulo}</h1>
          <p className="nx-sutil">{sesion !== null ? T.fichar.saludo(sesion.nombre) : fechaLarga()}</p>
        </div>
        <Boton variante="texto" onClick={salir}>
          {T.fichar.salir}
        </Boton>
      </header>

      {hoyLaborable !== null && !hoyLaborable.laborable && (
        <Aviso>{T.fichar.noLaborable(hoyLaborable.motivo ?? '')}</Aviso>
      )}

      <section className={`nx-tarjeta nx-jornada nx-jornada--${claseEstado}`}>
        <p className="nx-jornada__estado">{estado}</p>
        <p className="nx-jornada__cronometro">{jornada === null ? '—' : duracion(segundos)}</p>
        <p className="nx-sutil">
          {jornada === null
            ? T.fichar.sinJornada
            : T.fichar.desde(hora(jornada.horaEntrada))}
        </p>
        {jornada !== null && (jornada.segundosPausaAcumulados ?? 0) > 0 && (
          <p className="nx-sutil">{T.fichar.pausaAcumulada(duracion(jornada.segundosPausaAcumulados ?? 0))}</p>
        )}
      </section>

      {error !== null && <Aviso>{error}</Aviso>}

      <div className="nx-acciones">
        {jornada === null ? (
          <Boton ocupado={fichando} onClick={() => void fichar('INICIO')}>
            {T.fichar.entrar}
          </Boton>
        ) : (
          <>
            <Boton
              variante="secundario"
              ocupado={fichando}
              onClick={() => void fichar(jornada.enPausa === true ? 'PAUSA_FIN' : 'PAUSA_INICIO')}
            >
              {jornada.enPausa === true ? T.fichar.reanudar : T.fichar.pausar}
            </Boton>
            <Boton ocupado={fichando} onClick={() => void fichar('FIN')}>
              {T.fichar.salir_}
            </Boton>
          </>
        )}
      </div>
    </main>
  );
}
