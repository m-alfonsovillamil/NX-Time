/**
 * Registrar una empresa: la empresa y, dentro, la cuenta de quien la registra
 * como ADMIN.
 *
 * Hay dos formas, y esta página es las dos:
 *
 * - **Con correo y contraseña.** Desde la V37 (ADR 034) registrar **no abre
 *   sesión**: el servidor manda un código al correo y aquí se pasa a pedirlo
 *   (`ConfirmarCorreo`). Es lo que impide registrar una empresa con el correo
 *   de otro y usarla para mandar altas a cualquiera.
 * - **Con una cuenta de Google o de Microsoft** (ADR 038). Quién eres lo dice
 *   el proveedor, así que no hay ni contraseña ni código. El botón manda el
 *   navegador al servidor; al volver (`?sso=continuar`) el servidor ya sabe
 *   con qué cuenta has entrado —lo guarda en una cookie suya— y aquí solo se
 *   pide el nombre de la empresa y el tuyo.
 *
 * En los dos casos la sesión se abre con `origen: 'WEB'`: refresh en la cookie
 * `HttpOnly` y las 12 horas del navegador (ADR 019 y 030).
 */

import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { abrirSesion } from '../../api/sesion';
import { useProveedoresSso, useVueltaDeSso } from '../../api/sso';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { T } from '../../i18n/es';
import { ConfirmarCorreo } from './ConfirmarCorreo';
import { MarcoDeAcceso } from './MarcoDeAcceso';

const G = T.registro;

interface DatosDelRegistro {
  nombreEmpresa: string;
  nombre: string;
  apellidos: string;
  email: string;
  contrasena: string;
}

/** La cuenta del proveedor con la que se está registrando, tal como la dice el servidor. */
interface CuentaDeFuera {
  proveedor: string;
  correo: string;
}

/** Lo que se le dice a quien vuelve del proveedor sin poder seguir, o `null`. */
function mensajeDeVuelta(motivo: string | null): string | null {
  if (motivo === null || motivo === '' || motivo === 'continuar') return null;
  const motivos: Record<string, string> = G.sso.motivos;
  return motivos[motivo] ?? G.sso.motivos.fallo;
}

export function RegistroEmpresa() {
  const navegar = useNavigate();
  const proveedores = useProveedoresSso();
  const vuelta = useVueltaDeSso();

  const [datos, setDatos] = useState<DatosDelRegistro>({
    nombreEmpresa: '',
    nombre: '',
    apellidos: '',
    email: '',
    contrasena: '',
  });
  const [error, setError] = useState<string | null>(mensajeDeVuelta(vuelta));
  /** El correo al que ha salido el código: con él, se pasa a pedirlo. */
  const [pendiente, setPendiente] = useState<string | null>(null);
  /**
   * Con qué cuenta se registra: `undefined` mientras se pregunta al servidor,
   * `null` si no se viene de un proveedor (o aquello ha caducado).
   */
  const [deFuera, setDeFuera] = useState<CuentaDeFuera | null | undefined>(
    vuelta === 'continuar' ? undefined : null,
  );

  useEffect(() => {
    if (vuelta !== 'continuar') return;
    let vigente = true;
    void (async () => {
      let cuenta: CuentaDeFuera | null = null;
      try {
        const { data } = await cliente.GET('/auth/sso/registro');
        if (data?.correo !== undefined) cuenta = { proveedor: data.nombre ?? '', correo: data.correo };
      } catch {
        // Como si hubiera caducado: se dice, y queda el formulario de siempre.
      }
      if (!vigente) return;
      setDeFuera(cuenta);
      if (cuenta === null) setError(G.sso.motivos.caducado);
    })();
    return () => {
      vigente = false;
    };
  }, [vuelta]);

  const registrar = useMutacion(
    (cuerpo: DatosDelRegistro) => pedir(cliente.POST('/auth/register-manager', { body: { ...cuerpo, origen: 'WEB' } })),
    { alTerminar: (respuesta) => setPendiente(respuesta.email ?? datos.email.trim()) },
  );

  const registrarConSso = useMutacion(
    (cuerpo: Pick<DatosDelRegistro, 'nombreEmpresa' | 'nombre' | 'apellidos'>) =>
      pedir(cliente.POST('/auth/sso/registro', { body: cuerpo })),
    {
      alTerminar: (sesion) => {
        abrirSesion({
          accessToken: sesion.token ?? '',
          nombre: sesion.nombre ?? '',
          authorities: sesion.authorities ?? [],
          zonaHoraria: sesion.zonaHoraria,
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
    const comunes = {
      nombreEmpresa: datos.nombreEmpresa.trim(),
      nombre: datos.nombre.trim(),
      apellidos: datos.apellidos.trim(),
    };
    if (deFuera) {
      if (Object.values(comunes).some((v) => v === '')) return setError(G.faltan);
      setError(null);
      return registrarConSso.mutate(comunes);
    }
    const limpio = { ...comunes, email: datos.email.trim(), contrasena: datos.contrasena };
    if (Object.values(limpio).some((v) => v === '')) return setError(G.faltan);
    if (limpio.contrasena.length < 8) return setError(T.contrasenas.corta);
    if (limpio.contrasena.length > 72) return setError(T.contrasenas.larga);
    setError(null);
    registrar.mutate(limpio);
  }

  const mensaje = error ?? registrar.error?.message ?? registrarConSso.error?.message ?? null;

  if (pendiente !== null) return <ConfirmarCorreo email={pendiente} />;

  if (deFuera === undefined) {
    return (
      <MarcoDeAcceso>
        <div className="nx-tarjeta nx-formulario">
          <header>
            <h1>{G.titulo}</h1>
          </header>
          <p className="nx-sutil" role="status">
            {G.sso.comprobando}
          </p>
        </div>
      </MarcoDeAcceso>
    );
  }

  return (
    <MarcoDeAcceso>
      <form className="nx-tarjeta nx-formulario" onSubmit={enviar} noValidate>
        <header>
          <h1>{G.titulo}</h1>
          <p className="nx-sutil">{deFuera ? G.sso.explicacion(deFuera.proveedor, deFuera.correo) : G.explicacion}</p>
        </header>
        <Campo id="registro-empresa" etiqueta={G.empresa} autoComplete="organization" value={datos.nombreEmpresa} onChange={(e) => cambiar('nombreEmpresa', e.target.value)} />
        <Campo id="registro-nombre" etiqueta={G.nombre} autoComplete="given-name" maxLength={100} value={datos.nombre} onChange={(e) => cambiar('nombre', e.target.value)} />
        <Campo id="registro-apellidos" etiqueta={G.apellidos} autoComplete="family-name" maxLength={150} value={datos.apellidos} onChange={(e) => cambiar('apellidos', e.target.value)} />
        {!deFuera && (
          <>
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
          </>
        )}
        {mensaje !== null && <Aviso>{mensaje}</Aviso>}
        <Boton type="submit" ocupado={registrar.isPending || registrarConSso.isPending}>
          {G.crear}
        </Boton>

        {deFuera ? (
          <Boton
            variante="texto"
            onClick={() => {
              setDeFuera(null);
              setError(null);
            }}
          >
            {G.sso.otraForma}
          </Boton>
        ) : (
          proveedores.length > 0 && (
            <div className="nx-sso" role="group" aria-label={G.sso.etiqueta}>
              <p className="nx-sso__separador" aria-hidden="true">
                <span>{T.sso.separador}</span>
              </p>
              {proveedores.map((p) => (
                <a
                  key={p.id}
                  className="nx-boton nx-boton--secundario nx-boton--enlace"
                  href={`${p.inicio}${p.inicio.includes('?') ? '&' : '?'}registro=1`}
                >
                  {G.sso.registrarCon(p.nombre)}
                </a>
              ))}
            </div>
          )
        )}
        <Link className="nx-enlace" to="/">
          {G.yaTengoCuenta}
        </Link>
      </form>
    </MarcoDeAcceso>
  );
}
