/**
 * Los componentes pequeños: botón, campos, tarjeta, insignia y aviso.
 *
 * Están juntos a propósito: cada uno son quince líneas y repartirlos en un
 * fichero por componente solo añadiría ceremonia. Los que tienen lógica propia
 * (tabla, diálogo, pestañas, estados de carga) van aparte.
 *
 * Todos se pintan con los tokens de `estilos/tokens.css`, que se generan del
 * tema de la app Android: el color de un botón no puede tener dos verdades. Y
 * todos son HTML nativo por debajo (ADR 029): un `<select>` de verdad ya sabe
 * comportarse con el teclado, con un lector de pantalla y en el móvil.
 */

import {
  useId,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';

import { Icono, type NombreIcono } from './Icono';

interface BotonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: 'primario' | 'secundario' | 'texto' | 'peligro';
  ocupado?: boolean;
  /** Un icono delante del texto (descargar, imprimir). Decorativo: el texto dice lo que hace. */
  icono?: NombreIcono;
}

export function Boton({ variante = 'primario', ocupado = false, icono, children, className, ...resto }: BotonProps) {
  const clases = ['nx-boton', `nx-boton--${variante}`];
  if (icono !== undefined) clases.push('nx-boton--con-icono');
  if (className !== undefined) clases.push(className);
  return (
    <button
      type="button"
      {...resto}
      className={clases.join(' ')}
      disabled={resto.disabled === true || ocupado}
      // Un botón deshabilitado deja de anunciar por qué lo está; `aria-busy`
      // es lo que le dice a un lector de pantalla que hay algo en marcha.
      aria-busy={ocupado}
    >
      {icono !== undefined && <Icono nombre={icono} tamano={20} />}
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* Campos                                                              */
/* ------------------------------------------------------------------ */

interface Etiquetado {
  id: string;
  etiqueta: string;
  error?: string | undefined;
  ayuda?: string | undefined;
}

/**
 * Lo que comparten los tres campos: la etiqueta y el error enlazados al control.
 *
 * Sin `aria-describedby` el mensaje se ve pero no se lee: un lector de
 * pantalla no tiene forma de saber que ese texto explica ese campo.
 */
function describir({ id, error, ayuda }: Etiquetado) {
  const ids = [ayuda !== undefined ? `${id}-ayuda` : null, error !== undefined ? `${id}-error` : null]
    .filter((x) => x !== null)
    .join(' ');
  return { 'aria-invalid': error !== undefined, 'aria-describedby': ids === '' ? undefined : ids };
}

function Envoltura({ id, etiqueta, error, ayuda, children }: Etiquetado & { children: ReactNode }) {
  return (
    <div className="nx-campo">
      <label htmlFor={id}>{etiqueta}</label>
      {children}
      {ayuda !== undefined && (
        <span className="nx-campo__ayuda" id={`${id}-ayuda`}>
          {ayuda}
        </span>
      )}
      {error !== undefined && (
        <span className="nx-campo__error" id={`${id}-error`}>
          {error}
        </span>
      )}
    </div>
  );
}

type CampoProps = Etiquetado & Omit<InputHTMLAttributes<HTMLInputElement>, 'id'>;

/**
 * Un `<input>` con su etiqueta. También para fechas (`type="date"`): el
 * selector nativo es el que mejor funciona en un móvil, y el valor ya llega
 * como `aaaa-mm-dd`, que es lo que espera la API para un `LocalDate`.
 */
export function Campo({ id, etiqueta, error, ayuda, ...resto }: CampoProps) {
  return (
    <Envoltura id={id} etiqueta={etiqueta} error={error} ayuda={ayuda}>
      <input {...resto} id={id} {...describir({ id, etiqueta, error, ayuda })} />
    </Envoltura>
  );
}

type AreaDeTextoProps = Etiquetado & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'>;

export function AreaDeTexto({ id, etiqueta, error, ayuda, rows = 3, ...resto }: AreaDeTextoProps) {
  return (
    <Envoltura id={id} etiqueta={etiqueta} error={error} ayuda={ayuda}>
      <textarea {...resto} rows={rows} id={id} {...describir({ id, etiqueta, error, ayuda })} />
    </Envoltura>
  );
}

interface OpcionDeSelector {
  valor: string;
  texto: string;
  /** Las opciones seguidas con el mismo grupo van juntas en un `<optgroup>`. */
  grupo?: string;
}

type SelectorProps = Etiquetado &
  Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> & {
    opciones: readonly OpcionDeSelector[];
  };

/** Las opciones partidas en tramos seguidos del mismo grupo (o sin grupo). */
function tramos(opciones: readonly OpcionDeSelector[]): { grupo: string | undefined; opciones: OpcionDeSelector[] }[] {
  const resultado: { grupo: string | undefined; opciones: OpcionDeSelector[] }[] = [];
  for (const opcion of opciones) {
    const ultimo = resultado.at(-1);
    if (ultimo !== undefined && ultimo.grupo === opcion.grupo) ultimo.opciones.push(opcion);
    else resultado.push({ grupo: opcion.grupo, opciones: [opcion] });
  }
  return resultado;
}

export function Selector({ id, etiqueta, error, ayuda, opciones, ...resto }: SelectorProps) {
  const pintar = (o: OpcionDeSelector) => (
    <option key={o.valor} value={o.valor}>
      {o.texto}
    </option>
  );
  return (
    <Envoltura id={id} etiqueta={etiqueta} error={error} ayuda={ayuda}>
      <select {...resto} id={id} {...describir({ id, etiqueta, error, ayuda })}>
        {tramos(opciones).map((tramo, i) =>
          tramo.grupo === undefined ? (
            tramo.opciones.map(pintar)
          ) : (
            <optgroup key={`${tramo.grupo}-${i}`} label={tramo.grupo}>
              {tramo.opciones.map(pintar)}
            </optgroup>
          ),
        )}
      </select>
    </Envoltura>
  );
}

/* ------------------------------------------------------------------ */
/* Superficies                                                         */
/* ------------------------------------------------------------------ */

export function Tarjeta({
  titulo,
  icono,
  descripcion,
  acciones,
  children,
  className,
  id,
}: {
  titulo?: string;
  /** El icono del título, en su recuadro del color de la zona. */
  icono?: NombreIcono;
  /** Una frase bajo el título: qué es lo que hay en la tarjeta. */
  descripcion?: string;
  acciones?: ReactNode;
  children?: ReactNode;
  className?: string;
  /** Para enlazarla desde un índice (`#id`). */
  id?: string;
}) {
  const idTitulo = useId();
  return (
    <section
      id={id}
      className={`nx-tarjeta${className !== undefined ? ` ${className}` : ''}`}
      // Con título, la tarjeta es una región con nombre: un lector de pantalla
      // puede saltar de una a otra.
      aria-labelledby={titulo !== undefined ? idTitulo : undefined}
    >
      {(titulo !== undefined || acciones !== undefined) && (
        <header className="nx-tarjeta__cabecera">
          {icono !== undefined && (
            <span className="nx-tarjeta__icono">
              <Icono nombre={icono} />
            </span>
          )}
          {titulo !== undefined && (
            <div className="nx-tarjeta__titulo">
              <h2 id={idTitulo}>{titulo}</h2>
              {descripcion !== undefined && <p className="nx-sutil">{descripcion}</p>}
            </div>
          )}
          {acciones}
        </header>
      )}
      {children}
    </section>
  );
}

/**
 * Un estado que tiene que verse antes que nada: «La traza está intacta», «La
 * traza NO está intacta». El color lo dice de lejos, como el de la jornada;
 * el icono y el texto lo dicen sin depender del color.
 */
export function Destacado({
  tono,
  icono,
  titulo,
  children,
  role,
}: {
  tono: 'bien' | 'mal' | 'neutro';
  icono: NombreIcono;
  titulo: string;
  children?: ReactNode;
  /** `status` si aparece como resultado de algo que se acaba de pedir. */
  role?: 'status' | 'alert';
}) {
  return (
    <div className={`nx-destacado nx-destacado--${tono}`} role={role}>
      <span className="nx-destacado__icono">
        <Icono nombre={icono} />
      </span>
      <div className="nx-destacado__texto">
        <strong>{titulo}</strong>
        {children}
      </div>
    </div>
  );
}

/**
 * Una explicación en puntos, cada uno con su icono: el «cómo funciona» que va
 * al lado de lo que se hace, en vez de un párrafo largo encima que nadie lee.
 */
export function Puntos({ puntos }: { puntos: readonly { icono: NombreIcono; titulo: string; texto: string }[] }) {
  return (
    <ol className="nx-puntos">
      {puntos.map((p) => (
        <li key={p.titulo}>
          <span className="nx-puntos__icono">
            <Icono nombre={p.icono} tamano={20} />
          </span>
          <div>
            <strong>{p.titulo}</strong>
            <p className="nx-sutil">{p.texto}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}

export type Tono = 'neutro' | 'exito' | 'aviso' | 'error' | 'info';

/**
 * El estado de algo en una palabra: «Pendiente», «Aprobada», «En disputa».
 *
 * Rellena con un tinte suave de su color (desde el 5/10/2026; antes era solo
 * un borde, y en una tabla se perdía entre las líneas de separación). El
 * texto sigue siendo el color de estado de `ColoresJornada`, pensado para
 * leerse sobre la superficie: el tinte es un 14 % de ese mismo color sobre
 * ella, y el contraste apenas baja (lo vigila axe en los dos temas).
 */
export function Insignia({ tono = 'neutro', children }: { tono?: Tono; children: ReactNode }) {
  return <span className={`nx-insignia nx-insignia--${tono}`}>{children}</span>;
}

/**
 * Un aviso de error.
 *
 * `role="alert"` para que se anuncie al aparecer: es la diferencia entre que
 * alguien que no ve la pantalla se entere de que el fichaje ha fallado, o siga
 * esperando.
 */
export function Aviso({ children }: { children: ReactNode }) {
  return (
    <p className="nx-aviso" role="alert">
      {children}
    </p>
  );
}
