/**
 * Registrar una empresa: la empresa y, dentro, la cuenta de quien la registra
 * como ADMIN.
 *
 * Manda **`origen: 'WEB'`**, como el login: el servidor pone entonces el
 * refresh en la cookie `HttpOnly` y le da las 12 horas del navegador (ADR 019
 * y 030). Sin ello, el registro devolvía un refresh de 30 días en el cuerpo, al
 * alcance del JavaScript; es lo que el ADR 030 dejó apuntado para esta fase.
 */

import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { abrirSesion } from '../../api/sesion';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { T } from '../../i18n/es';

const G = T.registro;

interface DatosDelRegistro {
  nombreEmpresa: string;
  nombre: string;
  apellidos: string;
  email: string;
  contrasena: string;
}

export function RegistroEmpresa() {
  const navegar = useNavigate();
  const [datos, setDatos] = useState<DatosDelRegistro>({
    nombreEmpresa: '',
    nombre: '',
    apellidos: '',
    email: '',
    contrasena: '',
  });
  const [error, setError] = useState<string | null>(null);

  const registrar = useMutacion(
    (cuerpo: DatosDelRegistro) => pedir(cliente.POST('/auth/register-manager', { body: { ...cuerpo, origen: 'WEB' } })),
    {
      alTerminar: (sesion) => {
        abrirSesion({
          accessToken: sesion.token ?? '',
          nombre: sesion.nombre ?? '',
          authorities: sesion.authorities ?? [],
        });
        navegar('/fichar', { replace: true });
      },
    },
  );

  function cambiar(campo: keyof DatosDelRegistro, valor: string) {
    setDatos((d) => ({ ...d, [campo]: valor }));
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = {
      nombreEmpresa: datos.nombreEmpresa.trim(),
      nombre: datos.nombre.trim(),
      apellidos: datos.apellidos.trim(),
      email: datos.email.trim(),
      contrasena: datos.contrasena,
    };
    if (Object.values(limpio).some((v) => v === '')) return setError(G.faltan);
    if (limpio.contrasena.length < 8) return setError(T.contrasenas.corta);
    if (limpio.contrasena.length > 72) return setError(T.contrasenas.larga);
    setError(null);
    registrar.mutate(limpio);
  }

  const mensaje = error ?? registrar.error?.message ?? null;

  return (
    <main className="nx-centrado">
      <form className="nx-tarjeta nx-formulario" onSubmit={enviar} noValidate>
        <header>
          <h1>{G.titulo}</h1>
          <p className="nx-sutil">{G.explicacion}</p>
        </header>
        <Campo id="registro-empresa" etiqueta={G.empresa} autoComplete="organization" value={datos.nombreEmpresa} onChange={(e) => cambiar('nombreEmpresa', e.target.value)} />
        <Campo id="registro-nombre" etiqueta={G.nombre} autoComplete="given-name" maxLength={100} value={datos.nombre} onChange={(e) => cambiar('nombre', e.target.value)} />
        <Campo id="registro-apellidos" etiqueta={G.apellidos} autoComplete="family-name" maxLength={150} value={datos.apellidos} onChange={(e) => cambiar('apellidos', e.target.value)} />
        <Campo id="registro-email" etiqueta={G.email} type="email" autoComplete="email" value={datos.email} onChange={(e) => cambiar('email', e.target.value)} />
        <Campo
          id="registro-contrasena"
          etiqueta={G.contrasena}
          ayuda={G.contrasenaAyuda}
          type="password"
          autoComplete="new-password"
          value={datos.contrasena}
          onChange={(e) => cambiar('contrasena', e.target.value)}
        />
        {mensaje !== null && <Aviso>{mensaje}</Aviso>}
        <Boton type="submit" ocupado={registrar.isPending}>
          {G.crear}
        </Boton>
        <Link className="nx-enlace" to="/">
          {G.yaTengoCuenta}
        </Link>
      </form>
    </main>
  );
}
