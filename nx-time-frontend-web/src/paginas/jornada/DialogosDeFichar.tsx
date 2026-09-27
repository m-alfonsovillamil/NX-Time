/**
 * Las tres preguntas que puede hacer «Mi jornada» antes de fichar.
 *
 * - **¿Terminas la jornada?** Iniciar va directo y terminar no, y la asimetría
 *   es deliberada (la misma que en la app): empezar por error se arregla
 *   terminando; cerrar por error obliga a pedir una corrección y a que
 *   alguien la apruebe. Se confirma lo que cuesta deshacer.
 * - **Hoy no es laborable.** Se puede fichar igual; se avisa porque el
 *   servidor avisará a quien aprueba ausencias.
 * - **¿En qué proyecto?** Solo con dos o más proyectos disponibles: con uno o
 *   ninguno decide el servidor (ADR 017).
 */

import { useState, type FormEvent } from 'react';

import { Boton } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { T } from '../../i18n/es';
import { duracion, hora } from '../../util/fechas';
import type { Jornada, ProyectosParaFichar } from './consultas';

const F = T.fichar;

export function ConfirmarFin({
  jornada,
  segundosTrabajados,
  abierto,
  alConfirmar,
  alCancelar,
}: {
  jornada: Jornada | null;
  segundosTrabajados: number;
  abierto: boolean;
  alConfirmar: () => void;
  alCancelar: () => void;
}) {
  const pausa = jornada?.segundosPausaAcumulados ?? 0;
  return (
    <Dialogo
      abierto={abierto}
      titulo={F.confirmarFin.titulo}
      alCerrar={alCancelar}
      acciones={
        <>
          <Boton variante="texto" onClick={alCancelar}>
            {F.confirmarFin.no}
          </Boton>
          <Boton onClick={alConfirmar}>{F.confirmarFin.si}</Boton>
        </>
      }
    >
      <ul className="nx-lista-simple">
        <li>{F.confirmarFin.entrada(hora(jornada?.horaEntrada))}</li>
        <li>{F.confirmarFin.trabajado(duracion(segundosTrabajados))}</li>
        {pausa > 0 && <li>{F.confirmarFin.pausa(duracion(pausa))}</li>}
      </ul>
      <p className="nx-sutil">{F.confirmarFin.aviso}</p>
    </Dialogo>
  );
}

export function ConfirmarNoLaborable({
  motivo,
  alConfirmar,
  alCancelar,
}: {
  /** Null = cerrado. */
  motivo: string | null;
  alConfirmar: () => void;
  alCancelar: () => void;
}) {
  return (
    <Dialogo
      abierto={motivo !== null}
      titulo={F.noLaborableDialogo.titulo}
      alCerrar={alCancelar}
      acciones={
        <>
          <Boton variante="texto" onClick={alCancelar}>
            {T.app.cancelar}
          </Boton>
          <Boton onClick={alConfirmar}>{F.noLaborableDialogo.si}</Boton>
        </>
      }
    >
      <p>{F.noLaborableDialogo.texto(motivo ?? '')}</p>
    </Dialogo>
  );
}

/**
 * Elegir proyecto, para empezar la jornada o para cambiar con ella abierta.
 *
 * Un `<fieldset>` con radios y no una lista de botones: se ve cuál está
 * elegido antes de confirmar, y con el teclado se recorre con las flechas.
 * El formulario vive dentro del diálogo, que solo lo monta abierto: cada vez
 * que se abre empieza sin elección previa.
 */
function FormularioDeProyecto({
  modo,
  proyectos,
  ocupado,
  alElegir,
  alCancelar,
}: {
  modo: 'empezar' | 'cambiar';
  proyectos: ProyectosParaFichar | undefined;
  ocupado: boolean;
  alElegir: (proyectoId: number) => void;
  alCancelar: () => void;
}) {
  const actual = proyectos?.enCurso?.id;
  const opciones = proyectos?.disponibles ?? [];
  // Para empezar se propone el primero; para cambiar no se propone nada, que
  // el que está puesto ya es el de ahora.
  const [seleccion, setSeleccion] = useState<number | null>(modo === 'empezar' ? (opciones[0]?.id ?? null) : null);

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (seleccion !== null && seleccion !== actual) alElegir(seleccion);
  }

  return (
    <form onSubmit={enviar} className="nx-formulario-dialogo">
      <fieldset className="nx-opciones">
        <legend className="nx-solo-lector">{F.proyecto.etiqueta}</legend>
        {opciones.map((p) => {
          const texto = `${p.codigo ?? ''} · ${p.nombre ?? ''}`;
          return (
            <label key={p.id} className="nx-opcion">
              <input
                type="radio"
                name="proyecto"
                value={p.id}
                checked={seleccion === p.id}
                onChange={() => p.id !== undefined && setSeleccion(p.id)}
              />
              {p.id === actual ? F.proyecto.actual(texto) : texto}
            </label>
          );
        })}
      </fieldset>
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alCancelar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={ocupado} disabled={seleccion === null || seleccion === actual}>
          {modo === 'cambiar' ? F.proyecto.cambiar : F.proyecto.empezar}
        </Boton>
      </div>
    </form>
  );
}

export function ElegirProyecto({
  modo,
  ...resto
}: {
  /** Null = cerrado. */
  modo: 'empezar' | 'cambiar' | null;
  proyectos: ProyectosParaFichar | undefined;
  ocupado: boolean;
  alElegir: (proyectoId: number) => void;
  alCancelar: () => void;
}) {
  return (
    <Dialogo
      abierto={modo !== null}
      titulo={modo === 'cambiar' ? F.proyecto.cambiarTitulo : F.proyecto.elegirTitulo}
      alCerrar={resto.alCancelar}
      acciones={null}
    >
      {modo !== null && <FormularioDeProyecto modo={modo} {...resto} />}
    </Dialogo>
  );
}
