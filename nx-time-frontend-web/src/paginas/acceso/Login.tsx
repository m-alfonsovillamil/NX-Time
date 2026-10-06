/**
 * Entrar.
 *
 * Dos detalles que no son de adorno:
 *
 * - **`origen: 'WEB'`** en el cuerpo. El servidor da 30 días de refresh a la
 *   app y 12 horas al navegador (ADR 019). Si no se declarara, esta sesión
 *   heredaría el valor por defecto, que es `ANDROID`, y un navegador
 *   compartido se quedaría con un token de un mes. Es también lo que hace que
 *   el servidor ponga el refresh en una cookie `HttpOnly` y no en el cuerpo
 *   (ADR 030): esta página nunca lo ve.
 * - El campo se llama **`contrasena`**, no `password`. Con `password` el
 *   servidor responde 400, y eso ya costó un rato una vez. Ahora no puede
 *   repetirse: lo dicen los tipos generados del contrato.
 *
 * Debajo, **entrar con Google o con Microsoft** (ADR 036), si el servidor los
 * tiene configurados. No son botones: son enlaces, porque lo que hacen es
 * mandar el navegador al servidor, que lo lleva al proveedor y lo devuelve
 * aquí con la sesión hecha o con el motivo en la URL (`?sso=…`).
 */

import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { abrirSesion } from '../../api/sesion';
import { mensajeDeSso, useProveedoresSso, useVueltaDeSso } from '../../api/sso';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { T } from '../../i18n/es';
import type { EstadoDeVuelta } from '../../rutas/rutas';
import { mensajeDeError, mensajeDeRed } from '../../util/errores';
import { ConfirmarCorreo } from './ConfirmarCorreo';
import { MarcoDeAcceso } from './MarcoDeAcceso';

export function Login() {
  const navegar = useNavigate();
  // Si se llegó aquí desde un enlace a otra página (un correo, un aviso), al
  // entrar se vuelve a ella y no a la jornada.
  const desde = (useLocation().state as EstadoDeVuelta | null)?.desde ?? '/fichar';
  const [email, setEmail] = useState('');
  const [contrasena, setContrasena] = useState('');
  const proveedores = useProveedoresSso();
  // Si vuelve del proveedor sin haber entrado, se le dice por qué.
  const [error, setError] = useState<string | null>(mensajeDeSso(useVueltaDeSso()));
  const [entrando, setEntrando] = useState(false);
  /** Contraseña buena, correo sin confirmar (403, ADR 034): se pide el código que acaba de salir. */
  const [sinConfirmar, setSinConfirmar] = useState<string | null>(null);

  async function entrar(evento: FormEvent) {
    evento.preventDefault();
    if (entrando) return;

    if (email.trim() === '') return setError(T.login.faltaEmail);
    if (contrasena === '') return setError(T.login.faltaContrasena);

    setEntrando(true);
    setError(null);
    try {
      const { data, error: fallo, response } = await cliente.POST('/auth/login', {
        body: { email: email.trim(), contrasena, origen: 'WEB' },
      });

      if (!data && response.status === 403) {
        setSinConfirmar(email.trim());
        return;
      }
      if (!data) {
        // Un 401 aquí son credenciales malas, no una sesión caducada: decir
        // "vuelve a entrar" a quien está entrando no ayuda a nadie.
        setError(
          response.status === 401 ? T.errores.credenciales : mensajeDeError(fallo, response.status),
        );
        return;
      }

      abrirSesion({
        accessToken: data.token ?? '',
        nombre: data.nombre ?? '',
        authorities: data.authorities ?? [],
        zonaHoraria: data.zonaHoraria,
      });
      navegar(desde, { replace: true });
    } catch (fallo) {
      setError(mensajeDeRed(fallo));
    } finally {
      setEntrando(false);
    }
  }

  if (sinConfirmar !== null) return <ConfirmarCorreo email={sinConfirmar} destino={desde} />;

  return (
    <MarcoDeAcceso>
      <form className="nx-tarjeta nx-formulario" onSubmit={entrar} noValidate>
        <header>
          <h1>{T.app.nombre}</h1>
          <p className="nx-sutil">{T.login.subtitulo}</p>
        </header>

        <Campo
          id="email"
          etiqueta={T.login.email}
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Campo
          id="contrasena"
          etiqueta={T.login.contrasena}
          type="password"
          autoComplete="current-password"
          value={contrasena}
          onChange={(e) => setContrasena(e.target.value)}
        />

        {error !== null && <Aviso>{error}</Aviso>}

        <Boton type="submit" ocupado={entrando}>
          {entrando ? T.login.entrando : T.login.entrar}
        </Boton>

        {proveedores.length > 0 && (
          <div className="nx-sso" role="group" aria-label={T.sso.etiqueta}>
            <p className="nx-sso__separador" aria-hidden="true">
              <span>{T.sso.separador}</span>
            </p>
            {proveedores.map((p) => (
              <a key={p.id} className="nx-boton nx-boton--secundario nx-boton--enlace" href={p.inicio}>
                {T.sso.entrarCon(p.nombre)}
              </a>
            ))}
          </div>
        )}

        <Link className="nx-enlace" to="/recuperar-acceso">
          {T.login.recuperar}
        </Link>
        <Link className="nx-enlace" to="/registro">
          {T.login.registrarEmpresa}
        </Link>
      </form>
    </MarcoDeAcceso>
  );
}
