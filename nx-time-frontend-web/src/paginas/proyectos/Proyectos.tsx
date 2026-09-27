/**
 * Los proyectos: el reparto de horas del mes, la lista y el detalle de cada
 * uno (quién ha estado, horas del mes, asignar y sacar). Es la
 * `ProyectosScreen` + `DetalleProyectoScreen` de la app.
 *
 * El detalle va en la misma página, con el proyecto en la URL
 * (`?proyecto=`): se puede volver atrás o pasar el enlace.
 *
 * **Un proyecto terminado se cierra, no se borra.** Borrar solo se ofrece si
 * nunca se ha asignado a nadie (el servidor lo impone con 409): si no,
 * desaparecería el reparto de horas ya informado. **Sacar a alguien** tampoco
 * borra su asignación: le pone fecha de fin (hoy), y sus horas pasadas siguen
 * en el proyecto. Se puede estar en varios proyectos a la vez (ADR 017), pero
 * no dos veces en el mismo con fechas que se pisen: el 409 del servidor lo dice.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { AreaDeTexto, Aviso, Boton, Campo, Insignia, Selector } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { T } from '../../i18n/es';
import { proyectos } from '../../i18n/es/proyectos';
import { fechaCorta, hoyEnEspana, mesYAnio, minutos } from '../../util/fechas';
import { ordenar, usePlantilla } from '../plantilla/Plantilla';
import { NavegadorDeMes, useMes } from './mes';

const P = proyectos;
const D = proyectos.detalle;

type Proyecto = components['schemas']['ProjectResponse'];
type Asignacion = components['schemas']['ProjectAssignmentResponse'];

const CLAVE = ['proyectos'] as const;

export function vigenciaDe(p: { fechaInicio?: string; fechaFin?: string }): string {
  return p.fechaFin ? P.rango(fechaCorta(p.fechaInicio), fechaCorta(p.fechaFin)) : P.desde(fechaCorta(p.fechaInicio));
}

/** Una lista de barras horizontales: el que más, entero; el resto, en proporción. */
function Barras({ filas }: { filas: readonly { clave: string | number; texto: string; minutos: number }[] }) {
  const maximo = Math.max(1, ...filas.map((f) => f.minutos));
  return (
    <ul className="nx-barras">
      {filas.map((f) => (
        <li key={f.clave}>
          <span className="nx-barras__texto">{f.texto}</span>
          <span className="nx-barras__carril" aria-hidden="true">
            <span className="nx-barras__relleno" style={{ width: `${(f.minutos / maximo) * 100}%` }} />
          </span>
          <span className="nx-barras__cifra">{minutos(f.minutos)}</span>
        </li>
      ))}
    </ul>
  );
}

/* ------------------------------------------------------------------ */
/* Crear y editar                                                      */
/* ------------------------------------------------------------------ */

function FormularioDeProyecto({ proyecto, alTerminar }: { proyecto: Proyecto | null; alTerminar: () => void }) {
  const [codigo, setCodigo] = useState(proyecto?.codigo ?? '');
  const [nombre, setNombre] = useState(proyecto?.nombre ?? '');
  const [descripcion, setDescripcion] = useState(proyecto?.descripcion ?? '');
  const [inicio, setInicio] = useState(proyecto?.fechaInicio ?? hoyEnEspana());
  const [fin, setFin] = useState(proyecto?.fechaFin ?? '');
  const [error, setError] = useState<string | null>(null);

  const guardar = useMutacion(
    (cuerpo: components['schemas']['ProjectRequest']) =>
      proyecto?.id !== undefined
        ? pedir(cliente.PATCH('/api/v1/proyectos/{id}', { params: { path: { id: proyecto.id } }, body: cuerpo }))
        : pedir(cliente.POST('/api/v1/proyectos', { body: cuerpo })),
    { invalida: [CLAVE], exito: proyecto === null ? P.creado : P.guardado, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (codigo.trim() === '' || nombre.trim() === '' || inicio === '') return setError(P.faltan);
    if (fin !== '' && fin < inicio) return setError(P.finAntes);
    setError(null);
    guardar.mutate({
      codigo: codigo.trim(),
      nombre: nombre.trim(),
      fechaInicio: inicio,
      ...(descripcion.trim() !== '' ? { descripcion: descripcion.trim() } : {}),
      ...(fin !== '' ? { fechaFin: fin } : {}),
    });
  }

  const mensaje = error ?? guardar.error?.message ?? null;
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <div className="nx-fila-campos">
        <Campo id="proyecto-codigo" etiqueta={P.codigo} ayuda={P.codigoAyuda} maxLength={30} value={codigo} onChange={(e) => setCodigo(e.target.value)} />
        <Campo id="proyecto-nombre" etiqueta={P.nombre} maxLength={150} value={nombre} onChange={(e) => setNombre(e.target.value)} />
      </div>
      <AreaDeTexto id="proyecto-descripcion" etiqueta={P.descripcion} maxLength={1000} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      <div className="nx-fila-campos">
        <Campo id="proyecto-inicio" etiqueta={P.fechaInicio} type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} />
        <Campo id="proyecto-fin" etiqueta={P.fechaFin} type="date" value={fin} onChange={(e) => setFin(e.target.value)} />
      </div>
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={guardar.isPending}>
          {proyecto === null ? P.crear : P.guardar}
        </Boton>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------------ */
/* Detalle                                                             */
/* ------------------------------------------------------------------ */

function FormularioDeAsignacion({ proyectoId, alTerminar }: { proyectoId: number; alTerminar: () => void }) {
  const { consulta: personas } = usePlantilla();
  const [persona, setPersona] = useState('');
  const [desde, setDesde] = useState(hoyEnEspana());
  const [hasta, setHasta] = useState('');
  const [error, setError] = useState<string | null>(null);

  const asignar = useMutacion(
    (cuerpo: components['schemas']['ProjectAssignmentRequest']) =>
      pedir(cliente.POST('/api/v1/proyectos/{id}/asignaciones', { params: { path: { id: proyectoId } }, body: cuerpo })),
    { invalida: [CLAVE], exito: D.asignado, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (persona === '' || desde === '') return setError(D.faltaPersona);
    if (hasta !== '' && hasta < desde) return setError(P.finAntes);
    setError(null);
    asignar.mutate({ usuarioId: Number(persona), fechaInicio: desde, ...(hasta !== '' ? { fechaFin: hasta } : {}) });
  }

  const mensaje = error ?? asignar.error?.message ?? null;
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p className="nx-sutil">{D.asignarAyuda}</p>
      <Selector
        id="asignacion-persona"
        etiqueta={D.persona}
        value={persona}
        onChange={(e) => setPersona(e.target.value)}
        opciones={[
          { valor: '', texto: D.elegir },
          ...ordenar((personas.data ?? []).filter((p) => p.activo !== false)).map((p) => ({ valor: String(p.id), texto: p.nombre ?? '' })),
        ]}
      />
      <div className="nx-fila-campos">
        <Campo id="asignacion-desde" etiqueta={D.desdeEtiqueta} type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        <Campo id="asignacion-hasta" etiqueta={D.hastaEtiqueta} type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
      </div>
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={asignar.isPending}>
          {D.asignarConfirmar}
        </Boton>
      </div>
    </form>
  );
}

function Detalle({ id, alVolver }: { id: number; alVolver: () => void }) {
  const mes = useMes();
  const [editando, setEditando] = useState(false);
  const [asignando, setAsignando] = useState(false);
  const [sacando, setSacando] = useState<Asignacion | null>(null);
  const [borrando, setBorrando] = useState(false);

  const detalle = useQuery({
    queryKey: [...CLAVE, 'detalle', id, mes.anio, mes.mes],
    queryFn: () => pedir(cliente.GET('/api/v1/proyectos/{id}', { params: { path: { id }, query: { anio: mes.anio, mes: mes.mes } } })),
    placeholderData: (anterior) => anterior,
  });

  const estado = useMutacion(
    (activo: boolean) => pedir(cliente.PATCH('/api/v1/proyectos/{id}/estado', { params: { path: { id } }, body: { activo } })),
    { invalida: [CLAVE], exito: (_r, activo) => (activo ? D.reabierto : D.cerrado) },
  );
  const sacar = useMutacion(
    (asignacionId: number) =>
      pedir(
        cliente.PATCH('/api/v1/proyectos/asignaciones/{asignacionId}', {
          params: { path: { asignacionId } },
          // Hasta hoy incluido: deja de estar a partir de mañana.
          body: { fechaFin: hoyEnEspana() },
        }),
      ),
    { invalida: [CLAVE], exito: D.sacado, alTerminar: () => setSacando(null) },
  );
  const borrar = useMutacion(() => pedir(cliente.DELETE('/api/v1/proyectos/{id}', { params: { path: { id } } })), {
    invalida: [CLAVE],
    exito: D.borrado,
    alTerminar: alVolver,
  });

  return (
    <EstadoDeConsulta consulta={detalle} cargando={<Esqueleto lineas={6} />}>
      {(d) => {
        const p = d.proyecto ?? {};
        const asignaciones = d.asignaciones ?? [];
        const horas = d.horas ?? [];
        return (
          <>
            <section className="nx-tarjeta nx-expediente" aria-labelledby="proyecto-titulo">
              <div className="nx-incidencia__cabecera">
                <h2 id="proyecto-titulo">
                  {p.codigo} · {p.nombre}
                </h2>
                {p.activo === false ? <Insignia tono="neutro">{P.cerradoEtiqueta}</Insignia> : <Insignia tono="exito">{P.abierto}</Insignia>}
              </div>
              <span className="nx-sutil">{vigenciaDe(p)}</span>
              {p.descripcion && <p className="nx-texto-largo">{p.descripcion}</p>}
              <div className="nx-acciones-fila">
                <Boton variante="texto" onClick={() => setEditando(true)}>
                  {D.editar}
                </Boton>
                <Boton variante="texto" ocupado={estado.isPending} onClick={() => estado.mutate(p.activo === false)} title={D.cerrarAyuda}>
                  {p.activo === false ? D.reabrir : D.cerrar}
                </Boton>
                {asignaciones.length === 0 && (
                  <Boton variante="texto" onClick={() => setBorrando(true)}>
                    {D.borrar}
                  </Boton>
                )}
              </div>
              {estado.error !== null && <Aviso>{estado.error.message}</Aviso>}
            </section>

            <section className="nx-tarjeta" aria-labelledby="proyecto-quien">
              <div className="nx-incidencia__cabecera">
                <h2 id="proyecto-quien">{D.quienHaEstado}</h2>
                {p.activo !== false && <Boton onClick={() => setAsignando(true)}>{D.asignar}</Boton>}
              </div>
              {asignaciones.length === 0 ? (
                <p className="nx-sutil">{D.sinAsignaciones}</p>
              ) : (
                <ul className="nx-lista-incidencias" aria-label={D.quienHaEstado}>
                  {asignaciones.map((a) => (
                    <li key={a.id} className="nx-incidencia">
                      <div className="nx-incidencia__cabecera">
                        <strong>{a.usuario}</strong>
                        {a.vigente === true && <Insignia tono="exito">{D.vigente}</Insignia>}
                      </div>
                      <span className="nx-sutil">{vigenciaDe(a)}</span>
                      {/* Sacar pone fin hoy: si ya acaba hoy o antes, no hay nada que sacar. */}
                      {a.vigente === true && (a.fechaFin === undefined || a.fechaFin > hoyEnEspana()) && (
                        <div className="nx-acciones-fila">
                          <Boton variante="texto" onClick={() => setSacando(a)}>
                            {D.sacar}
                          </Boton>
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="nx-tarjeta" aria-labelledby="proyecto-horas">
              <NavegadorDeMes mes={mes} id="proyecto-horas" titulo={D.horasDelMes(mesYAnio(mes.anio, mes.mes).toLowerCase())} />
              {horas.length === 0 ? (
                <p className="nx-sutil">{D.sinHoras}</p>
              ) : (
                <Barras filas={horas.map((h) => ({ clave: h.usuarioId ?? 0, texto: h.nombre ?? '', minutos: h.minutos ?? 0 }))} />
              )}
            </section>

            <Dialogo abierto={editando} titulo={D.editarTitulo(p.codigo ?? '')} alCerrar={() => setEditando(false)} acciones={null}>
              {editando && <FormularioDeProyecto proyecto={p} alTerminar={() => setEditando(false)} />}
            </Dialogo>
            <Dialogo abierto={asignando} titulo={D.asignar} alCerrar={() => setAsignando(false)} acciones={null}>
              {asignando && <FormularioDeAsignacion proyectoId={id} alTerminar={() => setAsignando(false)} />}
            </Dialogo>
            <Dialogo
              abierto={sacando !== null}
              titulo={D.sacarTitulo(sacando?.usuario ?? '')}
              alCerrar={() => {
                sacar.reset();
                setSacando(null);
              }}
              acciones={
                <>
                  <Boton variante="texto" onClick={() => setSacando(null)}>
                    {T.app.cancelar}
                  </Boton>
                  <Boton variante="peligro" ocupado={sacar.isPending} onClick={() => sacando?.id !== undefined && sacar.mutate(sacando.id)}>
                    {D.sacar}
                  </Boton>
                </>
              }
            >
              <p>{D.sacarAviso(sacando?.usuario ?? '')}</p>
              {sacar.error !== null && <Aviso>{sacar.error.message}</Aviso>}
            </Dialogo>
            <Dialogo
              abierto={borrando}
              titulo={D.borrarTitulo(p.codigo ?? '')}
              alCerrar={() => {
                borrar.reset();
                setBorrando(false);
              }}
              acciones={
                <>
                  <Boton variante="texto" onClick={() => setBorrando(false)}>
                    {T.app.cancelar}
                  </Boton>
                  <Boton variante="peligro" ocupado={borrar.isPending} onClick={() => borrar.mutate(undefined)}>
                    {D.borrar}
                  </Boton>
                </>
              }
            >
              <p>{D.borrarTexto}</p>
              {borrar.error !== null && <Aviso>{borrar.error.message}</Aviso>}
            </Dialogo>
          </>
        );
      }}
    </EstadoDeConsulta>
  );
}

/* ------------------------------------------------------------------ */
/* La lista                                                            */
/* ------------------------------------------------------------------ */

function HorasPorProyecto() {
  const mes = useMes();
  const horas = useQuery({
    queryKey: [...CLAVE, 'horas', mes.anio, mes.mes],
    queryFn: () => pedir(cliente.GET('/api/v1/proyectos/horas', { params: { query: { anio: mes.anio, mes: mes.mes } } })),
    placeholderData: (anterior) => anterior,
  });
  return (
    <section className="nx-tarjeta" aria-labelledby="proyectos-horas">
      <NavegadorDeMes mes={mes} id="proyectos-horas" titulo={P.horas.titulo(mesYAnio(mes.anio, mes.mes))} />
      <EstadoDeConsulta consulta={horas} cargando={<Esqueleto lineas={3} />}>
        {(h) =>
          (h.proyectos ?? []).length === 0 ? (
            <p className="nx-sutil">{P.horas.vacio}</p>
          ) : (
            <Barras filas={(h.proyectos ?? []).map((x) => ({ clave: x.proyectoId ?? 0, texto: `${x.codigo ?? ''} · ${x.nombre ?? ''}`, minutos: x.minutos ?? 0 }))} />
          )
        }
      </EstadoDeConsulta>
    </section>
  );
}

export function Proyectos() {
  const [busqueda, setBusqueda] = useSearchParams();
  const [creando, setCreando] = useState(false);
  const lista = useQuery({ queryKey: [...CLAVE, 'lista'], queryFn: () => pedir(cliente.GET('/api/v1/proyectos', {})) });

  const elegido = Number(busqueda.get('proyecto'));
  const abierto = Number.isInteger(elegido) && elegido > 0 ? elegido : null;

  const columnas: Columna<Proyecto>[] = [
    { clave: 'codigo', cabecera: P.codigo, celda: (p) => <strong>{p.codigo}</strong> },
    { clave: 'nombre', cabecera: P.nombre, celda: (p) => p.nombre },
    { clave: 'vigencia', cabecera: P.vigencia, celda: (p) => vigenciaDe(p) },
    { clave: 'asignados', cabecera: P.asignados, celda: (p) => P.personas(p.asignados ?? 0), numerica: true },
    {
      clave: 'estado',
      cabecera: P.estado,
      celda: (p) => (p.activo === false ? <Insignia tono="neutro">{P.cerradoEtiqueta}</Insignia> : <Insignia tono="exito">{P.abierto}</Insignia>),
    },
    {
      clave: 'ver',
      cabecera: '',
      celda: (p) => (
        <Boton variante="texto" aria-label={`${P.ver} ${p.codigo ?? ''}`} onClick={() => setBusqueda({ proyecto: String(p.id) })}>
          {P.ver}
        </Boton>
      ),
    },
  ];

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera nx-cabecera--con-acciones">
        <h1>{P.titulo}</h1>
        {abierto === null ? (
          <Boton onClick={() => setCreando(true)}>{P.nuevo}</Boton>
        ) : (
          <Boton variante="secundario" onClick={() => setBusqueda({})}>
            ‹ {P.titulo}
          </Boton>
        )}
      </header>

      {abierto !== null ? (
        <Detalle key={abierto} id={abierto} alVolver={() => setBusqueda({})} />
      ) : (
        <>
          <HorasPorProyecto />
          <section className="nx-tarjeta">
            <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={5} />}>
              {(ps) =>
                ps.length === 0 ? (
                  <Vacio titulo={P.vacioTitulo} detalle={P.vacioTexto} />
                ) : (
                  <Tabla titulo={P.tablaTitulo} columnas={columnas} filas={ps} claveDeFila={(p) => p.id ?? 0} />
                )
              }
            </EstadoDeConsulta>
          </section>
        </>
      )}

      <Dialogo abierto={creando} titulo={P.nuevo} alCerrar={() => setCreando(false)} acciones={null}>
        {creando && <FormularioDeProyecto proyecto={null} alTerminar={() => setCreando(false)} />}
      </Dialogo>
    </div>
  );
}
