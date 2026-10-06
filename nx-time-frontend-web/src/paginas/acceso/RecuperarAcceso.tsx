/**
 * Elegir contraseña con un código que llega por correo (ADR 014): sirve igual
 * para quien la ha olvidado y para quien entra por primera vez.
 *
 * **Nadie teclea la contraseña de otro**: al dar de alta a alguien, el sistema
 * le manda un código, y esa persona elige la suya aquí. Por eso la pantalla
 * ofrece de entrada «Ya tengo un código», para quien viene del correo de
 * bienvenida.
 *
 * Pedir el código responde lo mismo tenga cuenta el correo o no: si no,
 * cualquiera podría averiguar quién trabaja en la empresa probando
 * direcciones. La pantalla lo dice igual: «si tiene una cuenta, le hemos
 * enviado un código».
 */

import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { T } from '../../i18n/es';
import { MarcoDeAcceso } from './MarcoDeAcceso';

const R = T.recuperar;

type Paso = 'correo' | 'codigo' | 'hecho';

export function RecuperarAcceso() {
  const [paso, setPaso] = useState<Paso>('correo');
  const [email, setEmail] = useState('');
  const [enviado, setEnviado] = useState(false);
  const [codigo, setCodigo] = useState('');
  const [nueva, setNueva] = useState('');
  const [repetida, setRepetida] = useState('');
  const [error, setError] = useState<string | null>(null);

  const pedirCodigo = useMutacion(
    (correo: string) => pedir(cliente.POST('/auth/recuperar', { body: { email: correo } })),
    {
      alTerminar: () => {
        setEnviado(true);
        setPaso('codigo');
      },
    },
  );

  const confirmar = useMutacion(
    (cuerpo: { email: string; codigo: string; contrasenaNueva: string }) =>
      pedir(cliente.POST('/auth/recuperar/confirmar', { body: cuerpo })),
    { alTerminar: () => setPaso('hecho') },
  );

  function enviarCorreo(evento: FormEvent) {
    evento.preventDefault();
    if (email.trim() === '') return setError(T.login.faltaEmail);
    setError(null);
    pedirCodigo.mutate(email.trim());
  }

  function yaTengoCodigo() {
    if (email.trim() === '') return setError(T.login.faltaEmail);
    setError(null);
    setEnviado(false);
    setPaso('codigo');
  }

  function enviarCodigo(evento: FormEvent) {
    evento.preventDefault();
    if (!/^\d{6}$/.test(codigo.trim())) return setError(R.codigoIncompleto);
    if (nueva.length < 8) return setError(T.contrasenas.corta);
    if (nueva.length > 72) return setError(T.contrasenas.larga);
    if (nueva !== repetida) return setError(T.contrasenas.noCoinciden);
    setError(null);
    confirmar.mutate({ email: email.trim(), codigo: codigo.trim(), contrasenaNueva: nueva });
  }

  const mensaje = error ?? pedirCodigo.error?.message ?? confirmar.error?.message ?? null;

  return (
    <MarcoDeAcceso>
      <div className="nx-tarjeta nx-formulario">
        <header>
          <h1>{paso === 'hecho' ? R.hechoTitulo : R.titulo}</h1>
        </header>

        {paso === 'correo' && (
          <form className="nx-formulario-dialogo" onSubmit={enviarCorreo} noValidate>
            <p>{R.explicacion}</p>
            <Campo id="recuperar-email" etiqueta={T.login.email} type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
            {mensaje !== null && <Aviso>{mensaje}</Aviso>}
            <Boton type="submit" ocupado={pedirCodigo.isPending}>
              {R.enviarCodigo}
            </Boton>
            <Boton variante="texto" onClick={yaTengoCodigo}>
              {R.yaTengoCodigo}
            </Boton>
          </form>
        )}

        {paso === 'codigo' && (
          <form className="nx-formulario-dialogo" onSubmit={enviarCodigo} noValidate>
            {enviado ? (
              <p role="status">{R.codigoEnviado(email.trim())}</p>
            ) : (
              <p>{R.codigoExplicacion}</p>
            )}
            <Campo
              id="recuperar-codigo"
              etiqueta={R.codigo}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={codigo}
              onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ''))}
            />
            <Campo id="recuperar-nueva" etiqueta={R.nueva} type="password" autoComplete="new-password" value={nueva} onChange={(e) => setNueva(e.target.value)} />
            <Campo id="recuperar-repetida" etiqueta={R.repetir} type="password" autoComplete="new-password" value={repetida} onChange={(e) => setRepetida(e.target.value)} />
            {mensaje !== null && <Aviso>{mensaje}</Aviso>}
            <Boton type="submit" ocupado={confirmar.isPending}>
              {R.guardar}
            </Boton>
            <Boton variante="texto" ocupado={pedirCodigo.isPending} onClick={() => pedirCodigo.mutate(email.trim())}>
              {R.pedirOtro}
            </Boton>
          </form>
        )}

        {paso === 'hecho' && (
          <div className="nx-formulario-dialogo">
            <p>{R.hechoTexto}</p>
            <Link className="nx-boton nx-boton--primario nx-boton--enlace nx-boton--centrado" to="/">
              {R.irALogin}
            </Link>
          </div>
        )}

        {paso !== 'hecho' && (
          <Link className="nx-enlace" to="/">
            {R.volver}
          </Link>
        )}
      </div>
    </MarcoDeAcceso>
  );
}
