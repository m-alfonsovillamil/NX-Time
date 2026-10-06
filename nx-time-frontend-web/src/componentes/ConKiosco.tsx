/**
 * La hora de entrada de una jornada y, si se abrió en un kiosco, en cuál
 * (ADR 033). Para los historiales: quien revisa ve de un vistazo qué fichajes
 * no se hicieron desde la propia sesión.
 */

import { kiosco as K } from '../i18n/es/kiosco';
import { Insignia } from './Basicos';

export function ConKiosco({ hora, kiosco }: { hora: string; kiosco: string | undefined }) {
  if (kiosco === undefined) return <>{hora}</>;
  return (
    <span className="nx-con-distintivo">
      {hora} <Insignia>{K.distintivo(kiosco)}</Insignia>
    </span>
  );
}
