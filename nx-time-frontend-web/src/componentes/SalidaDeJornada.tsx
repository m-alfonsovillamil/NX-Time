/**
 * La hora de salida de una jornada y, si la cerró el sistema, que fue así.
 *
 * El cierre de las 3:00 pone una salida a las jornadas que nadie cerró, y esa
 * hora es un tope, no un dato: sin el distintivo, en el historial es una
 * jornada de dieciséis horas como otra cualquiera. Con él, quien la ve sabe que
 * hay que pedir (o aprobar) una corrección.
 */

import { historial as H } from '../i18n/es/historial';
import { Insignia } from './Basicos';

export function SalidaDeJornada({ salida, cerradaPorElSistema }: { salida: string; cerradaPorElSistema: boolean | undefined }) {
  if (cerradaPorElSistema !== true) return <>{salida}</>;
  return (
    <span className="nx-con-distintivo">
      {salida} <Insignia tono="aviso">{H.cerradaPorElSistema}</Insignia>
    </span>
  );
}
