/**
 * Cookies y almacenamiento del navegador. Todo es técnico (art. 22.2 LSSI): no
 * hace falta pedir consentimiento, pero sí informar. Si se añade una cookie o
 * una clave de localStorage, va aquí.
 */

import { TITULAR } from '../../../legal/titular';

const COOKIES: readonly [string, string, string][] = [
  ['__Secure-nx_refresh', 'Mantiene abierta tu sesión en este navegador. Solo la lee el servidor (HttpOnly).', 'Siete días sin usarla, o hasta que salgas'],
  ['__Secure-nx_csrf', 'Protege la sesión: comprueba que las peticiones salen de esta web y no de otra.', 'Lo mismo que la sesión'],
  ['__Secure-nx_sso', 'Mientras entras con Google o Microsoft: recuerda a qué volver.', 'Diez minutos'],
  ['__Secure-nx_sso_alta', 'Mientras registras una empresa con Google o Microsoft: recuerda con qué cuenta.', 'Quince minutos'],
];

const ALMACENAMIENTO: readonly [string, string][] = [
  ['nx-tema', 'Si prefieres el tema claro o el oscuro.'],
  ['nx-menu-abiertos', 'Qué apartados del menú tienes desplegados.'],
  ['nx-push-token', 'Si has activado los avisos en este navegador, para poder apagarlos.'],
  ['nx-kiosco-token', 'Solo en una tablet usada como kiosco: la credencial del kiosco.'],
];

export function Cookies() {
  return (
    <>
      <p>
        {TITULAR.producto} solo usa cookies y almacenamiento del navegador <strong>técnicos</strong>: los necesarios para
        que funcione lo que tú pides (entrar, quedarte dentro, recordar tus preferencias). No hay publicidad, ni
        analítica, ni cookies de terceros. Por eso no te pedimos consentimiento: el artículo 22.2 de la LSSI no lo
        exige para estas. Pero tienes derecho a saber cuáles son.
      </p>

      <h2>Cookies</h2>
      <div className="nx-legal__tabla">
        <table>
          <thead>
            <tr>
              <th scope="col">Nombre</th>
              <th scope="col">Para qué</th>
              <th scope="col">Cuánto dura</th>
            </tr>
          </thead>
          <tbody>
            {COOKIES.map(([nombre, para, dura]) => (
              <tr key={nombre}>
                <td>
                  <code>{nombre}</code>
                </td>
                <td>{para}</td>
                <td>{dura}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p>
        Son nuestras, solo viajan por conexiones cifradas y no se comparten con nadie.
      </p>

      <h2>Almacenamiento del navegador</h2>
      <p>Además, la web guarda en tu navegador (localStorage):</p>
      <div className="nx-legal__tabla">
        <table>
          <thead>
            <tr>
              <th scope="col">Clave</th>
              <th scope="col">Para qué</th>
            </tr>
          </thead>
          <tbody>
            {ALMACENAMIENTO.map(([clave, para]) => (
              <tr key={clave}>
                <td>
                  <code>{clave}</code>
                </td>
                <td>{para}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p>
        <strong>Si activas los avisos</strong> en Ajustes, el componente de Google Firebase que los entrega guarda
        además, en la base de datos del navegador (IndexedDB), el identificador de este navegador para poder mandarle
        avisos. Solo se hace si lo activas tú, y se borra al apagarlos o al borrar los datos del sitio.
      </p>

      <h2>Cómo borrarlo</h2>
      <p>
        Saliendo de la sesión se borran las cookies de la sesión. Todo lo demás se borra desde los ajustes de tu
        navegador («Borrar datos de navegación» o «Datos del sitio»). Si las bloqueas, no podrás entrar.
      </p>

      <h2>En la app</h2>
      <p>
        La app de Android no usa cookies. Guarda tu sesión cifrada en el teléfono, y tus preferencias de avisos y del
        informe de errores (que puedes apagar en Ajustes).
      </p>
    </>
  );
}
