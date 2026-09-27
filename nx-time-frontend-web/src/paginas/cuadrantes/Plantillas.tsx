/**
 * Las plantillas de horario: un horario semanal con sus tramos, que luego se
 * asigna a cada persona.
 *
 * El editor comprueba los solapes en pantalla, entre días incluidos (la noche
 * del lunes pisa la mañana del martes), con las mismas reglas que el servidor
 * (`reglas.ts`); el servidor lo vuelve a comprobar. **Una plantilla que ya se
 * ha aplicado a días pasados solo se renombra**: cambiar sus tramos
 * reescribiría su horario teórico (`tramosEditables` lo dice el servidor).
 * Borrar, solo si nadie la tiene ni la ha tenido (`borrable`).
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { cuadrantes } from '../../i18n/es/cuadrantes';
import { minutos } from '../../util/fechas';
import { textoDelTramo } from '../cuadrante/MiCuadrante';
import { ListaDeTramos } from './ListaDeTramos';
import { DIAS, aEditable, aTramo, minutosSemanales, problemaEnLaSemana, type TramoEditable, type TramoSemanal } from './reglas';

const P = cuadrantes.plantilla;

export type Plantilla = components['schemas']['ScheduleTemplateResponse'];

export const CLAVE_CUADRANTES = ['cuadrantes'] as const;

function porDias(plantilla: Plantilla | null): TramoEditable[][] {
  const dias: TramoEditable[][] = DIAS.map(() => []);
  for (const t of plantilla?.tramos ?? []) {
    (dias[(t.diaSemana ?? 1) - 1] as TramoEditable[]).push(aEditable(t.inicio ?? 0, t.fin ?? 0));
  }
  return dias;
}

/** Los tramos de todos los días, en minutos; `null` si alguna hora no es una hora. */
export function aSemana(dias: readonly (readonly TramoEditable[])[]): TramoSemanal[] | null {
  const semana: TramoSemanal[] = [];
  for (const [i, tramos] of dias.entries()) {
    for (const t of tramos) {
      const tramo = aTramo(t);
      if (tramo === null) return null;
      semana.push({ diaSemana: i + 1, ...tramo });
    }
  }
  return semana;
}

function FormularioDePlantilla({ plantilla, alTerminar }: { plantilla: Plantilla | null; alTerminar: () => void }) {
  const [nombre, setNombre] = useState(plantilla?.nombre ?? '');
  const [descripcion, setDescripcion] = useState(plantilla?.descripcion ?? '');
  const [dias, setDias] = useState(() => porDias(plantilla));
  const [faltaNombre, setFaltaNombre] = useState(false);
  const bloqueada = plantilla !== null && plantilla.tramosEditables === false;

  const guardar = useMutacion(
    (cuerpo: components['schemas']['ScheduleTemplateRequest']) =>
      plantilla?.id !== undefined
        ? pedir(cliente.PUT('/api/v1/cuadrantes/plantillas/{plantillaId}', { params: { path: { plantillaId: plantilla.id } }, body: cuerpo }))
        : pedir(cliente.POST('/api/v1/cuadrantes/plantillas', { body: cuerpo })),
    { invalida: [CLAVE_CUADRANTES], exito: plantilla === null ? P.creada : P.guardada, alTerminar },
  );

  const semana = aSemana(dias);
  const problema = semana === null ? P.horaMal : problemaEnLaSemana(semana);

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (nombre.trim() === '') return setFaltaNombre(true);
    setFaltaNombre(false);
    if (semana === null || problema !== null) return;
    guardar.mutate({ nombre: nombre.trim(), ...(descripcion.trim() !== '' ? { descripcion: descripcion.trim() } : {}), tramos: semana });
  }

  // Los problemas de tramos se calculan en vivo: arreglar el tramo quita el aviso.
  const mensaje = (faltaNombre && nombre.trim() === '' ? P.faltaNombre : null) ?? problema ?? guardar.error?.message ?? null;
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <div className="nx-fila-campos">
        <Campo id="plantilla-nombre" etiqueta={P.nombre} maxLength={100} value={nombre} onChange={(e) => setNombre(e.target.value)} />
        <Campo id="plantilla-descripcion" etiqueta={P.descripcion} maxLength={500} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      </div>
      {bloqueada && <p className="nx-sutil">{P.tramosBloqueados}</p>}
      <ol className="nx-semana-editable">
        {DIAS.map((dia, i) => (
          <li key={dia}>
            <strong className="nx-semana-editable__dia">{dia.replace(/^./, (c) => c.toUpperCase())}</strong>
            <ListaDeTramos
              id={`plantilla-${i}`}
              dia={dia}
              tramos={dias[i] ?? []}
              bloqueada={bloqueada}
              alCambiar={(tramos) => setDias(dias.map((d, j) => (j === i ? tramos : d)))}
            />
          </li>
        ))}
      </ol>
      {semana !== null && <p>{P.total(minutos(minutosSemanales(semana)))}</p>}
      {/* El problema se enseña en cuanto aparece, no al guardar: es lo que señala el tramo. */}
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={guardar.isPending}>
          {P.guardar}
        </Boton>
      </div>
    </form>
  );
}

export function Plantillas() {
  const [editando, setEditando] = useState<Plantilla | 'nueva' | null>(null);
  const [borrando, setBorrando] = useState<Plantilla | null>(null);
  const lista = useQuery({
    queryKey: [...CLAVE_CUADRANTES, 'plantillas'],
    queryFn: () => pedir(cliente.GET('/api/v1/cuadrantes/plantillas', {})),
  });
  const borrar = useMutacion(
    (id: number) => pedir(cliente.DELETE('/api/v1/cuadrantes/plantillas/{plantillaId}', { params: { path: { plantillaId: id } } })),
    { invalida: [CLAVE_CUADRANTES], exito: P.borrada, alTerminar: () => setBorrando(null) },
  );

  return (
    <>
      <div className="nx-acciones-fila">
        <Boton onClick={() => setEditando('nueva')}>{P.nueva}</Boton>
      </div>
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
        {(plantillas) =>
          plantillas.length === 0 ? (
            <Vacio titulo={P.vacio} />
          ) : (
            <ul className="nx-lista-incidencias" aria-label={cuadrantes.plantillas}>
              {plantillas.map((p) => (
                <li key={p.id} className="nx-incidencia">
                  <div className="nx-incidencia__cabecera">
                    <strong>{p.nombre}</strong>
                    <span className="nx-sutil">{P.semanales(minutos(p.minutosSemanales ?? 0))}</span>
                  </div>
                  {p.descripcion && <span className="nx-sutil">{p.descripcion}</span>}
                  <ul className="nx-resumen-semana">
                    {DIAS.map((dia, i) => {
                      const tramos = (p.tramos ?? []).filter((t) => t.diaSemana === i + 1);
                      return (
                        <li key={dia}>
                          <span className="nx-sutil">{dia.slice(0, 3)}</span> {tramos.length === 0 ? P.libre : tramos.map(textoDelTramo).join(' · ')}
                        </li>
                      );
                    })}
                  </ul>
                  <div className="nx-acciones-fila">
                    <Boton variante="texto" onClick={() => setEditando(p)}>
                      {P.editar}
                    </Boton>
                    {p.borrable === true && (
                      <Boton variante="texto" onClick={() => setBorrando(p)}>
                        {P.borrar}
                      </Boton>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <Dialogo
        abierto={editando !== null}
        titulo={editando === 'nueva' || editando === null ? P.nueva : P.editarTitulo(editando.nombre ?? '')}
        alCerrar={() => setEditando(null)}
        acciones={null}
      >
        {editando !== null && <FormularioDePlantilla plantilla={editando === 'nueva' ? null : editando} alTerminar={() => setEditando(null)} />}
      </Dialogo>

      <Dialogo
        abierto={borrando !== null}
        titulo={P.borrarTitulo(borrando?.nombre ?? '')}
        alCerrar={() => {
          borrar.reset();
          setBorrando(null);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setBorrando(null)}>
              {T.app.cancelar}
            </Boton>
            <Boton variante="peligro" ocupado={borrar.isPending} onClick={() => borrando?.id !== undefined && borrar.mutate(borrando.id)}>
              {P.borrar}
            </Boton>
          </>
        }
      >
        <p>{P.borrarTexto}</p>
        {borrar.error !== null && <Aviso>{borrar.error.message}</Aviso>}
      </Dialogo>
    </>
  );
}
