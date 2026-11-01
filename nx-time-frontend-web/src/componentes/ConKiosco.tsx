/**
 * La hora de entrada de una jornada y, si se abrió en un kiosco, en cuál
 * (ADR 033), o si se fichó sin conexión (ADR 054). Para los historiales: quien
 * revisa ve de un vistazo qué fichajes no se hicieron desde la propia sesión.
 */

import { kiosco as K } from '../i18n/es/kiosco';
import { Insignia } from './Basicos';

export function ConKiosco({
  hora,
  kiosco,
  origen,
}: {
  hora: string;
  kiosco: string | undefined;
  origen?: string | undefined;
}) {
  if (kiosco !== undefined) {
    return (
      <span className="nx-con-distintivo">
        {hora} <Insignia>{K.distintivo(kiosco)}</Insignia>
      </span>
    );
  }
  if (origen === 'APP_SIN_CONEXION') {
    return (
      <span className="nx-con-distintivo">
        {hora} <Insignia>{K.distintivoSinConexion}</Insignia>
      </span>
    );
  }
  return <>{hora}</>;
}
