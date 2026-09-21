/**
 * Entrar.
 *
 * Dos detalles que no son de adorno:
 *
 * - **`origen: 'WEB'`** en el cuerpo. El servidor da 30 días de refresh a la
 *   app y 12 horas al navegador (ADR 019). Si no se declarara, esta sesión
 *   heredaría el valor por defecto, que es `ANDROID`, y un navegador
 *   compartido se quedaría con un token de un mes.
 * - El campo se llama **`contrasena`**, no `password`. Con `password` el
 *   servidor responde 400, y eso ya costó un rato una vez. Ahora no puede
 *   repetirse: lo dicen los tipos generados del contrato.
 */

import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';

import { cliente } from '../api/cliente';
import { abrirSesion } from '../api/sesion';
import { Aviso, Boton, Campo } from '../componentes/Basicos';
import { T } from '../i18n/es';
import { mensajeDeError, mensajeDeRed } from '../util/errores';

export function Login() {
  const navegar = useNavigate();
  const [email, setEmail] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [entrando, setEntrando] = useState(false);

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
        refreshToken: data.refreshToken ?? '',
        nombre: data.nombre ?? '',
        authorities: data.authorities ?? [],
      });
      navegar('/fichar', { replace: true });
    } catch (fallo) {
      setError(mensajeDeRed(fallo));
    } finally {
      setEntrando(false);
    }
  }

  return (
    <main className="nx-centrado">
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
      </form>
    </main>
  );
}
