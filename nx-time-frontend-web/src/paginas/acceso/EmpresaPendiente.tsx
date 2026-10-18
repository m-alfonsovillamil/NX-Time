/**
 * Lo que ve quien acaba de registrar su empresa mientras el operador no la
 * aprueba (ADR 046): tras confirmar el correo, o tras registrarla con Google o
 * Microsoft. El servidor responde 423 con el texto, que se enseña tal cual.
 */

import { Link } from 'react-router';

import { Aviso } from '../../componentes/Basicos';
import { T } from '../../i18n/es';
import { MarcoDeAcceso } from './MarcoDeAcceso';

const P = T.pendiente;

export function EmpresaPendiente({ mensaje }: { mensaje: string }) {
  return (
    <MarcoDeAcceso>
      <div className="nx-tarjeta nx-formulario" role="status">
        <header>
          <h1>{P.titulo}</h1>
        </header>
        <Aviso>{mensaje}</Aviso>
        <p className="nx-sutil">{P.siguiente}</p>
        <Link className="nx-enlace" to="/">
          {P.volver}
        </Link>
      </div>
    </MarcoDeAcceso>
  );
}

/** Si el error de la API es el 423 de una empresa que no está activa. */
export function esEmpresaNoActiva(error: unknown): error is Error & { status: number } {
  return error instanceof Error && 'status' in error && (error as { status: unknown }).status === 423;
}
