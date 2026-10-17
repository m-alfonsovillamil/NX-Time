/**
 * La política de privacidad de lo que el titular trata COMO RESPONSABLE (RGPD,
 * art. 13). Lo de la plantilla de cada empresa lo trata como encargado, por
 * cuenta de ella: eso va en el contrato de encargo, y a sus empleados les
 * informa su empresa.
 */

import { Link } from 'react-router';

import { TITULAR } from '../../../legal/titular';

export function Privacidad() {
  const correo = <a href={`mailto:${TITULAR.correoPrivacidad}`}>{TITULAR.correoPrivacidad}</a>;
  return (
    <>
      <h2>Lo primero: ¿eres empleado de una empresa que usa {TITULAR.producto}?</h2>
      <p>
        Entonces <strong>el responsable de tus datos es tu empresa</strong>, no nosotros. Tus fichajes, ausencias y el
        resto de lo que guardas aquí los tratamos por encargo suyo y siguiendo sus instrucciones, como dice el{' '}
        <Link to="/legal/encargo">contrato de encargo</Link>. Para saber cómo los usa, cuánto los guarda o para
        ejercer tus derechos, dirígete a ella: es quien tiene que informarte. Si nos escribes a nosotros, le pasaremos
        tu petición y te avisaremos de que lo hemos hecho.
      </p>
      <p>Esta página es sobre los datos que tratamos <strong>por nuestra cuenta</strong>.</p>

      <h2>Quién es el responsable</h2>
      <p>
        {TITULAR.nombre}, NIF {TITULAR.nif}, {TITULAR.domicilio}. Correo: {correo}.
      </p>

      <h2>Qué datos tratamos, para qué y por qué podemos</h2>

      <h3>1. Quien registra y administra una empresa</h3>
      <p>
        Al registrar una empresa nos das tu nombre, tus apellidos, tu correo y el nombre de la empresa, y guardamos
        cuándo aceptaste las condiciones y qué versión. Si te registras con Google o Microsoft, nos llega de ellos el
        identificador de tu cuenta y tu correo (nada más: ni tu contraseña ni tus contactos).
      </p>
      <ul>
        <li>
          <strong>Para:</strong> darte de alta, comprobar que el correo es tuyo, decidir si aprobamos el alta, avisarte
          de lo que pase con tu empresa en el servicio (aprobación, suspensión, cambios en las condiciones) y atender lo
          que nos pidas.
        </li>
        <li>
          <strong>Por qué podemos:</strong> es necesario para prestar el servicio a la empresa que representas
          (contrato, art. 6.1.b RGPD) y tenemos un interés legítimo en saber con quién tratamos y en poder contactar
          con quien administra cada empresa (art. 6.1.f).
        </li>
        <li>
          <strong>Cuánto tiempo:</strong> mientras tu empresa esté dada de alta y tú la administres. Después, bloqueados
          el tiempo en que puedan pedirse responsabilidades (hasta seis años) y luego borrados. Si no confirmas el
          correo, el registro se borra a los dos días; si rechazamos el alta, enseguida.
        </li>
      </ul>

      <h3>2. Quien usa la web o la app: datos técnicos y de seguridad</h3>
      <p>
        Al usar el servicio, el servidor registra datos técnicos: la dirección IP, la hora, la ruta pedida, el
        navegador o la versión de la app, y los intentos de entrada fallidos. Si la app o el servidor fallan, se manda
        un informe del error a Sentry: sin tu nombre, con los correos tapados antes de salir y, como mucho, el número
        interno de tu cuenta.
      </p>
      <ul>
        <li>
          <strong>Para:</strong> que el servicio funcione, proteger las cuentas (frenar a quien prueba contraseñas,
          detectar abusos) y arreglar fallos.
        </li>
        <li>
          <strong>Por qué podemos:</strong> interés legítimo en la seguridad y el buen funcionamiento del servicio
          (art. 6.1.f RGPD y considerando 49). En la app puedes apagar el informe de errores desde Ajustes.
        </li>
        <li>
          <strong>Cuánto tiempo:</strong> los registros del servidor, lo que los guarde el proveedor de alojamiento
          (días); los informes de errores, hasta 90 días.
        </li>
      </ul>

      <h3>3. Quien nos escribe</h3>
      <p>
        Si nos escribes (a {correo}, o a <a href={`mailto:${TITULAR.correoSeguridad}`}>{TITULAR.correoSeguridad}</a>{' '}
        para avisar de un fallo), tratamos tu correo y lo que nos cuentes para contestarte. Por interés legítimo en
        atenderte, o porque la ley nos obliga cuando ejerces un derecho. Lo guardamos mientras dure la conversación y,
        si es el ejercicio de un derecho o un aviso de seguridad, hasta tres años para poder demostrar cómo lo
        atendimos.
      </p>

      <h3>4. El operador del servicio</h3>
      <p>
        Quien mantiene el servicio puede ver, de cada empresa, cifras de uso y el contacto de sus administradores, para
        aprobarla, suspenderla o escribirle. Cada vez que lo hace queda registrado, sin poder borrarse, con quién y
        cuándo. No ve los fichajes ni los datos de la plantilla.
      </p>

      <h2>A quién se los damos</h2>
      <p>
        A nadie para que los use para lo suyo. Los usan, por nuestra cuenta, los proveedores con los que prestamos el
        servicio (alojamiento, base de datos, correo, avisos, informes de errores, entrar con Google o Microsoft): están
        en la <Link to="/legal/subencargados">lista de subencargados</Link>. Y los daríamos a una autoridad si una ley
        nos obliga.
      </p>

      <h2>Fuera de la Unión Europea</h2>
      <p>
        Los datos se guardan en la Unión Europea (Frankfurt). Algunos proveedores son empresas de Estados Unidos que
        pueden acceder a ellos, o tratar algunos allí (Google para los avisos, Sentry, Cloudflare). Para eso se acogen
        al Marco de Privacidad de Datos UE-EE. UU., que la Comisión Europea considera adecuado, o a las cláusulas
        contractuales tipo de la Comisión.
      </p>

      <h2>Tus derechos</h2>
      <p>
        Puedes pedirnos acceder a tus datos, corregirlos, borrarlos, llevártelos (portabilidad), limitar su uso u
        oponerte a que los tratemos por interés legítimo. Escribe a {correo} desde el correo de tu cuenta, o
        identificándote. Te contestamos en un mes como mucho.
      </p>
      <p>
        Si crees que no hemos tratado bien tus datos, puedes reclamar ante la Agencia Española de Protección de Datos (
        <a href="https://www.aepd.es">www.aepd.es</a>), aunque te agradecemos que antes nos lo cuentes a nosotros.
      </p>

      <h2>Lo que no hacemos</h2>
      <ul>
        <li>No vendemos datos ni los usamos para publicidad.</li>
        <li>No tomamos decisiones automatizadas sobre ti.</li>
        <li>No registramos tu ubicación: ni la web ni la app la piden.</li>
      </ul>
    </>
  );
}
