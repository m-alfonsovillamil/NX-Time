/** Aviso legal: la información del art. 10 de la LSSI, y el contacto como prestador de alojamiento (DSA). */

import { Link } from 'react-router';

import { TITULAR } from '../../../legal/titular';

export function AvisoLegal() {
  return (
    <>
      <h2>Quién presta el servicio</h2>
      <p>
        {TITULAR.producto} (<a href={TITULAR.web}>{TITULAR.web.replace('https://', '')}</a> y su app para Android) es
        un servicio de registro de jornada para empresas que presta:
      </p>
      <dl className="nx-legal__datos">
        <dt>Titular</dt>
        <dd>{TITULAR.nombre}</dd>
        <dt>NIF</dt>
        <dd>{TITULAR.nif}</dd>
        <dt>Domicilio</dt>
        <dd>{TITULAR.domicilio}</dd>
        <dt>Correo electrónico</dt>
        <dd>
          <a href={`mailto:${TITULAR.correoPrivacidad}`}>{TITULAR.correoPrivacidad}</a>
        </dd>
      </dl>
      <p>
        Es la información que pide el artículo 10 de la Ley 34/2002, de servicios de la sociedad de la información y
        de comercio electrónico (LSSI). El servicio es gratuito y no requiere autorización administrativa previa.
      </p>

      <h2>Para quién es</h2>
      <p>
        Para empresas y profesionales que necesitan llevar el registro de jornada de su plantilla. No es un servicio
        para consumidores. Lo que la empresa acepta al registrarse está en las{' '}
        <Link to="/legal/condiciones">condiciones del servicio</Link>, y cómo tratamos los datos de su plantilla, en el{' '}
        <Link to="/legal/encargo">contrato de encargo del tratamiento</Link>.
      </p>

      <h2>Punto de contacto y avisos de contenido ilícito</h2>
      <p>
        Como servicio que guarda información de sus usuarios, el Reglamento (UE) 2022/2065 de servicios digitales pide
        un punto de contacto para las autoridades y para quien use el servicio, y una forma de avisar de contenido
        ilícito. Los dos son el correo de arriba, en español o en inglés.
      </p>
      <p>Si avisas de un contenido que crees ilícito, dinos, por favor:</p>
      <ul>
        <li>por qué crees que es ilícito;</li>
        <li>dónde está, con todo el detalle que puedas (la empresa, la pantalla, la fecha);</li>
        <li>tu nombre y tu correo, salvo que se trate de abusos sexuales a menores;</li>
        <li>que lo haces de buena fe y que, hasta donde sabes, lo que cuentas es cierto.</li>
      </ul>
      <p>
        Te confirmaremos que lo hemos recibido, lo revisaremos con diligencia y te diremos qué hemos decidido. Ten en
        cuenta que el contenido de cada empresa (sus fichajes, sus ausencias, su canal de denuncias) lo gestiona ella:
        si el aviso es sobre eso, lo normal es que se lo traslademos.
      </p>

      <h2>Fallos de seguridad</h2>
      <p>
        Si encuentras una vulnerabilidad, escríbenos a{' '}
        <a href={`mailto:${TITULAR.correoSeguridad}`}>{TITULAR.correoSeguridad}</a>. Cómo lo gestionamos está en{' '}
        <Link to="/legal/seguridad">avisar de un fallo de seguridad</Link>.
      </p>

      <h2>Propiedad intelectual</h2>
      <p>
        El código de {TITULAR.producto} se publica con licencia MIT. Los datos que cada empresa guarda en el servicio
        son suyos; el titular no adquiere ningún derecho sobre ellos.
      </p>

      <h2>Ley aplicable</h2>
      <p>
        Este aviso se rige por la ley española. Para cualquier controversia con una empresa usuaria, lo que digan las
        condiciones del servicio.
      </p>
    </>
  );
}
