/**
 * El pie con los enlaces legales (plan J), para las pantallas de fuera de la
 * sesión (acceso, registro, kiosco) y para las propias páginas legales. Aparte
 * de ellas, que se cargan cuando se piden: el pie va en el paquete principal.
 */

import { Link } from 'react-router';

import { TITULAR } from '../legal/titular';

export function PieLegal() {
  return (
    <footer className="nx-pie-legal">
      <nav aria-label="Información legal">
        <Link to="/legal/aviso-legal">Aviso legal</Link>
        <Link to="/legal/privacidad">Privacidad</Link>
        <Link to="/legal/cookies">Cookies</Link>
        <Link to="/legal/condiciones">Condiciones</Link>
      </nav>
      <p>
        © {TITULAR.producto} · {TITULAR.nombre}
      </p>
    </footer>
  );
}
