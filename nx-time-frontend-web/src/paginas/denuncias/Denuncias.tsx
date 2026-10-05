/**
 * El canal de denuncias de la Ley 2/2023 visto por quien denuncia: presentar,
 * seguir una con su código y las que presenté con mi nombre.
 *
 * Es la `DenunciasScreen` de la app, y lo que tiene que dejar claro por encima
 * de todo es **qué significa marcar «anónima»**: el sistema no guarda quién
 * eres —tampoco después— y a cambio la única forma de volver al expediente es
 * un código que se enseña **una sola vez**. Por eso el texto junto a la casilla
 * cambia al marcarla (lo que importa es qué va a pasar con esto que envío), y
 * el diálogo del código no se cierra con Escape ni con un clic fuera: solo con
 * «Ya lo he guardado». El aviso de debajo del código lo escribe el servidor.
 *
 * **El código no se guarda en ningún sitio que dure**: ni en la URL (que
 * queda en el historial), ni en el almacenamiento del navegador, ni como clave
 * de una consulta. Buscar por código es una petición suelta, y el expediente
 * vive en el estado de esta página mientras está abierto.
 *
 * Las anónimas no salen en «Mis denuncias» y no pueden salir: no hay ningún
 * dato que las relacione con quien las puso.
 */

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent, type ReactNode } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { AreaDeTexto, Aviso, Boton, Campo, Insignia, Selector, type Tono } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { notificar } from '../../componentes/Notificaciones';
import { denuncias } from '../../i18n/es/denuncias';
import { fechaHoraCorta } from '../../util/fechas';

const D = denuncias;

type Categoria = components['schemas']['CreateComplaintRequest']['categoria'];
type Creada = components['schemas']['ComplaintCreatedResponse'];
type Expediente = components['schemas']['ComplaintResponse'];
type Resumen = components['schemas']['ComplaintSummaryResponse'];

const CLAVE_MIAS = ['denuncias', 'mias'] as const;

export const TONO_DENUNCIA: Record<string, Tono> = { RECIBIDA: 'aviso', EN_INVESTIGACION: 'info', RESUELTA: 'exito', ARCHIVADA: 'neutro' };

const CATEGORIAS = Object.entries(D.categorias).map(([valor, texto]) => ({ valor, texto }));

/** Cómo se llegó al expediente abierto: con el código, o por ser mío. */
type Via = { codigo: string } | { id: number };

/**
 * El plazo que corre, en una frase: primero el del acuse (7 días), luego el de
 * la respuesta (3 meses). Nada si no queda ninguno por cumplir.
 */
export function plazoDe(e: { diasHastaAcuse?: number; diasHastaRespuesta?: number }): { texto: string; vencido: boolean } | null {
  const { diasHastaAcuse: acuse, diasHastaRespuesta: respuesta } = e;
  if (acuse !== undefined) return acuse < 0 ? { texto: D.plazoAcuseVencido(-acuse), vencido: true } : { texto: D.plazoAcuse(acuse), vencido: false };
  if (respuesta !== undefined) {
    return respuesta < 0 ? { texto: D.plazoRespuestaVencido(-respuesta), vencido: true } : { texto: D.plazoRespuesta(respuesta), vencido: false };
  }
  return null;
}

export function Plazo({ e }: { e: { diasHastaAcuse?: number; diasHastaRespuesta?: number } }) {
  const plazo = plazoDe(e);
  if (plazo === null) return null;
  return <span className={plazo.vencido ? 'nx-texto-error' : 'nx-sutil'}>{plazo.texto}</span>;
}

export function cerrado(e: Expediente): boolean {
  return e.estado === 'RESUELTA' || e.estado === 'ARCHIVADA';
}

/* ------------------------------------------------------------------ */
/* Presentar                                                           */
/* ------------------------------------------------------------------ */

function FormularioDeDenuncia({ alCrear }: { alCrear: (creada: Creada) => void }) {
  const [categoria, setCategoria] = useState<Categoria>('ACOSO');
  const [descripcion, setDescripcion] = useState('');
  const [anonima, setAnonima] = useState(false);
  const [falta, setFalta] = useState(false);

  const presentar = useMutacion(
    () => pedir(cliente.POST('/api/v1/denuncias', { body: { categoria, descripcion: descripcion.trim(), anonima } })),
    {
      invalida: [CLAVE_MIAS],
      alTerminar: (creada) => {
        setDescripcion('');
        setAnonima(false);
        alCrear(creada);
      },
    },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (descripcion.trim() === '') return setFalta(true);
    setFalta(false);
    presentar.mutate();
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate aria-labelledby="denuncia-presentar">
      <h2 id="denuncia-presentar">{D.presentar}</h2>
      <Selector
        id="denuncia-categoria"
        etiqueta={D.categoria}
        value={categoria}
        onChange={(e) => setCategoria(e.target.value as Categoria)}
        opciones={CATEGORIAS}
      />
      <AreaDeTexto
        id="denuncia-descripcion"
        etiqueta={D.descripcion}
        ayuda={D.descripcionAyuda}
        error={falta ? D.sinDescripcion : undefined}
        rows={6}
        maxLength={5000}
        value={descripcion}
        onChange={(e) => setDescripcion(e.target.value)}
      />
      <div>
        <label className="nx-casilla">
          <input type="checkbox" checked={anonima} onChange={(e) => setAnonima(e.target.checked)} aria-describedby="denuncia-anonima-que" />
          {D.anonima}
        </label>
        {/* Cambia con la casilla: lo que hay que entender no es qué es el
            anonimato, sino qué va a pasar con lo que estoy a punto de enviar. */}
        <p id="denuncia-anonima-que" className={anonima ? 'nx-anonimato nx-anonimato--si' : 'nx-sutil'} aria-live="polite">
          {anonima ? D.anonimaSi : D.anonimaNo}
        </p>
      </div>
      {presentar.error !== null && <Aviso>{presentar.error.message}</Aviso>}
      <div>
        <Boton type="submit" ocupado={presentar.isPending}>
          {D.enviar}
        </Boton>
      </div>
    </form>
  );
}

/** El código, la única vez que se puede ver. Solo se sale confirmando. */
function DialogoDelCodigo({ creada, alGuardar }: { creada: Creada | null; alGuardar: () => void }) {
  const codigo = creada?.codigoSeguimiento ?? '';

  async function copiar() {
    try {
      await navigator.clipboard.writeText(codigo);
      notificar(D.copiado);
    } catch {
      // Sin permiso para el portapapeles: el código sigue en pantalla, y se
      // puede seleccionar a mano.
    }
  }

  return (
    <Dialogo
      abierto={creada !== null}
      titulo={D.codigoTitulo}
      alCerrar={alGuardar}
      obligatorio
      acciones={<Boton onClick={alGuardar}>{D.codigoGuardado}</Boton>}
    >
      <p className="nx-codigo-seguimiento">
        <code>{codigo}</code>
        <Boton variante="texto" onClick={() => void copiar()}>
          {D.copiar}
        </Boton>
      </p>
      {/* El aviso lo escribe el servidor: si dependiera de que cada cliente se
          acuerde de enseñarlo, el primero que lo olvide deja a alguien fuera
          de su expediente. El de la web solo cubre que no llegara. */}
      <p>
        <strong>{creada?.avisoImportante ?? D.codigoUnaVez}</strong>
      </p>
    </Dialogo>
  );
}

/* ------------------------------------------------------------------ */
/* El expediente                                                       */
/* ------------------------------------------------------------------ */

function pedirExpediente(via: Via) {
  return 'codigo' in via
    ? // El código, en el cuerpo y no en la URL: es la credencial de una denuncia
      // anónima, y una URL acaba en logs e historiales (ADR 034).
      pedir(cliente.POST('/api/v1/denuncias/seguimiento', { body: { codigo: via.codigo } }))
    : pedir(cliente.GET('/api/v1/denuncias/mias/{id}', { params: { path: { id: via.id } } }));
}

function FormularioDeMensaje({ via, alResponder }: { via: Via; alResponder: (e: Expediente) => void }) {
  const [texto, setTexto] = useState('');
  const [falta, setFalta] = useState(false);

  const responder = useMutacion(
    (t: string) =>
      'codigo' in via
        ? pedir(
            cliente.POST('/api/v1/denuncias/seguimiento/mensajes', { body: { codigo: via.codigo, texto: t } }),
          )
        : pedir(cliente.POST('/api/v1/denuncias/mias/{id}/mensajes', { params: { path: { id: via.id } }, body: { texto: t } })),
    {
      invalida: [CLAVE_MIAS],
      exito: D.mensajeEnviado,
      alTerminar: (e) => {
        setTexto('');
        alResponder(e);
      },
    },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (texto.trim() === '') return setFalta(true);
    setFalta(false);
    responder.mutate(texto.trim());
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <AreaDeTexto
        id="denuncia-mensaje"
        etiqueta={D.responder}
        error={falta ? D.mensajeVacio : undefined}
        maxLength={5000}
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
      />
      {responder.error !== null && <Aviso>{responder.error.message}</Aviso>}
      <div>
        <Boton type="submit" ocupado={responder.isPending}>
          {D.enviarMensaje}
        </Boton>
      </div>
    </form>
  );
}

/**
 * El expediente, igual para quien denuncia y para quien instruye: estado,
 * plazo, hechos, conclusión y conversación. Lo que cambia es el subtítulo
 * (quién la puso) y lo que va debajo (responder, cambiar de estado).
 */
export function CuerpoDeExpediente({ e, subtitulo, children }: { e: Expediente; subtitulo: string; children?: ReactNode }) {
  return (
    <div className="nx-expediente">
      <div className="nx-incidencia__cabecera">
        {e.estado && <Insignia tono={TONO_DENUNCIA[e.estado] ?? 'neutro'}>{D.estados[e.estado] ?? e.estado}</Insignia>}
        <span className="nx-sutil">{subtitulo}</span>
      </div>
      <Plazo e={e} />
      <p className="nx-texto-largo">{e.descripcion}</p>
      {e.conclusion && (
        <section>
          <h3>{D.conclusion}</h3>
          <p className="nx-texto-largo">{e.conclusion}</p>
        </section>
      )}
      {(e.mensajes ?? []).length > 0 && (
        <section>
          <h3>{D.conversacion}</h3>
          <ol className="nx-conversacion">
            {(e.mensajes ?? []).map((m) => (
              <li key={m.id} className={`nx-mensaje nx-mensaje--${m.autorRol === 'INSTRUCTOR' ? 'otro' : 'mio'}`}>
                <span className="nx-sutil">
                  {D.autores[m.autorRol ?? ''] ?? m.autorRol}
                  {m.creadoEn ? ` · ${fechaHoraCorta(m.creadoEn)}` : ''}
                </span>
                <p className="nx-texto-largo">{m.texto}</p>
              </li>
            ))}
          </ol>
        </section>
      )}
      {children}
    </div>
  );
}

function DialogoDeExpediente({
  abierto,
  via,
  expediente,
  alCambiar,
  alCerrar,
}: {
  abierto: boolean;
  via: Via | null;
  expediente: Expediente | null;
  alCambiar: (e: Expediente) => void;
  alCerrar: () => void;
}) {
  const e = expediente;
  return (
    <Dialogo abierto={abierto} titulo={e?.categoriaEtiqueta ?? D.titulo} alCerrar={alCerrar}>
      {e !== null && via !== null && (
        <CuerpoDeExpediente
          e={e}
          subtitulo={e.creadoEn ? (e.anonima === true ? D.presentadaAnonima(fechaHoraCorta(e.creadoEn)) : D.presentadaConNombre(fechaHoraCorta(e.creadoEn))) : ''}
        >
          {cerrado(e) ? <p className="nx-sutil">{D.cerrado}</p> : <FormularioDeMensaje via={via} alResponder={alCambiar} />}
        </CuerpoDeExpediente>
      )}
    </Dialogo>
  );
}

/* ------------------------------------------------------------------ */
/* Seguir y mis denuncias                                              */
/* ------------------------------------------------------------------ */

function BuscarPorCodigo({ alEncontrar }: { alEncontrar: (via: Via, e: Expediente) => void }) {
  const [codigo, setCodigo] = useState('');
  const [falta, setFalta] = useState(false);

  // Una petición suelta, no una consulta: el código no debe quedarse como
  // clave de caché después de usarlo.
  const buscar = useMutacion((c: string) => pedirExpediente({ codigo: c }), {
    alTerminar: (e, c) => {
      setCodigo('');
      alEncontrar({ codigo: c }, e);
    },
  });

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (codigo.trim() === '') return setFalta(true);
    setFalta(false);
    buscar.mutate(codigo.trim());
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate aria-labelledby="denuncia-seguir">
      <h2 id="denuncia-seguir">{D.seguir}</h2>
      <Campo
        id="denuncia-codigo"
        etiqueta={D.codigo}
        ayuda={D.seguirAyuda}
        error={falta ? D.sinCodigo : undefined}
        autoComplete="off"
        spellCheck={false}
        value={codigo}
        onChange={(e) => setCodigo(e.target.value)}
      />
      {buscar.error !== null && <Aviso>{buscar.error.message}</Aviso>}
      <div>
        <Boton type="submit" variante="secundario" ocupado={buscar.isPending}>
          {D.buscar}
        </Boton>
      </div>
    </form>
  );
}

function FilaDeDenuncia({ r, ocupado, alAbrir }: { r: Resumen; ocupado: boolean; alAbrir: () => void }) {
  return (
    <li className="nx-incidencia">
      <div className="nx-incidencia__cabecera">
        <strong>{r.categoriaEtiqueta}</strong>
        {r.estado && <Insignia tono={TONO_DENUNCIA[r.estado] ?? 'neutro'}>{D.estados[r.estado] ?? r.estado}</Insignia>}
      </div>
      {r.creadoEn && <span className="nx-sutil">{D.presentadaConNombre(fechaHoraCorta(r.creadoEn))}</span>}
      <Plazo e={r} />
      <span className="nx-sutil">{D.mensajesCuenta(r.mensajes ?? 0)}</span>
      <div className="nx-acciones-fila">
        <Boton variante="texto" ocupado={ocupado} onClick={alAbrir}>
          {D.ver}
        </Boton>
      </div>
    </li>
  );
}

function MisDenuncias({ alAbrir }: { alAbrir: (via: Via, e: Expediente) => void }) {
  const lista = useQuery({ queryKey: CLAVE_MIAS, queryFn: () => pedir(cliente.GET('/api/v1/denuncias/mias', {})) });
  const abrir = useMutacion((id: number) => pedirExpediente({ id }), { alTerminar: (e, id) => alAbrir({ id }, e) });

  return (
    <section aria-labelledby="denuncias-mias">
      <h2 id="denuncias-mias">{D.mias}</h2>
      {abrir.error !== null && <Aviso>{abrir.error.message}</Aviso>}
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={3} />}>
        {(mias) =>
          mias.length === 0 ? (
            <p className="nx-sutil">{D.miasVacio}</p>
          ) : (
            <ul className="nx-lista-incidencias" aria-label={D.mias}>
              {mias.map((r) => (
                <FilaDeDenuncia
                  key={r.id}
                  r={r}
                  ocupado={abrir.isPending && abrir.variables === r.id}
                  alAbrir={() => r.id !== undefined && abrir.mutate(r.id)}
                />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>
    </section>
  );
}

export function Denuncias() {
  const clienteDeConsultas = useQueryClient();
  const [creada, setCreada] = useState<Creada | null>(null);
  const [abierto, setAbierto] = useState<{ via: Via; expediente: Expediente } | null>(null);

  function alCrear(c: Creada) {
    if (c.anonima === true) setCreada(c);
    else notificar(D.presentadaIdentificada);
  }

  return (
    <div className="nx-pagina nx-pagina--estrecha">
      <header className="nx-cabecera">
        <h1>{D.titulo}</h1>
      </header>

      <section className="nx-tarjeta" aria-labelledby="denuncias-que-es">
        <h2 id="denuncias-que-es">{D.queEsTitulo}</h2>
        <p className="nx-sutil">{D.queEsTexto}</p>
      </section>

      <section className="nx-tarjeta">
        <FormularioDeDenuncia alCrear={alCrear} />
      </section>

      <section className="nx-tarjeta">
        <BuscarPorCodigo alEncontrar={(via, expediente) => setAbierto({ via, expediente })} />
      </section>

      <section className="nx-tarjeta">
        <MisDenuncias alAbrir={(via, expediente) => setAbierto({ via, expediente })} />
      </section>

      <DialogoDelCodigo
        creada={creada}
        alGuardar={() => {
          // El código deja de existir en la web en cuanto se confirma.
          setCreada(null);
          void clienteDeConsultas.invalidateQueries({ queryKey: CLAVE_MIAS });
        }}
      />

      <DialogoDeExpediente
        abierto={abierto !== null}
        via={abierto?.via ?? null}
        expediente={abierto?.expediente ?? null}
        alCambiar={(expediente) => setAbierto((a) => (a === null ? a : { ...a, expediente }))}
        alCerrar={() => setAbierto(null)}
      />
    </div>
  );
}
