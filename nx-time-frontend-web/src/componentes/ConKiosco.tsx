/**
 * La hora de entrada de una jornada y, si se abrió en un kiosco, en cuál
 * (ADR 033), o si se fichó sin conexión (ADR 054), y si se trabajó a distancia
 * (ADR 055). Para los historiales: quien revisa ve de un vistazo qué fichajes
 * no se hicieron desde la propia sesión.
 */

import { kiosco as K } from '../i18n/es/kiosco';
import { Insignia } from './Basicos';

export function ConKiosco({
  hora,
  kiosco,
  origen,
  modalidad,
}: {
  hora: string;
  kiosco: string | undefined;
  origen?: string | undefined;
  modalidad?: string | undefined;
}) {
  const distintivos: string[] = [];
  if (kiosco !== undefined) distintivos.push(K.distintivo(kiosco));
  else if (origen === 'APP_SIN_CONEXION') distintivos.push(K.distintivoSinConexion);
  if (modalidad === 'A_DISTANCIA') distintivos.push(K.distintivoADistancia);
  if (distintivos.length === 0) return <>{hora}</>;
  return (
    <span className="nx-con-distintivo">
      {hora}
      {distintivos.map((texto) => (
        <Insignia key={texto}>{texto}</Insignia>
      ))}
    </span>
  );
}
