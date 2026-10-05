/**
 * El cuadrante de una persona: su horario de las próximas dos semanas, las
 * plantillas que tiene asignadas y sus excepciones.
 *
 * **El pasado no se reescribe.** Asignar, solo desde hoy; una asignación que
 * ya ha estado en vigor no se borra, se cierra (con un último día que no puede
 * ser anterior a ayer); una que aún no ha empezado sí se puede borrar. Las
 * excepciones, solo de hoy en adelante.
 *
 * **El aviso de «el cuadrante no suma la jornada contratada» es del
 * servidor**: si la plantilla se separa más de media hora a la semana de su
 * contrato, se asigna igual, la respuesta trae el `aviso` y se queda a la
 * vista (una notificación que se va sola no bastaría).
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { AreaDeTexto, Aviso, Boton, Campo, Insignia, Selector } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { cuadrantes } from '../../i18n/es/cuadrantes';
import { fechaCorta, hoyEnEmpresa, sumarDias } from '../../util/fechas';
import { Dia } from '../cuadrante/MiCuadrante';
import { ListaDeTramos } from './ListaDeTramos';
import { CLAVE_CUADRANTES } from './Plantillas';
import { aTramo, problemaEnElTramo, solapeEnUnDia, type TramoEditable } from './reglas';

const C = cuadrantes.persona;

type Asignacion = components['schemas']['ScheduleAssignmentResponse'];

function FormularioDeAsignacion({ usuarioId, alTerminar }: { usuarioId: number; alTerminar: (aviso: string | null) => void }) {
  const hoy = hoyEnEmpresa();
  const plantillas = useQuery({
    queryKey: [...CLAVE_CUADRANTES, 'plantillas'],
    queryFn: () => pedir(cliente.GET('/api/v1/cuadrantes/plantillas', {})),
  });
  const [plantilla, setPlantilla] = useState('');
  const [desde, setDesde] = useState(hoy);
  const [hasta, setHasta] = useState('');
  const [falta, setFalta] = useState(false);

  const asignar = useMutacion(
    (cuerpo: components['schemas']['ScheduleAssignmentRequest']) => pedir(cliente.POST('/api/v1/cuadrantes/asignaciones', { body: cuerpo })),
    { invalida: [CLAVE_CUADRANTES], exito: C.asignada, alTerminar: (r) => alTerminar(r.aviso ?? null) },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (plantilla === '' || desde === '') return setFalta(true);
    setFalta(false);
    asignar.mutate({ usuarioId, plantillaId: Number(plantilla), fechaInicio: desde, ...(hasta !== '' ? { fechaFin: hasta } : {}) });
  }

  const mensaje = falta ? C.faltanAsignacion : (asignar.error?.message ?? null);
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p className="nx-sutil">{C.asignarAyuda}</p>
      <Selector
        id="asignacion-plantilla"
        etiqueta={C.plantilla}
        value={plantilla}
        onChange={(e) => setPlantilla(e.target.value)}
        opciones={[{ valor: '', texto: C.elegirPlantilla }, ...(plantillas.data ?? []).map((p) => ({ valor: String(p.id), texto: p.nombre ?? '' }))]}
      />
      <div className="nx-fila-campos">
        <Campo id="asignacion-desde" etiqueta={C.fechaInicio} type="date" min={hoy} value={desde} onChange={(e) => setDesde(e.target.value)} />
        <Campo id="asignacion-hasta" etiqueta={C.fechaFin} type="date" min={desde} value={hasta} onChange={(e) => setHasta(e.target.value)} />
      </div>
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={() => alTerminar(null)}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={asignar.isPending}>
          {C.asignar}
        </Boton>
      </div>
    </form>
  );
}

function FormularioDeCierre({ asignacion, alTerminar }: { asignacion: Asignacion; alTerminar: () => void }) {
  const hoy = hoyEnEmpresa();
  const [ultimo, setUltimo] = useState(hoy);
  const cerrar = useMutacion(
    (fechaFin: string) =>
      pedir(cliente.PATCH('/api/v1/cuadrantes/asignaciones/{asignacionId}', { params: { path: { asignacionId: asignacion.id ?? 0 } }, body: { fechaFin } })),
    { invalida: [CLAVE_CUADRANTES], exito: C.cerrada, alTerminar },
  );
  return (
    <form
      className="nx-formulario-dialogo"
      onSubmit={(e) => {
        e.preventDefault();
        cerrar.mutate(ultimo);
      }}
      noValidate
    >
      <p className="nx-sutil">{C.cerrarAyuda}</p>
      <Campo id="cierre-ultimo" etiqueta={C.ultimoDia} type="date" min={sumarDias(hoy, -1)} value={ultimo} onChange={(e) => setUltimo(e.target.value)} />
      {cerrar.error !== null && <Aviso>{cerrar.error.message}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={cerrar.isPending}>
          {C.cerrar}
        </Boton>
      </div>
    </form>
  );
}

function FormularioDeExcepcion({ usuarioId, alTerminar }: { usuarioId: number; alTerminar: () => void }) {
  const hoy = hoyEnEmpresa();
  const [fecha, setFecha] = useState(hoy);
  const [tipo, setTipo] = useState<'LIBRE' | 'TRAMO'>('LIBRE');
  const [tramos, setTramos] = useState<TramoEditable[]>([{ inicio: '09:00', fin: '14:00', alDiaSiguiente: false }]);
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState<string | null>(null);

  const crear = useMutacion(
    (cuerpo: components['schemas']['ScheduleExceptionRequest']) => pedir(cliente.POST('/api/v1/cuadrantes/excepciones', { body: cuerpo })),
    { invalida: [CLAVE_CUADRANTES], exito: C.excepcionCreada, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (fecha === '') return setError(C.faltaFecha);
    let enMinutos: { inicio: number; fin: number }[] = [];
    if (tipo === 'TRAMO') {
      const convertidos = tramos.map(aTramo);
      if (convertidos.some((t) => t === null) || convertidos.length === 0) return setError(cuadrantes.plantilla.horaMal);
      enMinutos = convertidos as { inicio: number; fin: number }[];
      const problema = enMinutos.map((t) => problemaEnElTramo(t.inicio, t.fin)).find((p) => p !== null) ?? solapeEnUnDia(enMinutos);
      if (problema) return setError(problema.replace(/^./, (c) => c.toUpperCase()));
    }
    setError(null);
    // Los tramos solo llevan inicio y fin: el día es el de la excepción.
    crear.mutate({
      usuarioId,
      fecha,
      tipo,
      ...(tipo === 'TRAMO' ? { tramos: enMinutos } : {}),
      ...(motivo.trim() !== '' ? { motivo: motivo.trim() } : {}),
    } as components['schemas']['ScheduleExceptionRequest']);
  }

  const mensaje = error ?? crear.error?.message ?? null;
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p className="nx-sutil">{C.excepcionAyuda}</p>
      <div className="nx-fila-campos">
        <Campo id="excepcion-fecha" etiqueta={C.fecha} type="date" min={hoy} value={fecha} onChange={(e) => setFecha(e.target.value)} />
        <Selector
          id="excepcion-tipo"
          etiqueta={C.tipo}
          value={tipo}
          onChange={(e) => setTipo(e.target.value as 'LIBRE' | 'TRAMO')}
          opciones={(['LIBRE', 'TRAMO'] as const).map((t) => ({ valor: t, texto: C.tipos[t] ?? t }))}
        />
      </div>
      {tipo === 'TRAMO' && <ListaDeTramos id="excepcion-tramos" dia={fechaCorta(fecha)} tramos={tramos} alCambiar={setTramos} />}
      <AreaDeTexto id="excepcion-motivo" etiqueta={C.motivo} maxLength={300} rows={2} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={crear.isPending}>
          {C.anadirExcepcion}
        </Boton>
      </div>
    </form>
  );
}

export function PorPersona({ usuarioId, nombre }: { usuarioId: number; nombre: string }) {
  const hoy = hoyEnEmpresa();
  const [asignando, setAsignando] = useState(false);
  const [cerrando, setCerrando] = useState<Asignacion | null>(null);
  const [anadiendo, setAnadiendo] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  const proximas = useQuery({
    queryKey: [...CLAVE_CUADRANTES, 'persona', usuarioId, 'dias', hoy],
    queryFn: () =>
      pedir(cliente.GET('/api/v1/cuadrantes/usuarios/{usuarioId}', { params: { path: { usuarioId }, query: { desde: hoy, hasta: sumarDias(hoy, 13) } } })),
  });
  const asignaciones = useQuery({
    queryKey: [...CLAVE_CUADRANTES, 'persona', usuarioId, 'asignaciones'],
    queryFn: () => pedir(cliente.GET('/api/v1/cuadrantes/usuarios/{usuarioId}/asignaciones', { params: { path: { usuarioId } } })),
  });
  const excepciones = useQuery({
    queryKey: [...CLAVE_CUADRANTES, 'persona', usuarioId, 'excepciones'],
    queryFn: () => pedir(cliente.GET('/api/v1/cuadrantes/usuarios/{usuarioId}/excepciones', { params: { path: { usuarioId } } })),
  });

  const deshacer = useMutacion(
    (id: number) => pedir(cliente.DELETE('/api/v1/cuadrantes/asignaciones/{asignacionId}', { params: { path: { asignacionId: id } } })),
    { invalida: [CLAVE_CUADRANTES], exito: C.borrada },
  );
  const quitarExcepcion = useMutacion(
    (id: number) => pedir(cliente.DELETE('/api/v1/cuadrantes/excepciones/{excepcionId}', { params: { path: { excepcionId: id } } })),
    { invalida: [CLAVE_CUADRANTES], exito: C.excepcionQuitada },
  );

  return (
    <div className="nx-expediente">
      {aviso !== null && <Aviso>{aviso}</Aviso>}

      <section aria-labelledby="persona-asignaciones">
        <div className="nx-incidencia__cabecera">
          <h3 id="persona-asignaciones">{C.asignaciones}</h3>
          <Boton onClick={() => setAsignando(true)}>{C.asignar}</Boton>
        </div>
        {deshacer.error !== null && <Aviso>{deshacer.error.message}</Aviso>}
        <EstadoDeConsulta consulta={asignaciones} cargando={<Esqueleto lineas={2} />}>
          {(lista) =>
            lista.length === 0 ? (
              <Vacio icono="cuadrantes" titulo={C.sinAsignaciones} />
            ) : (
              <ul className="nx-lista-incidencias" aria-label={C.asignaciones}>
                {lista.map((a) => {
                  const futura = (a.fechaInicio ?? '') > hoy;
                  // Cerrable si sigue abierta o acaba después de ayer (no se puede cerrar antes de ayer).
                  const cerrable = !futura && (a.fechaFin === undefined || a.fechaFin >= hoy);
                  return (
                    <li key={a.id} className="nx-incidencia">
                      <div className="nx-incidencia__cabecera">
                        <strong>{a.plantillaNombre}</strong>
                        {a.vigente === true ? <Insignia tono="exito">{C.vigente}</Insignia> : futura ? <Insignia tono="info">{C.futura}</Insignia> : null}
                      </div>
                      <span className="nx-sutil">
                        {a.fechaFin ? C.rango(fechaCorta(a.fechaInicio), fechaCorta(a.fechaFin)) : C.desde(fechaCorta(a.fechaInicio))}
                      </span>
                      <div className="nx-acciones-fila">
                        {futura && (
                          <Boton variante="texto" title={C.deshacerAyuda} onClick={() => a.id !== undefined && deshacer.mutate(a.id)}>
                            {C.deshacer}
                          </Boton>
                        )}
                        {cerrable && (
                          <Boton variante="texto" onClick={() => setCerrando(a)}>
                            {C.cerrar}
                          </Boton>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )
          }
        </EstadoDeConsulta>
      </section>

      <section aria-labelledby="persona-excepciones">
        <div className="nx-incidencia__cabecera">
          <h3 id="persona-excepciones">{C.excepciones}</h3>
          <Boton variante="secundario" onClick={() => setAnadiendo(true)}>
            {C.anadirExcepcion}
          </Boton>
        </div>
        {quitarExcepcion.error !== null && <Aviso>{quitarExcepcion.error.message}</Aviso>}
        <EstadoDeConsulta consulta={excepciones} cargando={<Esqueleto lineas={2} />}>
          {(lista) =>
            lista.length === 0 ? (
              <Vacio icono="festivo" titulo={C.sinExcepciones} />
            ) : (
              <ul className="nx-lista-incidencias" aria-label={C.excepciones}>
                {lista.map((e) => (
                  <li key={e.id} className="nx-incidencia">
                    <div className="nx-incidencia__cabecera">
                      <strong>{fechaCorta(e.fecha)}</strong>
                      <span>{e.tipo === 'LIBRE' ? C.libre : `${e.horaInicio ?? ''}–${e.horaFin ?? ''}`}</span>
                    </div>
                    {e.motivo && <span className="nx-sutil">{e.motivo}</span>}
                    {(e.fecha ?? '') >= hoy && (
                      <div className="nx-acciones-fila">
                        <Boton variante="texto" onClick={() => e.id !== undefined && quitarExcepcion.mutate(e.id)}>
                          {C.quitarExcepcion}
                        </Boton>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )
          }
        </EstadoDeConsulta>
      </section>

      <section aria-labelledby="persona-proximas">
        <h3 id="persona-proximas">{C.proximas}</h3>
        <EstadoDeConsulta consulta={proximas} cargando={<Esqueleto lineas={7} />}>
          {(dias) => (
            <ul className="nx-lista-cuadrante">
              {dias.map((d) => (
                <Dia key={d.fecha} dia={d} esHoy={d.fecha === hoy} />
              ))}
            </ul>
          )}
        </EstadoDeConsulta>
      </section>

      <Dialogo abierto={asignando} titulo={C.asignarTitulo(nombre)} alCerrar={() => setAsignando(false)} acciones={null}>
        {asignando && (
          <FormularioDeAsignacion
            usuarioId={usuarioId}
            alTerminar={(avisoDelServidor) => {
              setAviso(avisoDelServidor);
              setAsignando(false);
            }}
          />
        )}
      </Dialogo>
      <Dialogo abierto={cerrando !== null} titulo={C.cerrarTitulo} alCerrar={() => setCerrando(null)} acciones={null}>
        {cerrando !== null && <FormularioDeCierre asignacion={cerrando} alTerminar={() => setCerrando(null)} />}
      </Dialogo>
      <Dialogo abierto={anadiendo} titulo={C.excepcionTitulo(nombre)} alCerrar={() => setAnadiendo(false)} acciones={null}>
        {anadiendo && <FormularioDeExcepcion usuarioId={usuarioId} alTerminar={() => setAnadiendo(false)} />}
      </Dialogo>
    </div>
  );
}
