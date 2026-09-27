/**
 * El calendario laboral de la empresa: los festivos de cada mes, y añadir,
 * cambiar o quitar los autonómicos, locales y de empresa.
 *
 * **Los nacionales no se tocan**: los calcula el sistema y son de todas las
 * empresas. El servidor dice cuáles se pueden editar (`editable`) y rechaza el
 * ámbito NACIONAL al crear; la página no lo ofrece. Los autonómicos y locales,
 * y el traslado de un festivo que cae en domingo, los publica cada año el BOE
 * o la comunidad: hay que meterlos a mano, y la nota lo recuerda.
 *
 * Usa la misma consulta que el calendario de «lo mío»: un festivo nuevo sale
 * también allí sin recargar.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Insignia, Selector } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { proyectos } from '../../i18n/es/proyectos';
import { diaLargo, mesYAnio } from '../../util/fechas';
import { NavegadorDeMes, useMes } from './mes';

const C = proyectos.calendario;

type Festivo = components['schemas']['HolidayResponse'];
type Ambito = components['schemas']['HolidayRequest']['ambito'];

const AMBITOS_EDITABLES: Ambito[] = ['AUTONOMICO', 'LOCAL', 'EMPRESA'];

function FormularioDeFestivo({ festivo, diaInicial, alTerminar }: { festivo: Festivo | null; diaInicial: string; alTerminar: () => void }) {
  const [fecha, setFecha] = useState(festivo?.fecha ?? diaInicial);
  const [descripcion, setDescripcion] = useState(festivo?.descripcion ?? '');
  const [ambito, setAmbito] = useState<Ambito>(festivo?.ambito && festivo.ambito !== 'NACIONAL' ? festivo.ambito : 'AUTONOMICO');
  const [falta, setFalta] = useState(false);

  const guardar = useMutacion(
    (cuerpo: components['schemas']['HolidayRequest']) =>
      festivo?.id !== undefined
        ? pedir(cliente.PATCH('/api/v1/calendario/festivos/{id}', { params: { path: { id: festivo.id } }, body: cuerpo }))
        : pedir(cliente.POST('/api/v1/calendario/festivos', { body: cuerpo })),
    { invalida: [['calendario']], exito: festivo === null ? C.creado : C.guardado, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (fecha === '' || descripcion.trim() === '') return setFalta(true);
    setFalta(false);
    guardar.mutate({ fecha, descripcion: descripcion.trim(), ambito });
  }

  const mensaje = falta ? C.faltan : (guardar.error?.message ?? null);
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <div className="nx-fila-campos">
        <Campo id="festivo-fecha" etiqueta={C.fecha} type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} />
        <Selector
          id="festivo-ambito"
          etiqueta={C.ambito}
          value={ambito}
          onChange={(e) => setAmbito(e.target.value as Ambito)}
          opciones={AMBITOS_EDITABLES.map((a) => ({ valor: a, texto: C.ambitos[a] ?? a }))}
        />
      </div>
      <Campo id="festivo-descripcion" etiqueta={C.descripcion} maxLength={150} value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={guardar.isPending}>
          {C.guardar}
        </Boton>
      </div>
    </form>
  );
}

export function CalendarioLaboral() {
  const mes = useMes();
  const [editando, setEditando] = useState<Festivo | 'nuevo' | null>(null);
  const [quitando, setQuitando] = useState<Festivo | null>(null);

  const calendario = useQuery({
    // La misma clave que el calendario de «lo mío» sin el equipo: comparten caché.
    queryKey: ['calendario', mes.anio, mes.mes, false],
    queryFn: () => pedir(cliente.GET('/api/v1/calendario', { params: { query: { anio: mes.anio, mes: mes.mes, equipo: false } } })),
    placeholderData: (anterior) => anterior,
  });

  const quitar = useMutacion(
    (id: number) => pedir(cliente.DELETE('/api/v1/calendario/festivos/{id}', { params: { path: { id } } })),
    { invalida: [['calendario']], exito: C.quitado, alTerminar: () => setQuitando(null) },
  );

  const primerDia = `${mes.anio}-${String(mes.mes).padStart(2, '0')}-01`;

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera nx-cabecera--con-acciones">
        <h1>{C.titulo}</h1>
        <Boton onClick={() => setEditando('nuevo')}>{C.anadir}</Boton>
      </header>
      <p className="nx-sutil">{C.nota}</p>

      <section className="nx-tarjeta">
        <NavegadorDeMes mes={mes} id="calendario-laboral-mes" titulo={C.delMes(mesYAnio(mes.anio, mes.mes).toLowerCase())} />
        <EstadoDeConsulta consulta={calendario} cargando={<Esqueleto lineas={3} />}>
          {(c) => {
            const festivos = [...(c.festivos ?? [])].sort((a, b) => (a.fecha ?? '').localeCompare(b.fecha ?? ''));
            return festivos.length === 0 ? (
              <p className="nx-sutil">{C.vacio}</p>
            ) : (
              <ul className="nx-lista-incidencias" aria-labelledby="calendario-laboral-mes">
                {festivos.map((f) => (
                  <li key={`${f.fecha}-${f.id ?? f.descripcion}`} className="nx-incidencia">
                    <div className="nx-incidencia__cabecera">
                      <strong>{diaLargo(f.fecha ?? '')}</strong>
                      <Insignia tono={f.ambito === 'NACIONAL' ? 'neutro' : 'info'}>{C.ambitos[f.ambito ?? ''] ?? f.ambito}</Insignia>
                    </div>
                    <span>{f.descripcion}</span>
                    {f.editable === true && f.id !== undefined ? (
                      <div className="nx-acciones-fila">
                        <Boton variante="texto" onClick={() => setEditando(f)}>
                          {C.editar}
                        </Boton>
                        <Boton variante="texto" onClick={() => setQuitando(f)}>
                          {C.quitar}
                        </Boton>
                      </div>
                    ) : (
                      <span className="nx-sutil">{C.noEditable}</span>
                    )}
                  </li>
                ))}
              </ul>
            );
          }}
        </EstadoDeConsulta>
      </section>

      <Dialogo abierto={editando !== null} titulo={editando === 'nuevo' ? C.anadirTitulo : C.editarTitulo} alCerrar={() => setEditando(null)} acciones={null}>
        {editando !== null && (
          <FormularioDeFestivo festivo={editando === 'nuevo' ? null : editando} diaInicial={primerDia} alTerminar={() => setEditando(null)} />
        )}
      </Dialogo>

      <Dialogo
        abierto={quitando !== null}
        titulo={C.quitarTitulo(quitando?.descripcion ?? '')}
        alCerrar={() => {
          quitar.reset();
          setQuitando(null);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setQuitando(null)}>
              {T.app.cancelar}
            </Boton>
            <Boton variante="peligro" ocupado={quitar.isPending} onClick={() => quitando?.id !== undefined && quitar.mutate(quitando.id)}>
              {C.quitar}
            </Boton>
          </>
        }
      >
        <p>{C.quitarTexto}</p>
        {quitar.error !== null && <Aviso>{quitar.error.message}</Aviso>}
      </Dialogo>
    </div>
  );
}
