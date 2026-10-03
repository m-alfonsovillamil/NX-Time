/**
 * Confirmar el correo con el código que llegó al registrar la empresa (V37,
 * ADR 034). Lo enseñan el registro, justo después de registrar, y el login,
 * cuando el servidor responde 403 a quien tiene la contraseña buena pero no
 * ha confirmado todavía (el login ya le ha mandado otro código).
 *
 * Si el código vale, el servidor abre la sesión como el login, con
 * `origen: 'WEB'`: el refresh, en la cookie (ADR 030).
 */

import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { abrirSesion } from '../../api/sesion';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { T } from '../../i18n/es';

const C = T.confirmarCorreo;

export function ConfirmarCorreo({ email, destino = '/fichar' }: { email: string; destino?: string }) {
  const navegar = useNavigate();
  const [codigo, setCodigo] = useState('');
  const [error, setError] = useState<string | null>(null);

  const confirmar = useMutacion(
    (limpio: string) =>
      pedir(cliente.POST('/auth/registro/confirmar', { body: { email, codigo: limpio, origen: 'WEB' } })),
    {
      alTerminar: (sesion) => {
        abrirSesion({
          accessToken: sesion.token ?? '',
          nombre: sesion.nombre ?? '',
          authorities: sesion.authorities ?? [],
          zonaHoraria: sesion.zonaHoraria,
        });
        navegar(destino, { replace: true });
      },
    },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = codigo.replace(/\s/g, '');
    if (!/^\d{6}$/.test(limpio)) return setError(C.faltaCodigo);
    setError(null);
    confirmar.mutate(limpio);
  }

  const mensaje = error ?? confirmar.error?.message ?? null;

  return (
    <main className="nx-centrado">
      <form className="nx-tarjeta nx-formulario" onSubmit={enviar} noValidate>
        <header>
          <h1>{C.titulo}</h1>
          <p className="nx-sutil">{C.explicacion(email)}</p>
        </header>
        <Campo
          id="confirmar-codigo"
          etiqueta={C.codigo}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={7}
          value={codigo}
          onChange={(e) => setCodigo(e.target.value)}
        />
        {mensaje !== null && <Aviso>{mensaje}</Aviso>}
        <Boton type="submit" ocupado={confirmar.isPending}>
          {confirmar.isPending ? C.entrando : C.entrar}
        </Boton>
        <p className="nx-sutil">{C.otroCodigo}</p>
        <Link className="nx-enlace" to="/">
          {C.volver}
        </Link>
      </form>
    </main>
  );
}
