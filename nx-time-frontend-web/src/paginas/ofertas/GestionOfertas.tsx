/**
 * Gestionar las ofertas internas: crearlas, publicarlas, cerrarlas y valorar
 * sus candidaturas. Es la `GestionOfertasScreen` de la app.
 *
 * **Una oferta nace en BORRADOR**: publicar avisa a toda la plantilla, así que
 * no puede ser el efecto colateral de guardar un formulario. El aviso sale una
 * sola vez, la primera que se publica; retirarla al borrador y volver a
 * publicarla no vuelve a avisar. **Una cerrada no se reabre**, por eso se
 * confirma.
 *
 * Las candidaturas de una oferta se ven en la misma página (`?oferta=`). El CV
 * que se descarga es el que se **congeló al presentarse** (`cvAdjuntoId`), no
 * el que la persona tenga hoy. **Descartar pide comentario**: lo lee un
 * compañero, sobre sí mismo, en la empresa en la que sigue trabajando mañana.
 * Nadie valora su propia candidatura (`puedoValorar` lo dice el servidor).
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';

import { cliente } from '../../api/cliente';
import { ErrorDeApi, pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { useSesion } from '../../api/useSesion';
import { AreaDeTexto, Aviso, Boton, Campo, Insignia, Selector, type Tono } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { T } from '../../i18n/es';
import { ofertas } from '../../i18n/es/ofertas';
import { descargar } from '../../util/descargar';
import { diaEnEmpresa, fechaCorta } from '../../util/fechas';
import { plazoDe } from './Ofertas';

const O = ofertas;
const G = ofertas.gestion;

type Oferta = components['schemas']['JobPostingResponse'];
type Candidatura = components['schemas']['JobApplicationResponse'];
type EstadoCandidatura = components['schemas']['UpdateApplicationStatusRequest']['estado'];

const CLAVE = ['ofertas'] as const;
const TONO_OFERTA: Record<string, Tono> = { BORRADOR: 'neutro', ABIERTA: 'exito', CERRADA: 'aviso' };
const TONO_CANDIDATURA: Record<string, Tono> = { RECIBIDA: 'neutro', EN_PROCESO: 'info', DESCARTADA: 'error', SELECCIONADA: 'exito' };

function FormularioDeOferta({ oferta, alTerminar }: { oferta: Oferta | null; alTerminar: () => void }) {
  const { puede } = useSesion();
  const [titulo, setTitulo] = useState(oferta?.titulo ?? '');
  const [puesto, setPuesto] = useState(oferta?.puesto ?? '');
  const [descripcion, setDescripcion] = useState(oferta?.descripcion ?? '');
  // null = sin tocar: se toma el que ya tenía la oferta.
  const [elegido, setDepartamento] = useState<string | null>(null);
  const [cierre, setCierre] = useState(oferta?.fechaCierre ?? '');
  const [falta, setFalta] = useState(false);

  const departamentos = useQuery({
    queryKey: ['plantilla', 'departamentos'],
    queryFn: () => pedir(cliente.GET('/api/v1/departamentos', {})),
    enabled: puede('empleado:leer'),
  });
  // La oferta trae el NOMBRE del departamento, no su id: se busca por nombre.
  // Sin esto, editar la oferta y guardar le quitaría el departamento (es un PUT).
  const departamento =
    elegido ?? String((departamentos.data ?? []).find((d) => d.nombre === oferta?.departamento)?.id ?? '');

  const guardar = useMutacion(
    (cuerpo: components['schemas']['JobPostingRequest']) =>
      oferta?.id !== undefined
        ? pedir(cliente.PUT('/api/v1/ofertas/{id}', { params: { path: { id: oferta.id } }, body: cuerpo }))
        : pedir(cliente.POST('/api/v1/ofertas', { body: cuerpo })),
    { invalida: [CLAVE], exito: oferta === null ? G.creada : G.guardada, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (titulo.trim() === '' || descripcion.trim() === '') return setFalta(true);
    setFalta(false);
    guardar.mutate({
      titulo: titulo.trim(),
      descripcion: descripcion.trim(),
      ...(puesto.trim() !== '' ? { puesto: puesto.trim() } : {}),
      ...(departamento !== '' ? { departamentoId: Number(departamento) } : {}),
      ...(cierre !== '' ? { fechaCierre: cierre } : {}),
    });
  }

  const mensaje = falta ? G.faltan : (guardar.error?.message ?? null);
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      {oferta === null && <p className="nx-sutil">{G.naceEnBorrador}</p>}
      <Campo id="oferta-titulo" etiqueta={G.tituloCampo} maxLength={150} value={titulo} onChange={(e) => setTitulo(e.target.value)} />
      <div className="nx-fila-campos">
        <Campo id="oferta-puesto" etiqueta={G.puesto} maxLength={150} value={puesto} onChange={(e) => setPuesto(e.target.value)} />
        {(departamentos.data ?? []).length > 0 && (
          <Selector
            id="oferta-departamento"
            etiqueta={G.departamento}
            value={departamento}
            onChange={(e) => setDepartamento(e.target.value)}
            opciones={[{ valor: '', texto: G.sinDepartamento }, ...(departamentos.data ?? []).map((d) => ({ valor: String(d.id), texto: d.nombre ?? '' }))]}
          />
        )}
      </div>
      <AreaDeTexto id="oferta-descripcion" etiqueta={G.descripcion} rows={6} maxLength={5000} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      <Campo id="oferta-cierre" etiqueta={G.fechaCierre} type="date" value={cierre} onChange={(e) => setCierre(e.target.value)} />
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={guardar.isPending || departamentos.isLoading}>
          {oferta === null ? G.guardarBorrador : G.guardar}
        </Boton>
      </div>
    </form>
  );
}

function Candidaturas({ oferta }: { oferta: Oferta }) {
  const id = oferta.id ?? 0;
  const [descartando, setDescartando] = useState<Candidatura | null>(null);
  const [errorCv, setErrorCv] = useState<string | null>(null);
  const lista = useQuery({
    queryKey: [...CLAVE, 'candidaturas', id],
    queryFn: () => pedir(cliente.GET('/api/v1/ofertas/{id}/candidaturas', { params: { path: { id } } })),
  });
  const valorar = useMutacion(
    ({ candidatura, estado, comentario }: { candidatura: number; estado: EstadoCandidatura; comentario?: string }) =>
      pedir(
        cliente.PATCH('/api/v1/candidaturas/{id}/estado', {
          params: { path: { id: candidatura } },
          body: { estado, ...(comentario !== undefined ? { comentario } : {}) },
        }),
      ),
    { invalida: [CLAVE], exito: G.valorada, alTerminar: () => setDescartando(null) },
  );

  async function bajarCv(c: Candidatura) {
    setErrorCv(null);
    try {
      await descargar(
        cliente.GET('/api/v1/perfil/adjuntos/{id}', { params: { path: { id: c.cvAdjuntoId ?? 0 } }, parseAs: 'blob' }),
        c.cvNombre ?? 'cv.pdf',
      );
    } catch (fallo) {
      setErrorCv(fallo instanceof ErrorDeApi ? fallo.message : String(fallo));
    }
  }

  const resuelta = (c: Candidatura) => c.estado === 'DESCARTADA' || c.estado === 'SELECCIONADA';

  return (
    <section className="nx-tarjeta" aria-labelledby="oferta-candidaturas">
      <h2 id="oferta-candidaturas">{G.candidaturas}</h2>
      {errorCv !== null && <Aviso>{errorCv}</Aviso>}
      {valorar.error !== null && descartando === null && <Aviso>{valorar.error.message}</Aviso>}
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={3} />}>
        {(cs) =>
          cs.length === 0 ? (
            <p className="nx-sutil">{G.sinCandidaturas}</p>
          ) : (
            <ul className="nx-lista-incidencias" aria-label={G.candidaturas}>
              {cs.map((c) => (
                <li key={c.id} className="nx-incidencia">
                  <div className="nx-incidencia__cabecera">
                    <strong>{c.candidato}</strong>
                    {c.estado && <Insignia tono={TONO_CANDIDATURA[c.estado] ?? 'neutro'}>{O.estadosCandidatura[c.estado] ?? c.estado}</Insignia>}
                  </div>
                  {c.creadoEn && <span className="nx-sutil">{O.presentadaEl(fechaCorta(diaEnEmpresa(c.creadoEn)))}</span>}
                  {c.carta && <p className="nx-texto-largo">{c.carta}</p>}
                  {c.comentario && <span>{O.comentario(c.comentario)}</span>}
                  {c.resueltaPor && <span className="nx-sutil">{O.resueltaPor(c.resueltaPor)}</span>}
                  <div className="nx-acciones-fila">
                    {c.cvAdjuntoId !== undefined && (
                      <Boton variante="texto" onClick={() => void bajarCv(c)}>
                        {G.verCv}
                      </Boton>
                    )}
                    {c.puedoValorar === true && !resuelta(c) && (
                      <>
                        {c.estado !== 'EN_PROCESO' && (
                          <Boton variante="texto" onClick={() => c.id !== undefined && valorar.mutate({ candidatura: c.id, estado: 'EN_PROCESO' })}>
                            {G.aProceso}
                          </Boton>
                        )}
                        <Boton variante="texto" onClick={() => c.id !== undefined && valorar.mutate({ candidatura: c.id, estado: 'SELECCIONADA' })}>
                          {G.seleccionar}
                        </Boton>
                        <Boton variante="texto" onClick={() => setDescartando(c)}>
                          {G.descartar}
                        </Boton>
                      </>
                    )}
                  </div>
                  {c.puedoValorar === false && !resuelta(c) && <span className="nx-sutil">{G.propia}</span>}
                </li>
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <DialogoDeTexto
        abierto={descartando !== null}
        titulo={G.descartarTitulo(descartando?.candidato ?? '')}
        ayuda={G.comentarioAyuda}
        etiqueta={G.comentario}
        vacio={G.comentarioVacio}
        boton={G.descartar}
        peligro
        maxLength={1000}
        ocupado={valorar.isPending}
        error={valorar.error?.message ?? null}
        alEnviar={(comentario) => descartando?.id !== undefined && valorar.mutate({ candidatura: descartando.id, estado: 'DESCARTADA', comentario })}
        alCerrar={() => {
          valorar.reset();
          setDescartando(null);
        }}
      />
    </section>
  );
}

export function GestionOfertas() {
  const [busqueda, setBusqueda] = useSearchParams();
  const [editando, setEditando] = useState<Oferta | 'nueva' | null>(null);
  const [cerrando, setCerrando] = useState<Oferta | null>(null);
  const lista = useQuery({ queryKey: [...CLAVE, 'gestion'], queryFn: () => pedir(cliente.GET('/api/v1/ofertas/gestion', {})) });

  const estado = useMutacion(
    ({ id, nuevo }: { id: number; nuevo: Oferta['estado'] & string }) =>
      pedir(cliente.PATCH('/api/v1/ofertas/{id}/estado', { params: { path: { id } }, body: { estado: nuevo } })),
    {
      invalida: [CLAVE],
      exito: (_r, v) => (v.nuevo === 'ABIERTA' ? G.publicada : v.nuevo === 'CERRADA' ? G.cerrada : G.retirada),
      alTerminar: () => setCerrando(null),
    },
  );

  const elegida = Number(busqueda.get('oferta'));
  const abierta = Number.isInteger(elegida) && elegida > 0 ? (lista.data ?? []).find((o) => o.id === elegida) : undefined;

  const columnas: Columna<Oferta>[] = [
    { clave: 'oferta', cabecera: G.oferta, celda: (o) => <strong>{o.titulo}</strong> },
    { clave: 'estado', cabecera: G.estado, celda: (o) => o.estado && <Insignia tono={TONO_OFERTA[o.estado] ?? 'neutro'}>{O.estados[o.estado] ?? o.estado}</Insignia> },
    { clave: 'plazo', cabecera: G.plazo, celda: (o) => plazoDe(o) },
    { clave: 'candidaturas', cabecera: G.candidaturas, celda: (o) => String(o.candidaturas ?? 0), numerica: true },
    {
      clave: 'acciones',
      cabecera: G.acciones,
      celda: (o) => (
        <div className="nx-acciones-fila" role="group" aria-label={G.accionesDe(o.titulo ?? '')}>
          {o.estado !== 'CERRADA' && (
            <Boton variante="texto" onClick={() => setEditando(o)}>
              {G.editar}
            </Boton>
          )}
          {o.estado === 'BORRADOR' && (
            <Boton variante="texto" title={G.publicarAyuda} onClick={() => o.id !== undefined && estado.mutate({ id: o.id, nuevo: 'ABIERTA' })}>
              {G.publicar}
            </Boton>
          )}
          {o.estado === 'ABIERTA' && (
            <>
              <Boton variante="texto" onClick={() => o.id !== undefined && estado.mutate({ id: o.id, nuevo: 'BORRADOR' })}>
                {G.retirar}
              </Boton>
              <Boton variante="texto" onClick={() => setCerrando(o)}>
                {G.cerrar}
              </Boton>
            </>
          )}
          {o.estado !== 'BORRADOR' && (
            <Boton variante="texto" onClick={() => setBusqueda({ oferta: String(o.id) })}>
              {G.verCandidaturas}
            </Boton>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera nx-cabecera--con-acciones">
        <h1>{abierta ? abierta.titulo : G.titulo}</h1>
        {abierta ? (
          <Boton variante="secundario" onClick={() => setBusqueda({})}>
            ‹ {G.volver}
          </Boton>
        ) : (
          <Boton onClick={() => setEditando('nueva')}>{G.nueva}</Boton>
        )}
      </header>

      {estado.error !== null && cerrando === null && <Aviso>{estado.error.message}</Aviso>}

      {abierta ? (
        <Candidaturas oferta={abierta} />
      ) : (
        <section className="nx-tarjeta">
          <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
            {(os) =>
              os.length === 0 ? <Vacio titulo={G.vacio} /> : <Tabla titulo={G.tablaTitulo} columnas={columnas} filas={os} claveDeFila={(o) => o.id ?? 0} />
            }
          </EstadoDeConsulta>
        </section>
      )}

      <Dialogo
        abierto={editando !== null}
        titulo={editando === 'nueva' || editando === null ? G.nueva : G.editarTitulo(editando.titulo ?? '')}
        alCerrar={() => setEditando(null)}
        acciones={null}
      >
        {editando !== null && <FormularioDeOferta oferta={editando === 'nueva' ? null : editando} alTerminar={() => setEditando(null)} />}
      </Dialogo>

      <Dialogo
        abierto={cerrando !== null}
        titulo={G.cerrarTitulo(cerrando?.titulo ?? '')}
        alCerrar={() => {
          estado.reset();
          setCerrando(null);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setCerrando(null)}>
              {T.app.cancelar}
            </Boton>
            <Boton variante="peligro" ocupado={estado.isPending} onClick={() => cerrando?.id !== undefined && estado.mutate({ id: cerrando.id, nuevo: 'CERRADA' })}>
              {G.cerrar}
            </Boton>
          </>
        }
      >
        <p>{G.cerrarTexto}</p>
        {estado.error !== null && <Aviso>{estado.error.message}</Aviso>}
      </Dialogo>
    </div>
  );
}
