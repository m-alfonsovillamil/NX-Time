/**
 * Los cuatro componentes que necesitan dos pantallas.
 *
 * Están juntos en un fichero a propósito: cada uno son quince líneas y
 * repartirlos en cuatro ficheros con su import cada uno solo añadiría
 * ceremonia. Cuando alguno crezca o aparezca el quinto, se separan.
 *
 * Todos se pintan con los tokens de `estilos/tokens.css`, que se generan del
 * tema de la app Android: el color de un botón no puede tener dos verdades.
 */

import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';

import { T } from '../i18n/es';

interface BotonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: 'primario' | 'secundario' | 'texto';
  ocupado?: boolean;
}

export function Boton({ variante = 'primario', ocupado = false, children, ...resto }: BotonProps) {
  return (
    <button
      {...resto}
      className={`nx-boton nx-boton--${variante}`}
      disabled={resto.disabled === true || ocupado}
      // Un botón deshabilitado deja de anunciar por qué lo está; `aria-busy`
      // es lo que le dice a un lector de pantalla que hay algo en marcha.
      aria-busy={ocupado}
    >
      {children}
    </button>
  );
}

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  etiqueta: string;
  error?: string | undefined;
}

export function Campo({ etiqueta, error, id, ...resto }: CampoProps) {
  const idError = error !== undefined ? `${id}-error` : undefined;
  return (
    <div className="nx-campo">
      <label htmlFor={id}>{etiqueta}</label>
      <input
        {...resto}
        id={id}
        aria-invalid={error !== undefined}
        // Sin esto el mensaje se ve pero no se lee: un lector de pantalla no
        // tiene forma de saber que ese texto explica este campo.
        aria-describedby={idError}
      />
      {error !== undefined && (
        <span className="nx-campo__error" id={idError}>
          {error}
        </span>
      )}
    </div>
  );
}

export function Cargando({ texto = T.app.cargando }: { texto?: string }) {
  return (
    <p className="nx-cargando" role="status">
      {texto}
    </p>
  );
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
