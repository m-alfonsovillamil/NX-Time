/**
 * Los subencargados (RGPD, art. 28.2 y 28.4). Añadir o cambiar uno se avisa a
 * los administradores con 15 días (contrato de encargo, punto 3.5).
 *
 * Las garantías de transferencia son las que declara cada proveedor; antes de
 * cambiar esta lista, comprobarlas en https://www.dataprivacyframework.gov/list.
 */

import { TITULAR } from '../../../legal/titular';

interface Subencargado {
  quien: string;
  para: string;
  donde: string;
  garantia: string;
}

const LISTA: readonly Subencargado[] = [
  {
    quien: 'Render Services, Inc. (EE. UU.)',
    para: 'Alojamiento del servidor y de la web',
    donde: 'Frankfurt (UE)',
    garantia: 'Marco de Privacidad de Datos UE-EE. UU. y cláusulas contractuales tipo',
  },
  {
    quien: 'Neon (Databricks, Inc., EE. UU.)',
    para: 'Base de datos',
    donde: 'AWS Frankfurt (UE)',
    garantia: 'Marco de Privacidad de Datos UE-EE. UU. y cláusulas contractuales tipo',
  },
  {
    quien: 'Cloudflare, Inc. (EE. UU.)',
    para: 'Dominio (DNS) y reenvío del correo de contacto',
    donde: 'Red mundial',
    garantia: 'Marco de Privacidad de Datos UE-EE. UU. y cláusulas contractuales tipo',
  },
  {
    quien: 'Sendinblue SAS · Brevo (Francia)',
    para: 'Envío de los correos del servicio (códigos, avisos)',
    donde: 'UE',
    garantia: 'No hay transferencia',
  },
  {
    quien: 'Google LLC · Firebase Cloud Messaging (EE. UU.)',
    para: 'Entrega de los avisos al móvil y al navegador, si se activan',
    donde: 'Mundial',
    garantia: 'Marco de Privacidad de Datos UE-EE. UU. y cláusulas contractuales tipo',
  },
  {
    quien: 'Functional Software, Inc. · Sentry (EE. UU.)',
    para: 'Informes de errores de la app y del servidor, sin correos',
    donde: 'Frankfurt (UE)',
    garantia: 'Marco de Privacidad de Datos UE-EE. UU. y cláusulas contractuales tipo',
  },
  {
    quien: 'Google LLC y Microsoft Corporation (EE. UU.)',
    para: 'Entrar con una cuenta de Google o de Microsoft, solo si la persona lo elige',
    donde: 'Mundial',
    garantia: 'Marco de Privacidad de Datos UE-EE. UU.',
  },
];

export function Subencargados() {
  return (
    <>
      <p>
        Son los proveedores con los que {TITULAR.producto} presta el servicio y que, para hacerlo, tratan datos
        personales por cuenta del titular. Cada uno queda obligado a lo mismo que el titular en el contrato de encargo.
        Si cambia esta lista, se avisa antes a los administradores de cada empresa.
      </p>
      <div className="nx-legal__tabla">
        <table>
          <thead>
            <tr>
              <th scope="col">Proveedor</th>
              <th scope="col">Para qué</th>
              <th scope="col">Dónde están los datos</th>
              <th scope="col">Garantía si salen de la UE</th>
            </tr>
          </thead>
          <tbody>
            {LISTA.map((sub) => (
              <tr key={sub.quien}>
                <td>{sub.quien}</td>
                <td>{sub.para}</td>
                <td>{sub.donde}</td>
                <td>{sub.garantia}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p>
        Las certificaciones en el Marco de Privacidad de Datos pueden consultarse en la{' '}
        <a href="https://www.dataprivacyframework.gov/list">lista oficial del Departamento de Comercio de EE. UU.</a>
      </p>
    </>
  );
}
