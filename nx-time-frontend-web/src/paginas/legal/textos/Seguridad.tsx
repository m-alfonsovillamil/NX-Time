/** La política de divulgación de vulnerabilidades. La misma que SECURITY.md del repositorio. */

import { TITULAR } from '../../../legal/titular';

export function Seguridad() {
  return (
    <>
      <h2>Cómo avisar</h2>
      <p>
        Si encuentras un fallo de seguridad en {TITULAR.producto} (la API, la web o la app de Android), escríbenos a{' '}
        <a href={`mailto:${TITULAR.correoSeguridad}`}>{TITULAR.correoSeguridad}</a> y no lo publiques hasta que esté
        arreglado. Cuéntanos qué has encontrado y dónde, cómo reproducirlo y qué crees que podría hacer alguien con él.
      </p>

      <h2>Lo que hacemos</h2>
      <ul>
        <li>Te contestamos en 3 días laborables como mucho.</li>
        <li>Te tenemos al tanto de lo que hacemos y te avisamos cuando esté arreglado.</li>
        <li>Si lo quieres, te nombramos al publicar el arreglo.</li>
        <li>No emprendemos ninguna acción contra quien investiga de buena fe siguiendo estas normas.</li>
      </ul>

      <h2>Lo que te pedimos</h2>
      <ul>
        <li>
          Prueba con tu propia cuenta o con una empresa de prueba que registres tú. No accedas a datos de otras personas
          ni los cambies; si das con alguno sin querer, para y avísanos.
        </li>
        <li>Nada que degrade el servicio para los demás: ni pruebas de carga, ni denegación de servicio, ni correo masivo.</li>
        <li>Nada de ingeniería social, ni contra personas ni contra nuestros proveedores.</li>
      </ul>

      <h2>Qué versiones</h2>
      <p>La que está en producción y la última versión publicada de la app.</p>

      <h2>Si se está aprovechando</h2>
      <p>
        Si una vulnerabilidad de la app se está explotando de verdad, además de arreglarla la notificamos como pide el
        Reglamento (UE) 2024/2847 de ciberresiliencia: alerta temprana en 24 horas, notificación en 72 horas e informe
        final en 14 días.
      </p>
      <p>
        La versión legible por máquinas de esta página está en{' '}
        <a href="/.well-known/security.txt">/.well-known/security.txt</a>.
      </p>
    </>
  );
}
