/**
 * Las condiciones del servicio (condiciones generales de la contratación, Ley
 * 7/1998). Las acepta quien registra la empresa, en su nombre. Cambiarlas es
 * subir application.legal.version: se les piden de nuevo a los ADMIN.
 */

import { Link } from 'react-router';

import { TITULAR } from '../../../legal/titular';

export function Condiciones() {
  const P = TITULAR.producto;
  const correo = <a href={`mailto:${TITULAR.correoPrivacidad}`}>{TITULAR.correoPrivacidad}</a>;
  return (
    <>
      <p>
        Estas condiciones regulan el uso de {P} entre {TITULAR.nombre} (NIF {TITULAR.nif}, «el titular») y la empresa o
        profesional que lo registra («la empresa»). Quien registra la empresa declara que es mayor de edad y que puede
        obligarla. Junto con el <Link to="/legal/encargo">contrato de encargo del tratamiento</Link>, que forma parte de
        ellas, es todo lo que se acuerda.
      </p>

      <h2>1. Qué es el servicio</h2>
      <p>
        {P} es una aplicación web y Android para llevar el registro de jornada de una plantilla: fichar, corregir
        fichajes con su rastro, pedir y aprobar ausencias, cuadrantes, informes, la firma mensual del registro,
        kioscos para fichar en el centro de trabajo y un canal interno de denuncias, entre otras funciones. Se ofrece a
        empresas y profesionales, no a consumidores.
      </p>

      <h2>2. Alta</h2>
      <ul>
        <li>
          La empresa se registra con el correo de quien la va a administrar, que tiene que confirmar. Después,{' '}
          <strong>el titular revisa y aprueba el alta</strong>; hasta entonces nadie de ella puede entrar. El titular
          puede rechazar un alta sin dar motivo, y entonces borra lo que se guardó al registrarse.
        </li>
        <li>La empresa da datos ciertos y los mantiene al día, en particular el correo de sus administradores.</li>
      </ul>

      <h2>3. Precio</h2>
      <p>
        El servicio es <strong>gratuito</strong>. Si algún día dejara de serlo, se avisaría con al menos dos meses de
        antelación y nada se cobraría sin que la empresa lo acepte expresamente.
      </p>

      <h2>4. Lo que le toca a la empresa</h2>
      <p>
        {P} es una herramienta. <strong>Quien está obligada a cumplir la ley laboral y de protección de datos es la
        empresa</strong>, y en particular:
      </p>
      <ul>
        <li>
          llevar el registro de jornada que pide el artículo 34.9 del Estatuto de los Trabajadores, conservarlo cuatro
          años y ponerlo a disposición de su plantilla, de la representación legal y de la Inspección de Trabajo;
        </li>
        <li>
          informar a su plantilla de cómo trata sus datos (RGPD, art. 13) y del uso del registro, consultar a la
          representación legal cuando proceda y respetar el derecho a la desconexión digital (art. 88 de la Ley
          Orgánica 3/2018);
        </li>
        <li>
          valorar si necesita una evaluación de impacto (RGPD, art. 35), sobre todo si usa el canal de denuncias o
          guarda motivos de ausencia que revelen datos de salud;
        </li>
        <li>
          si tiene 50 o más personas en plantilla, tener un sistema interno de información conforme a la Ley 2/2023 y
          designar a su responsable: el canal de {P} puede ser la herramienta, pero no la exime de lo demás;
        </li>
        <li>
          dar de alta solo a quien trabaje para ella, con el rol que le corresponda, y darle de baja cuando deje de
          hacerlo;
        </li>
        <li>no poner un kiosco donde pueda usarlo el público;</li>
        <li>
          usar el servicio de forma lícita: nada de guardar datos que no tengan que ver con la relación laboral, ni de
          usarlo para vigilar a la plantilla más allá de lo que la ley permite.
        </li>
      </ul>
      <p>
        Las cuentas son personales. Cada persona guarda su contraseña y su código del kiosco, y la empresa avisa al
        titular en cuanto sepa de un acceso indebido.
      </p>

      <h2>5. Lo que hace el titular</h2>
      <ul>
        <li>Prestar el servicio con diligencia y mantenerlo al día, también en seguridad.</li>
        <li>
          Tratar los datos de la plantilla solo para prestar el servicio y como dice el contrato de encargo, y avisar
          sin dilación de cualquier brecha que les afecte.
        </li>
        <li>Avisar con antelación de los cambios que afecten a la empresa.</li>
      </ul>

      <h2>6. Lo que no garantiza</h2>
      <p>
        Por ser gratuito, el servicio se presta <strong>tal como es y según disponibilidad</strong>. El titular hace lo
        razonable para que funcione y no pierda datos (copias, verificación diaria de la integridad del registro), pero
        no garantiza que esté siempre disponible, ni que no tenga errores, ni que sirva para un fin concreto. Puede
        haber paradas para mantenimiento o por fallos de los proveedores.
      </p>
      <p>
        <strong>La empresa debe tener una forma de llevar el registro si el servicio no está disponible</strong> y
        descargar con regularidad los informes que necesite conservar.
      </p>

      <h2>7. Responsabilidad</h2>
      <p>
        El titular responde de los daños que cause por dolo o culpa grave, y de lo que la ley no permita excluir. Fuera
        de eso, y siendo un servicio gratuito, no responde de daños indirectos, lucro cesante, sanciones impuestas a la
        empresa por incumplir sus propias obligaciones, ni de la pérdida de datos que la empresa podía haber evitado
        descargándolos. En materia de protección de datos, cada parte responde como dice el RGPD (art. 82).
      </p>

      <h2>8. Suspensión</h2>
      <p>
        El titular puede suspender el acceso de una empresa si incumple estas condiciones, si lo ordena una autoridad
        o si es necesario para proteger el servicio o a terceros. Avisará a sus administradores y, salvo que no pueda
        esperar, antes de hacerlo. Mientras dure la suspensión, los datos se conservan.
      </p>

      <h2>9. Baja y devolución de los datos</h2>
      <ul>
        <li>La empresa puede darse de baja cuando quiera, escribiendo a {correo} desde el correo de un administrador.</li>
        <li>
          Antes de borrar nada, el titular le entrega una copia completa de sus datos en formatos que se pueden leer
          (los informes de cada persona, la traza de auditoría y las firmas). <strong>La empresa sigue obligada a
          conservar el registro de jornada cuatro años</strong>: tiene que guardar esa copia.
        </li>
        <li>
          Treinta días después de entregarla, el titular borra los datos de la empresa y de su plantilla, salvo lo que
          una ley le obligue a conservar, y lo confirma por escrito.
        </li>
        <li>
          El titular puede dejar de prestar el servicio avisando con al menos tres meses de antelación, con el mismo
          derecho a la copia.
        </li>
      </ul>

      <h2>10. Cambios en estas condiciones</h2>
      <p>
        Si cambian, se publica la versión nueva con su fecha y se pide a los administradores que la acepten la próxima
        vez que entren. Si un cambio importante no le conviene a la empresa, puede darse de baja como en el punto 9.
      </p>

      <h2>11. Ley y tribunales</h2>
      <p>
        Estas condiciones se rigen por la ley española. Para cualquier controversia, las partes se someten a los
        juzgados y tribunales del domicilio del titular, salvo que la ley disponga otra cosa.
      </p>
    </>
  );
}
