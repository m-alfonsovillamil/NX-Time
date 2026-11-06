/**
 * Cómo se pide el borrado de una cuenta y de sus datos. Google Play exige una
 * página pública con esto a toda app que deja crear cuentas. Solo describe lo
 * que ya existe (ADR 047 y 050): no promete nada nuevo, y por eso no sube la
 * versión de las condiciones.
 */

import { TITULAR } from '../../../legal/titular';

export function BorrarCuenta() {
  return (
    <>
      <p>
        Cómo pedir que se borre tu cuenta de {TITULAR.producto} y qué se borra. Depende de si usas {TITULAR.producto}{' '}
        porque tu empresa lo usa, o si eres quien la administra.
      </p>

      <h2>Si trabajas en una empresa que usa {TITULAR.producto}</h2>
      <p>
        Tus datos son de tu empresa, que es la responsable; {TITULAR.producto} es la herramienta que usa. Para pedir el
        borrado:
      </p>
      <ul>
        <li>
          en la app: <strong>Mi perfil → Ajustes → Borrar mis datos → Pedir el borrado</strong>;
        </li>
        <li>
          en la web: <strong>Ajustes → Borrar mis datos → Pedir el borrado</strong>;
        </li>
        <li>o directamente a tu empresa, por el canal que tenga para la protección de datos.</li>
      </ul>
      <p>
        Lo revisa el departamento de personal de tu empresa, que tiene un mes para responder. Si lo ejecuta, tu cuenta
        se desactiva y no se puede volver a entrar, y se borran tu foto, tu currículum, tu fecha de nacimiento, tus
        candidaturas y tus avisos.
      </p>
      <p>
        <strong>Tu registro de jornada no se borra enseguida.</strong> La ley obliga a la empresa a guardarlo cuatro
        años: tus fichajes, ausencias y correcciones se conservan con tu nombre cuatro años desde tu último fichaje, y
        después se anonimizan. Antes de pedir el borrado puedes descargar una copia de todo en{' '}
        <strong>Ajustes → Descargar mis datos</strong>.
      </p>

      <h2>Si administras la empresa</h2>
      <p>
        Para borrar la empresa entera, con todas sus cuentas: en la web, <strong>Ajustes de la empresa → Dar de baja la
        empresa</strong>. Durante 30 días todo sigue funcionando para que os llevéis los datos, y se puede cancelar.
        Después se borra todo: personas, fichajes, ausencias, firmas, denuncias y la traza de auditoría, y no se puede
        deshacer.
      </p>
      <p>
        La empresa tiene que conservar el registro de jornada cuatro años: descárgalo antes desde{' '}
        <strong>Informes</strong>.
      </p>

      <h2>Si no puedes entrar</h2>
      <p>
        Escribe a <a href={`mailto:${TITULAR.correoPrivacidad}`}>{TITULAR.correoPrivacidad}</a> desde el correo de tu
        cuenta, diciendo de qué empresa eres. Si los datos son de tu empresa, le hacemos llegar la petición, porque es
        ella quien decide; si son nuestros (la cuenta con la que se registró una empresa), la atendemos nosotros en un
        mes como mucho.
      </p>
    </>
  );
}
