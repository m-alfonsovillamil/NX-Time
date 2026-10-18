/**
 * El contrato de encargo del tratamiento (RGPD, art. 28.3). Lo acepta la
 * empresa al registrarse, junto con las condiciones. Si cambia algo de lo que
 * aquí se promete (una medida, un plazo), se sube application.legal.version.
 */

import { Link } from 'react-router';

import { TITULAR } from '../../../legal/titular';

export function Encargo() {
  const P = TITULAR.producto;
  const correo = <a href={`mailto:${TITULAR.correoPrivacidad}`}>{TITULAR.correoPrivacidad}</a>;
  return (
    <>
      <p>
        Al usar {P}, la empresa (<strong>la responsable</strong>) encarga a {TITULAR.nombre}, NIF {TITULAR.nif} (
        <strong>el encargado</strong>), que trate por su cuenta los datos personales de su plantilla y de las demás
        personas que use el servicio. Este contrato es el que pide el artículo 28.3 del Reglamento (UE) 2016/679
        (RGPD) y la Ley Orgánica 3/2018. Forma parte de las <Link to="/legal/condiciones">condiciones del servicio</Link>
        , dura lo mismo que ellas y se acepta en el mismo momento.
      </p>

      <h2>1. Objeto, naturaleza y finalidad</h2>
      <p>
        Prestar a la responsable el servicio {P}: guardar, organizar, consultar, calcular y comunicar los datos que ella
        y su plantilla introducen, con la única finalidad de que la empresa lleve el registro de jornada y gestione lo
        que el servicio permite (ausencias, correcciones, cuadrantes, horas extra, reparto de horas por proyecto,
        firma mensual del registro, kioscos, avisos, analítica de su plantilla, canal interno de denuncias y ofertas de
        empleo internas).
      </p>

      <h2>2. Qué datos y de quién</h2>
      <ul>
        <li>
          <strong>Personas:</strong> la plantilla de la empresa y quien la gestiona; candidatos a sus ofertas internas;
          quien usa su canal de denuncias, y las personas que se mencionen en él.
        </li>
        <li>
          <strong>Datos:</strong> identificativos y de contacto (nombre, apellidos, correo, fecha de nacimiento, foto
          si se sube), laborales (puesto, departamento, rol, jornada contratada, saldo de vacaciones, cuadrante),
          el registro de jornada (horas de entrada, salida y pausas, correcciones con su autor y su motivo, firmas),
          ausencias y sus motivos, horas extra, currículos, lo que se cuente en el canal de denuncias, y datos técnicos
          (IP y dispositivo con que se ficha, sesiones, avisos).
        </li>
        <li>
          <strong>Datos especialmente protegidos:</strong> el servicio no los pide, pero pueden aparecer en el motivo de
          una ausencia (por ejemplo, de salud) o en una denuncia. La responsable decide si los recoge y cómo.
        </li>
        <li>No se registra la ubicación.</li>
        <li>
          <strong>El canal de denuncias:</strong> el encargado solo aporta la herramienta. No recibe, no lee y no
          gestiona las comunicaciones: lo hace el Responsable del Sistema Interno de Información que designe la
          responsable (Ley 2/2023, art. 8) o, mientras no lo designe, sus administradores. El servicio da acuse de
          recibo al presentarlas, suprime su contenido si en tres meses no se abre una investigación, borra los
          expedientes a los diez años y deja registrado quién abre cada uno, sin que lo vean los administradores si hay
          Responsable.
        </li>
      </ul>

      <h2>3. Lo que hace el encargado</h2>
      <ol>
        <li>
          <strong>Seguir las instrucciones de la responsable.</strong> Sus instrucciones son el uso que haga del
          servicio y su configuración, y lo que le pida por escrito a {correo}. Si una instrucción le parece contraria a
          la ley, se lo dirá.
        </li>
        <li>
          <strong>No usar los datos para nada más</strong>: ni para fines propios, ni para publicidad, ni para
          venderlos o cederlos, salvo que una ley lo obligue (y entonces se lo dirá antes, si la ley lo permite).
        </li>
        <li>
          <strong>Confidencialidad.</strong> Solo el titular accede a los datos, y solo cuando hace falta para prestar
          el servicio o lo pide la responsable. Si algún día hubiera más personas, se comprometerían por escrito a
          guardar secreto.
        </li>
        <li>
          <strong>Seguridad.</strong> Aplicar las medidas del <a href="#anexo">anexo</a> y mantenerlas al día con el
          estado de la técnica (RGPD, art. 32).
        </li>
        <li>
          <strong>Subencargados.</strong> La responsable autoriza de forma general a que el encargado use los
          proveedores de la <Link to="/legal/subencargados">lista de subencargados</Link>. Antes de añadir o cambiar
          uno, el encargado lo avisará a los administradores con al menos 15 días, y la responsable podrá oponerse
          dándose de baja. Cada subencargado queda obligado a lo mismo que el encargado.
        </li>
        <li>
          <strong>Ayudar con los derechos de las personas</strong>: el servicio permite consultar, corregir, exportar y
          borrar los datos de cada persona, y cuenta en pantalla el mes que hay para responder a cada petición de
          borrado. A cada persona le dice, la primera vez que entra, que la responsable es su empresa y a quién
          dirigirse (el contacto que la responsable ponga en sus ajustes). Si una persona escribe al encargado, este se
          lo pasará a la responsable en 5 días laborables y no contestará por ella.
        </li>
        <li>
          <strong>Conservar solo lo necesario.</strong> La responsable encarga al encargado que borre solo lo que ya
          no hace falta (sesiones caducadas, códigos de acceso, avisos y dispositivos antiguos, candidaturas resueltas
          hace más de un año y currículos sustituidos) y que anonimice a cada persona dada de baja cuando hayan pasado
          cuatro años desde su último fichaje y desde su baja, que es lo que la ley obliga a conservar el registro de
          jornada (art. 34.9 del Estatuto de los Trabajadores). El registro se queda; lo que se quita es quién era. Si
          la responsable necesita otro plazo, por ejemplo por un procedimiento abierto, lo dirá por escrito antes de
          que venza.
        </li>
        <li>
          <strong>Ayudar con las demás obligaciones</strong> de la responsable (seguridad, brechas, evaluaciones de
          impacto, consulta previa), dándole la información que tenga y que le haga falta.
        </li>
        <li>
          <strong>Avisar de una brecha de seguridad</strong> que afecte a los datos de la responsable sin dilación
          indebida y, en todo caso, en las 48 horas siguientes a saberlo, con lo que se sepa: qué ha pasado, qué datos
          y cuántas personas pueden estar afectadas, qué consecuencias puede tener y qué se ha hecho. Notificar a la
          Agencia Española de Protección de Datos y, si procede, a las personas, le corresponde a la responsable.
        </li>
        <li>
          <strong>Al terminar</strong>, entregar a la responsable todos sus datos y borrarlos treinta días después, como
          dicen las condiciones (punto 9), salvo que una ley obligue a conservarlos. Las copias de seguridad se borran
          solas en su ciclo (como mucho 30 días después).
        </li>
        <li>
          <strong>Demostrar que cumple</strong>: dar a la responsable la información que pida para comprobarlo, y
          permitir auditorías razonables, avisadas con antelación y a su costa, que no pongan en riesgo los datos de
          otras empresas.
        </li>
      </ol>

      <h2>4. Lo que hace la responsable</h2>
      <ul>
        <li>Tener una base legal para cada dato que guarde y no meter en el servicio datos que no necesite.</li>
        <li>Informar a las personas de su plantilla, a los candidatos y a quien use su canal de denuncias.</li>
        <li>Hacer la evaluación de impacto si procede, y consultar a la representación legal cuando toque.</li>
        <li>Atender los derechos de las personas y las brechas que le notifique el encargado.</li>
        <li>Dar acceso solo a quien corresponda, con el rol que corresponda.</li>
      </ul>

      <h2>5. Fuera de la Unión Europea</h2>
      <p>
        Los datos se guardan en la Unión Europea. Si un subencargado accede a ellos desde fuera, o trata alguno fuera
        (como el envío de avisos al móvil), lo hace con una garantía del capítulo V del RGPD: una decisión de adecuación
        (en Estados Unidos, el Marco de Privacidad de Datos UE-EE. UU.) o las cláusulas contractuales tipo de la
        Comisión. Están en la lista de subencargados.
      </p>

      <h2>6. Responsabilidad</h2>
      <p>
        Cada parte responde de lo suyo como dice el artículo 82 del RGPD. Si el encargado usara los datos para otra
        cosa o incumpliera este contrato, se le consideraría responsable de ese tratamiento.
      </p>

      <h2 id="anexo">Anexo: medidas de seguridad</h2>
      <h3>Acceso</h3>
      <ul>
        <li>Cada persona entra con su cuenta. Las contraseñas se guardan con BCrypt y nunca en claro.</li>
        <li>
          Límites a los intentos de entrada por cuenta y por dirección, con esperas que crecen, y bloqueo del código del
          kiosco tras varios fallos.
        </li>
        <li>Entrada con Google o Microsoft (OpenID Connect con PKCE), si se quiere.</li>
        <li>
          Permisos por rol (empleado, gestor, recursos humanos, administración), y cada empresa solo ve lo suyo: se
          comprueba en cada consulta.
        </li>
        <li>
          Sesiones cortas (el acceso caduca a los 15 minutos y se renueva), que se pueden cerrar desde Ajustes; dar de
          baja a alguien le cierra todas las suyas.
        </li>
      </ul>
      <h3>Integridad y trazabilidad</h3>
      <ul>
        <li>
          Cada fichaje y cada corrección queda en una traza de auditoría que no se puede cambiar ni borrar desde la
          aplicación, encadenada con huellas criptográficas y comprobada entera cada noche.
        </li>
        <li>La firma mensual del registro guarda una huella de lo firmado y se invalida si algo cambia después.</li>
        <li>Lo que hace el operador del servicio con cada empresa queda registrado sin poder borrarse.</li>
        <li>
          Queda registrado, sin poder cambiarse ni borrarse durante dos años, quién consulta datos personales de otra
          persona (un currículo, un perfil, un registro mensual, el Excel de horas, un expediente de denuncia o una
          exportación). La administración de la empresa puede revisarlo.
        </li>
        <li>
          Los correos de aviso no copian lo que escribió nadie (motivos, comentarios, cartas): se lee dentro de la
          aplicación.
        </li>
      </ul>
      <h3>Comunicaciones y almacenamiento</h3>
      <ul>
        <li>Todo viaja cifrado (HTTPS con HSTS) y la web lleva una política de contenido estricta.</li>
        <li>Los datos se guardan cifrados en el proveedor de base de datos, en Frankfurt.</li>
        <li>
          Copias de seguridad diarias, cifradas, que se conservan 30 días, y restauración a cualquier punto de las
          últimas horas.
        </li>
        <li>La app guarda la sesión cifrada con el almacén de claves del teléfono.</li>
      </ul>
      <h3>Operación</h3>
      <ul>
        <li>
          Acceso a la infraestructura solo del titular, con contraseñas únicas y segundo factor en los proveedores que
          lo permiten.
        </li>
        <li>
          Dependencias vigiladas (alertas automáticas de vulnerabilidades y análisis de código en cada cambio) y
          actualizaciones periódicas.
        </li>
        <li>Registros de errores sin correos electrónicos.</li>
        <li>
          Un procedimiento escrito para responder a incidentes, con el aviso a la responsable en 48 horas, y una forma
          pública de avisar de vulnerabilidades (<Link to="/legal/seguridad">aquí</Link>).
        </li>
      </ul>
    </>
  );
}
