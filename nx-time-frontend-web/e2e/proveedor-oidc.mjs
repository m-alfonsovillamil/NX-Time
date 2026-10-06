/**
 * Un Google y un Microsoft de mentira, para probar el SSO con un navegador de
 * verdad (ADR 036). Lo arranca quien lanza las specs: ver `e2e/sso.spec.ts`.
 *
 * Hace lo que un proveedor de OpenID Connect, lo justo:
 *
 * - `/google/autorizar` y `/microsoft/autorizar`: la página de «elige tu
 *   cuenta». Es HTML con enlaces; Playwright pulsa uno, como una persona.
 * - `/token`: canjea el código por un ID token firmado.
 * - `/jwks`: la clave pública con la que el backend comprueba esa firma.
 *
 * No comprueba el `client_secret` ni el PKCE: eso lo prueba `SsoIT` en el
 * backend, donde se puede torcer cada pieza. Aquí se prueba lo que solo se ve
 * con un navegador: que las cookies van y vuelven, y que la web retoma la
 * sesión.
 *
 * Sin dependencias: `node e2e/proveedor-oidc.mjs`.
 */

import { createSign, generateKeyPairSync, randomBytes } from 'node:crypto';
import { createServer } from 'node:http';

const PUERTO = Number(process.env.PUERTO_OIDC ?? 9099);
const INQUILINO_DE_EMPRESA = '11111111-2222-3333-4444-555555555555';

/** Las cuentas que ofrece cada proveedor. El correo de Javier es el de la demo. */
const CUENTAS = {
  google: [
    { id: 'javier', nombre: 'Javier (con cuenta en NX Time)', sub: 'google-javier', email: 'javier.lopez@techcorp.demo', claims: { email_verified: true } },
    { id: 'nadie', nombre: 'Alguien sin cuenta en NX Time', sub: 'google-nadie', email: 'nadie@sin-cuenta.example', claims: { email_verified: true } },
  ],
  microsoft: [
    // Una cuenta de trabajo sin `xms_edov`: Microsoft no garantiza su correo.
    { id: 'trabajo', nombre: 'Javier, cuenta del trabajo', sub: 'microsoft-javier', email: 'javier.lopez@techcorp.demo', claims: { tid: INQUILINO_DE_EMPRESA } },
  ],
};

const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const CLAVE = { ...publicKey.export({ format: 'jwk' }), kid: 'e2e', alg: 'RS256', use: 'sig' };

/** código → lo que hay que poner en su ID token. */
const pendientes = new Map();

const b64 = (dato) => Buffer.from(typeof dato === 'string' ? dato : JSON.stringify(dato)).toString('base64url');

function idToken(claims) {
  const ahora = Math.floor(Date.now() / 1000);
  const cuerpo = `${b64({ alg: 'RS256', typ: 'JWT', kid: 'e2e' })}.${b64({ iat: ahora, exp: ahora + 300, ...claims })}`;
  return `${cuerpo}.${createSign('RSA-SHA256').update(cuerpo).sign(privateKey).toString('base64url')}`;
}

const escapar = (texto) => texto.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

function paginaDeCuentas(proveedor, consulta) {
  const enlaces = CUENTAS[proveedor]
    .map((c) => `<li><a href="/${proveedor}/elegir?cuenta=${c.id}&amp;${escapar(consulta)}">${escapar(c.nombre)}</a></li>`)
    .join('');
  // Con `viewport` y sin oferta de traducción: también se usa desde el navegador
  // de un emulador de Android, para probar la app, y allí la página salía
  // diminuta y tapada por los avisos de Chrome. Por eso los enlaces van a media
  // pantalla y son grandes.
  return `<!doctype html><html lang="es" translate="no"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><meta name="google" content="notranslate">
<title>Elige una cuenta</title>
<style>body{font:18px/1.4 sans-serif;margin:0;padding:38vh 24px 24px}li,p{margin:0 0 28px}a{display:block;padding:12px 0}</style></head>
<body><h1>Elige una cuenta (${proveedor} de mentira)</h1><ul>${enlaces}</ul>
<p><a href="/${proveedor}/elegir?cuenta=cancelar&amp;${escapar(consulta)}">Cancelar</a></p></body></html>`;
}

const servidor = createServer(async (peticion, respuesta) => {
  const url = new URL(peticion.url, `http://localhost:${PUERTO}`);
  const [, proveedor, accion] = url.pathname.split('/');
  const responder = (estado, tipo, cuerpo, cabeceras = {}) => {
    respuesta.writeHead(estado, { 'Content-Type': tipo, 'Cache-Control': 'no-store', ...cabeceras });
    respuesta.end(cuerpo);
  };

  if (url.pathname === '/jwks') return responder(200, 'application/json', JSON.stringify({ keys: [CLAVE] }));

  if (url.pathname === '/token' && peticion.method === 'POST') {
    let cuerpo = '';
    for await (const trozo of peticion) cuerpo += trozo;
    const formulario = new URLSearchParams(cuerpo);
    const pendiente = pendientes.get(formulario.get('code'));
    pendientes.delete(formulario.get('code'));
    if (pendiente === undefined) return responder(400, 'application/json', '{"error":"invalid_grant"}');
    const emisor =
      pendiente.proveedor === 'google'
        ? 'https://accounts.google.com'
        : `https://login.microsoftonline.com/${pendiente.cuenta.claims.tid}/v2.0`;
    const token = idToken({
      iss: emisor,
      aud: formulario.get('client_id'),
      sub: pendiente.cuenta.sub,
      email: pendiente.cuenta.email,
      nonce: pendiente.nonce,
      ...pendiente.cuenta.claims,
    });
    return responder(200, 'application/json', JSON.stringify({ token_type: 'Bearer', id_token: token }));
  }

  if (CUENTAS[proveedor] !== undefined && accion === 'autorizar') {
    return responder(200, 'text/html; charset=utf-8', paginaDeCuentas(proveedor, url.searchParams.toString()));
  }

  if (CUENTAS[proveedor] !== undefined && accion === 'elegir') {
    const vuelta = new URL(url.searchParams.get('redirect_uri'));
    vuelta.searchParams.set('state', url.searchParams.get('state'));
    const cuenta = CUENTAS[proveedor].find((c) => c.id === url.searchParams.get('cuenta'));
    if (cuenta === undefined) {
      vuelta.searchParams.set('error', 'access_denied');
    } else {
      const codigo = randomBytes(16).toString('hex');
      pendientes.set(codigo, { proveedor, cuenta, nonce: url.searchParams.get('nonce') });
      vuelta.searchParams.set('code', codigo);
    }
    return responder(302, 'text/plain', '', { Location: vuelta.toString() });
  }

  return responder(404, 'text/plain', 'No existe.');
});

servidor.listen(PUERTO, '127.0.0.1', () => {
  console.log(`Proveedor OIDC de mentira en http://localhost:${PUERTO}`);
});
