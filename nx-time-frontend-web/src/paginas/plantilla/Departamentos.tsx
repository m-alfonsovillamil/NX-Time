/**
 * Los departamentos de la empresa y quién está en cada uno.
 *
 * Es la sección de departamentos del `PanelEmpresaScreen` de la app, con una
 * diferencia que era el motivo de hacerla: **aquí sale toda la plantilla**,
 * gestores incluidos (`/gestor/plantilla`, fase W6). En la app solo se podía
 * poner departamento a los EMPLEADO, y por eso había gestores sin ninguno.
 * El grupo «Sin departamento» lista a esa gente con un selector al lado, para
 * arreglarlo sin ir persona por persona a su ficha.
 *
 * Borrar solo se ofrece con el departamento vacío: el servidor lo rechaza con
 * gente dentro (409) y lo que hay que hacer antes es moverla.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Selector } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Dialogo } from '../../componentes/Dialogo';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { plantilla } from '../../i18n/es/plantilla';
import { CLAVES_PLANTILLA } from './claves';
import { ordenar, usePlantilla, type Persona } from './Plantilla';

const D = plantilla.departamentos;

type Departamento = components['schemas']['DepartmentResponse'];

function Nuevo() {
  const [nombre, setNombre] = useState('');
  const [falta, setFalta] = useState(false);
  const crear = useMutacion((n: string) => pedir(cliente.POST('/api/v1/departamentos', { body: { nombre: n } })), {
    invalida: [CLAVES_PLANTILLA.todo],
    exito: D.creado,
    alTerminar: () => setNombre(''),
  });

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (nombre.trim() === '') return setFalta(true);
    setFalta(false);
    crear.mutate(nombre.trim());
  }

  return (
    <form className="nx-filtros" onSubmit={enviar} noValidate>
      <Campo
        id="departamento-nuevo"
        etiqueta={D.nuevo}
        maxLength={100}
        error={falta ? D.nombreVacio : undefined}
        value={nombre}
        onChange={(e) => setNombre(e.target.value)}
      />
      <Boton type="submit" ocupado={crear.isPending}>
        {D.anadir}
      </Boton>
      {crear.error !== null && <Aviso>{crear.error.message}</Aviso>}
    </form>
  );
}

/** Un selector de departamento para una persona: cambiarlo ya es asignarlo. */
function AsignarDepartamento({ persona, departamentos }: { persona: Persona; departamentos: readonly Departamento[] }) {
  const asignar = useMutacion(
    (departamentoId: number) =>
      pedir(
        cliente.PATCH('/api/v1/departamentos/empleados/{usuarioId}', {
          params: { path: { usuarioId: persona.id ?? 0 } },
          body: { departamentoId },
        }),
      ),
    {
      invalida: [CLAVES_PLANTILLA.todo],
      exito: (_r, id) => D.asignada(persona.nombre ?? '', departamentos.find((d) => d.id === id)?.nombre ?? ''),
    },
  );
  return (
    <>
      <Selector
        id={`asignar-${persona.id}`}
        etiqueta={D.asignarA(persona.nombre ?? '')}
        value=""
        disabled={asignar.isPending}
        onChange={(e) => e.target.value !== '' && asignar.mutate(Number(e.target.value))}
        opciones={[{ valor: '', texto: D.asignar }, ...departamentos.map((d) => ({ valor: String(d.id), texto: d.nombre ?? '' }))]}
      />
      {asignar.error !== null && <Aviso>{asignar.error.message}</Aviso>}
    </>
  );
}

export function Departamentos() {
  const departamentos = useQuery({
    queryKey: CLAVES_PLANTILLA.departamentos,
    queryFn: () => pedir(cliente.GET('/api/v1/departamentos', {})),
  });
  const { consulta: personas } = usePlantilla();
  const [renombrando, setRenombrando] = useState<Departamento | null>(null);
  const [borrando, setBorrando] = useState<Departamento | null>(null);

  const renombrar = useMutacion(
    ({ id, nombre }: { id: number; nombre: string }) =>
      pedir(cliente.PATCH('/api/v1/departamentos/{id}', { params: { path: { id } }, body: { nombre } })),
    { invalida: [CLAVES_PLANTILLA.todo], exito: D.renombrado, alTerminar: () => setRenombrando(null) },
  );
  const borrar = useMutacion(
    (id: number) => pedir(cliente.DELETE('/api/v1/departamentos/{id}', { params: { path: { id } } })),
    { invalida: [CLAVES_PLANTILLA.todo], exito: D.borrado, alTerminar: () => setBorrando(null) },
  );

  const activas = ordenar((personas.data ?? []).filter((p) => p.activo !== false));

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={D.titulo} />

      <section className="nx-tarjeta">
        <Nuevo />
      </section>

      <EstadoDeConsulta consulta={departamentos} cargando={<Esqueleto lineas={4} />}>
        {(lista) => {
          const sinAsignar = activas.filter((p) => p.departamentoId === undefined);
          return (
            <>
              {lista.length === 0 && (
                <section className="nx-tarjeta">
                  <Vacio titulo={D.vacio} />
                </section>
              )}
              <div className="nx-rejilla-tarjetas">
                {lista.map((d) => {
                  const dentro = activas.filter((p) => p.departamentoId === d.id);
                  const cuantos = d.empleados ?? 0;
                  return (
                    <section key={d.id} className="nx-tarjeta nx-departamento" aria-labelledby={`dep-${d.id}`}>
                      <div className="nx-incidencia__cabecera">
                        <h2 id={`dep-${d.id}`}>{d.nombre}</h2>
                        <span className="nx-sutil">{D.personas(cuantos)}</span>
                      </div>
                      {dentro.length > 0 && <p>{dentro.map((p) => p.nombre).join(', ')}</p>}
                      <div className="nx-acciones-fila">
                        <Boton variante="texto" onClick={() => setRenombrando(d)}>
                          {D.renombrar}
                        </Boton>
                        {cuantos === 0 && (
                          <Boton variante="texto" onClick={() => setBorrando(d)}>
                            {D.borrar}
                          </Boton>
                        )}
                      </div>
                    </section>
                  );
                })}
              </div>
              {lista.length > 0 && sinAsignar.length > 0 && (
                <section className="nx-tarjeta" aria-labelledby="dep-sin">
                  <h2 id="dep-sin">{D.sinAsignar}</h2>
                  <p className="nx-sutil">{D.sinAsignarAyuda}</p>
                  <ul className="nx-lista-incidencias">
                    {sinAsignar.map((p) => (
                      <li key={p.id} className="nx-incidencia">
                        <AsignarDepartamento persona={p} departamentos={lista} />
                      </li>
                    ))}
                  </ul>
                </section>
              )}
            </>
          );
        }}
      </EstadoDeConsulta>

      <DialogoDeTexto
        abierto={renombrando !== null}
        titulo={D.renombrarTitulo(renombrando?.nombre ?? '')}
        etiqueta={D.nuevo}
        vacio={D.nombreVacio}
        boton={plantilla.guardar}
        maxLength={100}
        inicial={renombrando?.nombre ?? ''}
        ocupado={renombrar.isPending}
        error={renombrar.error?.message ?? null}
        alEnviar={(nombre) => renombrando?.id !== undefined && renombrar.mutate({ id: renombrando.id, nombre })}
        alCerrar={() => {
          renombrar.reset();
          setRenombrando(null);
        }}
      />

      <Dialogo
        abierto={borrando !== null}
        titulo={D.borrarTitulo(borrando?.nombre ?? '')}
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
              {D.borrar}
            </Boton>
          </>
        }
      >
        <p>{D.borrarTexto}</p>
        {borrar.error !== null && <Aviso>{borrar.error.message}</Aviso>}
      </Dialogo>
    </div>
  );
}
