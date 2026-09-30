/**
 * La pantalla de la tablet del kiosco (ADR 033), en `/kiosco`.
 *
 * Va fuera del marco y de la sesión: la tablet no es de nadie. Lo único que
 * guarda es **su** token, el del dispositivo, en `localStorage`; sin él, lo
 * primero es emparejarla. Con él, espera a que alguien pase su tarjeta o busque
 * su nombre, le deja fichar y vuelve a esperar sola.
 *
 * Tres cosas a propósito:
 *
 * - **No sondea al servidor mientras espera.** Solo lo llama al identificar,
 *   al fichar y cada pocas horas para refrescar la lista de nombres: una
 *   tablet encendida todo el día no puede tener la base de datos despierta
 *   (la cuota de Neon, septiembre de 2026).
 * - **Vuelve sola a la espera**: a los pocos segundos de fichar, y si alguien
 *   se va a medias. Que el siguiente no fiche en nombre del anterior.
 * - **Las horas, en la zona de la empresa del kiosco** (ADR 032), que llega en
 *   `/kiosco/yo`.
 */

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { clienteDeKiosco } from '../../api/cliente';
import { ErrorDeApi, pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { kiosco as K } from '../../i18n/es/kiosco';
import { fijarZona, hora } from '../../util/fechas';
import { empezarALeer, type Lector } from './lectorQr';

const T = K.tablet;

type Info = components['schemas']['KioskInfo'];
type Persona = components['schemas']['KioskPerson'];
type Identidad = components['schemas']['KioskIdentity'];
type Tipo = components['schemas']['KioskClockRequest']['tipo'];

/** Solo `qr`, o `usuarioId` y `pin`. */
type Credencial = { qr: string } | { usuarioId: number; pin: string };

const CLAVE_TOKEN = 'nx-kiosco-token';
const VUELTA_TRAS_FICHAR_MS = 5_000;
const VUELTA_SI_SE_VA_MS = 20_000;
const REFRESCO_DE_LA_LISTA_MS = 3 * 60 * 60 * 1000;
const PREGUNTA_EMPAREJAMIENTO_MS = 4_000;

function leerToken(): string | null {
  try {
    return localStorage.getItem(CLAVE_TOKEN);
  } catch {
    return null;
  }
}

function guardarToken(token: string | null): void {
  try {
    if (token === null) localStorage.removeItem(CLAVE_TOKEN);
    else localStorage.setItem(CLAVE_TOKEN, token);
  } catch {
    // Sin almacenamiento (modo privado): la tablet se emparejaría en cada recarga.
  }
}

export function Kiosco() {
  const [token, setToken] = useState<string | null>(leerToken);
  const tokenActual = useRef(token);
  tokenActual.current = token;
  const api = useMemo(() => clienteDeKiosco(() => tokenActual.current), []);

  const tener = useCallback((nuevo: string | null) => {
    guardarToken(nuevo);
    setToken(nuevo);
  }, []);

  return (
    <main className="nx-kiosco">
      {token === null ? (
        <Emparejar api={api} alTener={tener} />
      ) : (
        <Puesto api={api} alPerderElToken={() => tener(null)} />
      )}
    </main>
  );
}

type Api = ReturnType<typeof clienteDeKiosco>;

/* ------------------------------------------------------------------ */
/* Emparejar                                                           */
/* ------------------------------------------------------------------ */

function Emparejar({ api, alTener }: { api: Api; alTener: (token: string) => void }) {
  const [codigo, setCodigo] = useState<{ codigo: string; secreto: string; caducaEn: string } | null>(null);
  const [estado, setEstado] = useState<'pidiendo' | 'esperando' | 'caducado' | 'error'>('pidiendo');
  const [error, setError] = useState<string | null>(null);

  const pedirCodigo = useCallback(async () => {
    setEstado('pidiendo');
    setError(null);
    try {
      const nuevo = await pedir(api.POST('/kiosco/emparejar', {}));
      setCodigo({ codigo: nuevo.codigo ?? '', secreto: nuevo.secreto ?? '', caducaEn: nuevo.caducaEn ?? '' });
      setEstado('esperando');
    } catch (fallo) {
      setError(fallo instanceof Error ? fallo.message : String(fallo));
      setEstado('error');
    }
  }, [api]);

  useEffect(() => {
    void pedirCodigo();
  }, [pedirCodigo]);

  // Mientras espera a que el ADMIN lo confirme, y solo entonces, pregunta cada
  // pocos segundos: son diez minutos como mucho.
  useEffect(() => {
    if (estado !== 'esperando' || codigo === null) return;
    const temporizador = setInterval(async () => {
      try {
        const respuesta = await pedir(
          api.POST('/kiosco/emparejar/estado', { body: { secreto: codigo.secreto } }),
        );
        if (respuesta.estado === 'LISTO' && respuesta.token !== undefined) alTener(respuesta.token);
        else if (respuesta.estado === 'CADUCADO') setEstado('caducado');
        else if (respuesta.estado === 'ENTREGADO') {
          setError(T.yaEntregado);
          setEstado('error');
        }
      } catch {
        // Un fallo de red suelto no para la espera: se vuelve a preguntar.
      }
    }, PREGUNTA_EMPAREJAMIENTO_MS);
    return () => clearInterval(temporizador);
  }, [api, codigo, estado, alTener]);

  return (
    <section className="nx-kiosco__panel" aria-labelledby="kiosco-emparejar">
      <h1 id="kiosco-emparejar">{T.emparejarTitulo}</h1>
      {estado === 'pidiendo' && <p>{T.preparando}</p>}
      {estado === 'esperando' && codigo !== null && (
        <>
          <p>{T.emparejarTexto}</p>
          <p className="nx-kiosco__codigo" aria-label={codigo.codigo.split('').join(' ')}>
            {codigo.codigo.slice(0, 4)} {codigo.codigo.slice(4)}
          </p>
          <p className="nx-sutil">{T.caduca(hora(codigo.caducaEn))}</p>
          <p role="status">{T.esperando}</p>
        </>
      )}
      {estado === 'caducado' && <p role="status">{T.caducado}</p>}
      {error !== null && <Aviso>{error}</Aviso>}
      {(estado === 'caducado' || estado === 'error') && (
        <Boton onClick={() => void pedirCodigo()}>{T.otroCodigo}</Boton>
      )}
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* El puesto: esperar, identificarse y fichar                          */
/* ------------------------------------------------------------------ */

type Paso =
  | { paso: 'espera' }
  | { paso: 'lista' }
  | { paso: 'pin'; persona: Persona }
  | { paso: 'identificado'; identidad: Identidad; credencial: Credencial }
  | { paso: 'proyecto'; identidad: Identidad; credencial: Credencial }
  | { paso: 'hecho'; texto: string };

function Puesto({ api, alPerderElToken }: { api: Api; alPerderElToken: () => void }) {
  const [info, setInfo] = useState<Info | null>(null);
  const [personas, setPersonas] = useState<Persona[]>([]);
  const [paso, setPaso] = useState<Paso>({ paso: 'espera' });
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const siNoVale = useCallback(
    (fallo: unknown) => {
      // Revocado, o de una base que ya no existe: a emparejar otra vez.
      if (fallo instanceof ErrorDeApi && fallo.status === 401) alPerderElToken();
    },
    [alPerderElToken],
  );

  const cargarLista = useCallback(async () => {
    try {
      setPersonas(await pedir(api.GET('/kiosco/plantilla', {})));
    } catch (fallo) {
      siNoVale(fallo);
    }
  }, [api, siNoVale]);

  useEffect(() => {
    void (async () => {
      try {
        const yo = await pedir(api.GET('/kiosco/yo', {}));
        fijarZona(yo.zonaHoraria);
        setInfo(yo);
      } catch (fallo) {
        siNoVale(fallo);
        setError(fallo instanceof Error ? fallo.message : String(fallo));
      }
    })();
    void cargarLista();
    const temporizador = setInterval(() => void cargarLista(), REFRESCO_DE_LA_LISTA_MS);
    return () => clearInterval(temporizador);
  }, [api, cargarLista, siNoVale]);

  const aEspera = useCallback(() => {
    setError(null);
    setPaso({ paso: 'espera' });
  }, []);

  // Si alguien se va a medias, o tras fichar, vuelve sola a la espera.
  const [toque, setToque] = useState(0);
  useEffect(() => {
    if (paso.paso === 'espera') return;
    const temporizador = setTimeout(aEspera, paso.paso === 'hecho' ? VUELTA_TRAS_FICHAR_MS : VUELTA_SI_SE_VA_MS);
    return () => clearTimeout(temporizador);
  }, [paso, toque, aEspera]);

  async function identificar(credencial: Credencial) {
    setOcupado(true);
    setError(null);
    try {
      const identidad = await pedir(api.POST('/kiosco/identificar', { body: credencial }));
      setPaso({ paso: 'identificado', identidad, credencial });
    } catch (fallo) {
      siNoVale(fallo);
      setError(fallo instanceof Error ? fallo.message : String(fallo));
    } finally {
      setOcupado(false);
    }
  }

  async function fichar(credencial: Credencial, tipo: Tipo, proyectoId?: number) {
    setOcupado(true);
    setError(null);
    try {
      const hecho = await pedir(
        api.POST('/kiosco/fichar', { body: { ...credencial, tipo, ...(proyectoId !== undefined ? { proyectoId } : {}) } }),
      );
      setPaso({ paso: 'hecho', texto: T.hecho[tipo](hecho.nombre ?? '', hora(hecho.instante)) });
    } catch (fallo) {
      siNoVale(fallo);
      setError(fallo instanceof Error ? fallo.message : String(fallo));
    } finally {
      setOcupado(false);
    }
  }

  function alElegirTipo(identidad: Identidad, credencial: Credencial, tipo: Tipo) {
    const proyectos = identidad.proyectos ?? [];
    // Con varios proyectos hoy, se pregunta en cuál, como en la app (ADR 017).
    if (tipo === 'INICIO' && proyectos.length > 1) {
      setPaso({ paso: 'proyecto', identidad, credencial });
      return;
    }
    void fichar(credencial, tipo, tipo === 'INICIO' ? proyectos[0]?.id : undefined);
  }

  return (
    <div className="nx-kiosco__puesto" onPointerDown={() => setToque((n) => n + 1)} onKeyDown={() => setToque((n) => n + 1)}>
      <header className="nx-kiosco__cabecera">
        <span>{info?.nombre ?? T.titulo}</span>
        <Reloj />
      </header>

      {error !== null && <Aviso>{error}</Aviso>}

      {paso.paso === 'espera' && (
        <Espera
          ocupado={ocupado}
          alLeer={(qr) => void identificar({ qr })}
          alBuscar={() => {
            setError(null);
            setPaso({ paso: 'lista' });
          }}
        />
      )}

      {paso.paso === 'lista' && (
        <Lista personas={personas} alElegir={(persona) => setPaso({ paso: 'pin', persona })} alVolver={aEspera} />
      )}

      {paso.paso === 'pin' && (
        <Pin
          persona={paso.persona}
          ocupado={ocupado}
          alEnviar={(pin) => void identificar({ usuarioId: paso.persona.id ?? 0, pin })}
          alVolver={() => setPaso({ paso: 'lista' })}
        />
      )}

      {paso.paso === 'identificado' && (
        <Acciones
          identidad={paso.identidad}
          ocupado={ocupado}
          alElegir={(tipo) => alElegirTipo(paso.identidad, paso.credencial, tipo)}
          alVolver={aEspera}
        />
      )}

      {paso.paso === 'proyecto' && (
        <Panel titulo={T.elegirProyecto}>
          <div className="nx-kiosco__botones">
            {(paso.identidad.proyectos ?? []).map((p) => (
              <Boton key={p.id} ocupado={ocupado} onClick={() => void fichar(paso.credencial, 'INICIO', p.id)}>
                {p.codigo} · {p.nombre}
              </Boton>
            ))}
            <Boton variante="secundario" ocupado={ocupado} onClick={() => void fichar(paso.credencial, 'INICIO')}>
              {T.sinProyecto}
            </Boton>
          </div>
          <Boton variante="texto" onClick={aEspera}>
            {T.volver}
          </Boton>
        </Panel>
      )}

      {paso.paso === 'hecho' && (
        <Panel titulo={paso.texto}>
          <p role="status">{T.hasta}</p>
        </Panel>
      )}
    </div>
  );
}

function Panel({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="nx-kiosco__panel" aria-labelledby="kiosco-paso">
      <h1 id="kiosco-paso">{titulo}</h1>
      {children}
    </section>
  );
}

/** La hora de ahora, en la zona del kiosco, que avanza sola. No llama al servidor. */
function Reloj() {
  const [ahora, setAhora] = useState(() => new Date());
  useEffect(() => {
    const temporizador = setInterval(() => setAhora(new Date()), 15_000);
    return () => clearInterval(temporizador);
  }, []);
  return <time dateTime={ahora.toISOString()}>{hora(ahora.toISOString())}</time>;
}

function Espera({ ocupado, alLeer, alBuscar }: { ocupado: boolean; alLeer: (qr: string) => void; alBuscar: () => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const [sinCamara, setSinCamara] = useState(false);
  const leyendo = useRef(false);
  const alLeerActual = useRef(alLeer);
  alLeerActual.current = alLeer;

  useEffect(() => {
    let lector: Lector | null = null;
    let vigente = true;
    const elemento = video.current;
    if (elemento === null || navigator.mediaDevices?.getUserMedia === undefined) {
      setSinCamara(true);
      return;
    }
    void empezarALeer(elemento, (codigo) => {
      // Una tarjeta delante de la cámara se lee muchas veces por segundo: una basta.
      if (leyendo.current) return;
      leyendo.current = true;
      alLeerActual.current(codigo);
    })
      .then((nuevo) => {
        if (vigente) lector = nuevo;
        else nuevo.parar();
      })
      .catch(() => {
        if (vigente) setSinCamara(true);
      });
    return () => {
      vigente = false;
      lector?.parar();
    };
  }, []);

  // Otra lectura en cuanto termina la anterior (bien o mal).
  useEffect(() => {
    if (!ocupado) leyendo.current = false;
  }, [ocupado]);

  return (
    <section className="nx-kiosco__panel" aria-labelledby="kiosco-espera">
      <h1 id="kiosco-espera">{sinCamara ? T.oBuscaTuNombre : T.pasaTuTarjeta}</h1>
      {sinCamara ? (
        <p>{T.sinCamara}</p>
      ) : (
        <video ref={video} className="nx-kiosco__camara" aria-hidden="true" />
      )}
      <Boton variante={sinCamara ? 'primario' : 'secundario'} className="nx-kiosco__grande" onClick={alBuscar}>
        {T.oBuscaTuNombre}
      </Boton>
    </section>
  );
}

function Lista({ personas, alElegir, alVolver }: { personas: Persona[]; alElegir: (p: Persona) => void; alVolver: () => void }) {
  const [texto, setTexto] = useState('');
  const buscado = sinTildes(texto.trim());
  const encontradas = personas.filter((p) =>
    sinTildes(`${p.nombre ?? ''} ${p.apellidos ?? ''}`).includes(buscado),
  );
  return (
    <Panel titulo={T.oBuscaTuNombre}>
      <Campo id="kiosco-buscar" etiqueta={T.buscar} value={texto} autoFocus onChange={(e) => setTexto(e.target.value)} />
      {personas.length === 0 && <p>{T.nadieConPin}</p>}
      {personas.length > 0 && encontradas.length === 0 && <p>{T.nadie}</p>}
      <ul className="nx-kiosco__lista">
        {encontradas.slice(0, 24).map((p) => (
          <li key={p.id}>
            <Boton variante="secundario" className="nx-kiosco__grande" onClick={() => alElegir(p)}>
              {p.nombre} {p.apellidos ?? ''}
            </Boton>
          </li>
        ))}
      </ul>
      <Boton variante="texto" onClick={alVolver}>
        {T.volver}
      </Boton>
    </Panel>
  );
}

function sinTildes(texto: string): string {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

function Pin({ persona, ocupado, alEnviar, alVolver }: {
  persona: Persona;
  ocupado: boolean;
  alEnviar: (pin: string) => void;
  alVolver: () => void;
}) {
  const [pin, setPin] = useState('');
  const teclas = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];
  return (
    <Panel titulo={T.pinDe(persona.nombre ?? '')}>
      <p className="nx-kiosco__pin" aria-live="polite" aria-label={`${pin.length} cifras`}>
        {'•'.repeat(pin.length) || ' '}
      </p>
      <div className="nx-kiosco__teclado">
        {teclas.map((t) => (
          <Boton key={t} variante="secundario" disabled={pin.length >= 6} onClick={() => setPin(pin + t)}>
            {t}
          </Boton>
        ))}
        <Boton variante="texto" onClick={() => setPin(pin.slice(0, -1))}>
          {T.borrar}
        </Boton>
        <Boton variante="secundario" disabled={pin.length >= 6} onClick={() => setPin(pin + '0')}>
          0
        </Boton>
        <Boton ocupado={ocupado} disabled={pin.length < 4} onClick={() => alEnviar(pin)}>
          {T.entrar}
        </Boton>
      </div>
      <Boton variante="texto" onClick={alVolver}>
        {T.volver}
      </Boton>
    </Panel>
  );
}

function Acciones({ identidad, ocupado, alElegir, alVolver }: {
  identidad: Identidad;
  ocupado: boolean;
  alElegir: (tipo: Tipo) => void;
  alVolver: () => void;
}) {
  const estado = identidad.estado;
  return (
    <Panel titulo={T.hola(identidad.nombre ?? '')}>
      <p>{estado === 'TRABAJANDO' ? T.trabajando : estado === 'EN_PAUSA' ? T.enPausa : T.sinJornada}</p>
      <div className="nx-kiosco__botones">
        {estado === 'SIN_JORNADA' && (
          <Boton className="nx-kiosco__grande" ocupado={ocupado} onClick={() => alElegir('INICIO')}>
            {T.ficharEntrada}
          </Boton>
        )}
        {estado === 'TRABAJANDO' && (
          <>
            <Boton className="nx-kiosco__grande" ocupado={ocupado} onClick={() => alElegir('FIN')}>
              {T.ficharSalida}
            </Boton>
            <Boton variante="secundario" className="nx-kiosco__grande" ocupado={ocupado} onClick={() => alElegir('PAUSA_INICIO')}>
              {T.empezarPausa}
            </Boton>
          </>
        )}
        {estado === 'EN_PAUSA' && (
          <Boton className="nx-kiosco__grande" ocupado={ocupado} onClick={() => alElegir('PAUSA_FIN')}>
            {T.volverDePausa}
          </Boton>
        )}
      </div>
      <Boton variante="texto" onClick={alVolver}>
        {T.volver}
      </Boton>
    </Panel>
  );
}
