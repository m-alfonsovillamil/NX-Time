/**
 * Las cuentas de Google o de Microsoft con las que entro (ADR 036).
 *
 * Sirve para dos cosas: ver y quitar las que ya están, y **vincular una que por
 * el botón de entrar no pasa**. Una cuenta de Microsoft del trabajo no siempre
 * garantiza que su correo sea de quien entra, y entonces el servidor no la
 * relaciona sola con la cuenta de NX Time. Aquí sí se puede: quien llega tiene
 * su sesión abierta y entra en esa otra cuenta, así que controla las dos.
 *
 * Vincular es una navegación, no una petición: el navegador va al servidor,
 * que lo lleva al proveedor y lo devuelve a esta página con `?sso=vinculada` o
 * con el motivo por el que no ha podido ser.
 *
 * **La sección no existe si no hay nada que enseñar**: sin proveedores
 * configurados en el servidor y sin ninguna cuenta vinculada, no se pinta ni
 * sale en el índice.
 */

import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { useProveedoresSso, useVueltaDeSso, type ProveedorSso } from '../../api/sso';
import { Aviso, Boton, Insignia, Tarjeta } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { notificar } from '../../componentes/Notificaciones';
import { T } from '../../i18n/es';
import { cuenta } from '../../i18n/es/cuenta';
import { diaEnEmpresa, fechaCorta } from '../../util/fechas';

const V = cuenta.ajustes.vinculadas;

const CLAVE = ['perfil', 'identidades'] as const;
const ID = 'ajustes-vinculadas';

interface Identidad {
  proveedor: string;
  nombre: string;
  correo: string;
  vinculadaEn: string | undefined;
}

export interface DatosDeCuentasVinculadas {
  proveedores: ProveedorSso[];
  identidades: Identidad[];
  /** Si hay algo que enseñar: algún proveedor con el que vincular, o alguna cuenta ya vinculada. */
  hay: boolean;
}

export function useCuentasVinculadas(): DatosDeCuentasVinculadas {
  const proveedores = useProveedoresSso();
  const consulta = useQuery({
    queryKey: CLAVE,
    queryFn: () => pedir(cliente.GET('/api/v1/perfil/identidades')),
  });
  const identidades = (consulta.data ?? []).flatMap((i) =>
    i.proveedor !== undefined
      ? [{ proveedor: i.proveedor, nombre: i.nombre ?? i.proveedor, correo: i.correo ?? '', vinculadaEn: i.vinculadaEn }]
      : [],
  );
  return { proveedores, identidades, hay: proveedores.length > 0 || identidades.length > 0 };
}

export function CuentasVinculadas({ proveedores, identidades }: DatosDeCuentasVinculadas) {
  const vuelta = useVueltaDeSso();
  const [quitando, setQuitando] = useState<Identidad | null>(null);

  // Al volver del proveedor: a la vista, y dicho.
  useEffect(() => {
    if (vuelta === null) return;
    // Con «?.()»: jsdom, donde corren los tests, no tiene scrollIntoView.
    document.getElementById(ID)?.scrollIntoView?.();
    if (vuelta === 'vinculada') notificar(V.hecho);
  }, [vuelta]);

  const desvincular = useMutacion(
    (proveedor: string) =>
      pedir(cliente.DELETE('/api/v1/perfil/identidades/{proveedor}', { params: { path: { proveedor } } })),
    { invalida: [CLAVE], alTerminar: () => setQuitando(null) },
  );

  const motivos: Record<string, string> = V.motivos;
  const fallo = vuelta !== null && vuelta !== 'vinculada' ? (motivos[vuelta] ?? V.motivos.fallo) : null;

  // Una fila por proveedor: los que se pueden vincular y, por si alguno se ha
  // apagado en el servidor, los que ya lo están (para poder quitarlos).
  const filas = [
    ...proveedores.map((p) => ({ id: p.id, nombre: p.nombre, inicio: p.inicio as string | null })),
    ...identidades
      .filter((i) => !proveedores.some((p) => p.id === i.proveedor))
      .map((i) => ({ id: i.proveedor, nombre: i.nombre, inicio: null })),
  ];

  return (
    <Tarjeta id={ID} icono="enlace" titulo={V.titulo} descripcion={V.explicacion}>
      {fallo !== null && <Aviso>{fallo}</Aviso>}

      <ul className="nx-cuentas-vinculadas">
        {filas.map((fila) => {
          const identidad = identidades.find((i) => i.proveedor === fila.id);
          return (
            <li key={fila.id}>
              <div className="nx-cuentas-vinculadas__cuenta">
                <strong>{fila.nombre}</strong>
                {identidad !== undefined ? (
                  <span className="nx-sutil">
                    {identidad.correo}
                    {identidad.vinculadaEn !== undefined &&
                      ` · ${V.vinculadaEl(fechaCorta(diaEnEmpresa(identidad.vinculadaEn)))}`}
                  </span>
                ) : (
                  <Insignia>{V.sinVincular}</Insignia>
                )}
              </div>
              {identidad !== undefined ? (
                <Boton variante="texto" aria-label={V.desvincularDe(fila.nombre)} onClick={() => setQuitando(identidad)}>
                  {V.desvincular}
                </Boton>
              ) : (
                fila.inicio !== null && (
                  <a className="nx-boton nx-boton--secundario nx-boton--enlace" href={`${fila.inicio}?vincular=1`}>
                    {V.vincular(fila.nombre)}
                  </a>
                )
              )}
            </li>
          );
        })}
      </ul>

      <Dialogo
        abierto={quitando !== null}
        titulo={quitando !== null ? V.confirmarTitulo(quitando.nombre) : ''}
        alCerrar={() => setQuitando(null)}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setQuitando(null)}>
              {T.app.cancelar}
            </Boton>
            <Boton
              variante="peligro"
              ocupado={desvincular.isPending}
              onClick={() => quitando !== null && desvincular.mutate(quitando.proveedor)}
            >
              {V.desvincular}
            </Boton>
          </>
        }
      >
        <p>{V.confirmarTexto}</p>
        {desvincular.error !== null && <Aviso>{desvincular.error.message}</Aviso>}
      </Dialogo>
    </Tarjeta>
  );
}
