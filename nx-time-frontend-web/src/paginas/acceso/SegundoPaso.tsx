/**
 * El código del segundo factor, después de la contraseña (ADR 051).
 *
 * El login de quien lo tiene activado no abre sesión: devuelve un `desafio` de
 * cinco minutos, que va aquí con el código a `/auth/2fa`. El campo vale
 * igual para las 6 cifras de la aplicación que para un código de
 * recuperación: el servidor distingue uno de otro por su forma.
 *
 * Si el desafío ha caducado, el servidor responde 401 diciendo que hay que
 * volver a entrar, y el mensaje se enseña tal cual: el enlace de abajo lleva
 * otra vez al formulario de la contraseña.
 */

import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { abrirSesion } from '../../api/sesion';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { T } from '../../i18n/es';
import { EmpresaPendiente, esEmpresaNoActiva } from './EmpresaPendiente';
import { MarcoDeAcceso } from './MarcoDeAcceso';

const S = T.segundoPaso;

/** Un código de la aplicación (6 cifras) o uno de recuperación (10 letras y números, con guion o sin él). */
export function pareceUnCodigo(escrito: string): boolean {
  const limpio = escrito.replace(/[\s-]/g, '');
  return /^\d{6}$/.test(limpio) || /^[a-z0-9]{10}$/i.test(limpio);
}

export function SegundoPaso({ desafio, destino, alVolver }: { desafio: string; destino: string; alVolver: () => void }) {
  const navegar = useNavigate();
  const [codigo, setCodigo] = useState('');
  const [error, setError] = useState<string | null>(null);

  const verificar = useMutacion(
    (escrito: string) => pedir(cliente.POST('/auth/2fa', { body: { desafio, codigo: escrito, origen: 'WEB' } })),
    {
      alTerminar: (sesion) => {
        abrirSesion({
          accessToken: sesion.token ?? '',
          nombre: sesion.nombre ?? '',
          authorities: sesion.authorities ?? [],
          zonaHoraria: sesion.zonaHoraria,
          condicionesPorAceptar: sesion.condicionesPorAceptar,
          segundoFactorPorActivar: sesion.segundoFactorPorActivar,
        });
        navegar(destino, { replace: true });
      },
    },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!pareceUnCodigo(codigo)) return setError(S.faltaCodigo);
    setError(null);
    verificar.mutate(codigo.trim());
  }

  if (esEmpresaNoActiva(verificar.error)) return <EmpresaPendiente mensaje={verificar.error.message} />;
  const mensaje = error ?? verificar.error?.message ?? null;

  return (
    <MarcoDeAcceso>
      <form className="nx-tarjeta nx-formulario" onSubmit={enviar} noValidate>
        <header>
          <h1>{S.titulo}</h1>
          <p className="nx-sutil">{S.explicacion}</p>
        </header>
        <Campo
          id="segundo-paso-codigo"
          etiqueta={S.codigo}
          ayuda={S.ayuda}
          autoComplete="one-time-code"
          autoCapitalize="off"
          spellCheck={false}
          maxLength={14}
          value={codigo}
          onChange={(e) => setCodigo(e.target.value)}
        />
        {mensaje !== null && <Aviso>{mensaje}</Aviso>}
        <Boton type="submit" ocupado={verificar.isPending}>
          {verificar.isPending ? S.entrando : S.entrar}
        </Boton>
        <p className="nx-sutil">{S.sinCodigos}</p>
        <Boton variante="texto" onClick={alVolver}>
          {S.volver}
        </Boton>
      </form>
    </MarcoDeAcceso>
  );
}
