/**
 * Las rutas: el login, las secciones del catálogo dentro del marco, y los dos errores.
 *
 * Las rutas no se escriben aquí una a una: salen de `navegacion/secciones.ts`,
 * que es también de donde salen el menú y los destinos de los avisos. Una
 * sección con página tiene ruta; sin página, no.
 *
 * Dos comportamientos que no son obvios:
 *
 * - **Sin sesión, una ruta conocida lleva al login y, al entrar, vuelve a
 *   ella.** Es lo que hace útil un enlace a la web desde un correo o un aviso
 *   («tienes una ausencia por aprobar» → `/ausencias-equipo/pendientes`). Una
 *   ruta que no existe da 404 con o sin sesión: mandar a entrar para después
 *   decir «no existe» sería hacer perder el tiempo.
 * - **Con sesión, sin la authority, 403 con explicación** y no el login ni un
 *   404: la página existe, lo que falta es el permiso, y decirlo ahorra una
 *   consulta a quien administra.
 */

import { useQueryClient } from '@tanstack/react-query';
import { useEffect, type ReactNode } from 'react';
import { Link, Navigate, Route, Routes, useLocation } from 'react-router';

import { haySesion, suscribirse } from '../api/sesion';
import { useSesion, useSesionIniciada } from '../api/useSesion';
import { ServidorDespertando } from '../componentes/ServidorDespertando';
import { T } from '../i18n/es';
import { Marco } from '../navegacion/Marco';
import { disponibles } from '../navegacion/secciones';
import { Login } from '../paginas/Login';

/** Lo que el login lee para volver a donde se quería ir. */
export interface EstadoDeVuelta {
  desde?: string;
}

function AlLogin() {
  const { pathname, search } = useLocation();
  return <Navigate to="/" replace state={{ desde: `${pathname}${search}` } satisfies EstadoDeVuelta} />;
}

function PaginaDeError({ titulo, detalle, volver }: { titulo: string; detalle?: string; volver: string }) {
  return (
    <div className="nx-centrado">
      <div className="nx-tarjeta nx-formulario">
        <h1>{titulo}</h1>
        {detalle !== undefined && <p>{detalle}</p>}
        <Link to="/fichar">{volver}</Link>
      </div>
    </div>
  );
}

function NoEncontrado() {
  return <PaginaDeError titulo={T.noEncontrado.titulo} volver={T.noEncontrado.volver} />;
}

/** La guarda por authority. Sin `authority`, deja pasar a cualquiera con sesión. */
export function Requiere({ authority, children }: { authority: string | undefined; children: ReactNode }) {
  const { puede } = useSesion();
  if (authority !== undefined && !puede(authority)) {
    return <PaginaDeError titulo={T.sinPermiso.titulo} detalle={T.sinPermiso.detalle} volver={T.sinPermiso.volver} />;
  }
  return <>{children}</>;
}

/**
 * Al cerrar la sesión se tira la caché de datos.
 *
 * Sin esto, quien entre después en el mismo navegador vería un instante la
 * jornada y los avisos del anterior, hasta que llegaran los suyos.
 */
function useVaciarCacheAlSalir() {
  const consultas = useQueryClient();
  useEffect(
    () =>
      suscribirse(() => {
        if (!haySesion()) consultas.clear();
      }),
    [consultas],
  );
}

export function App() {
  const dentro = useSesionIniciada();
  useVaciarCacheAlSalir();
  const secciones = disponibles();

  return (
    <div className="nx-fondo">
      <ServidorDespertando />
      <Routes>
        <Route path="/" element={dentro ? <Navigate to="/fichar" replace /> : <Login />} />
        {dentro ? (
          <Route element={<Marco />}>
            {secciones.map(({ ruta, requiere, pagina: Pagina }) => (
              <Route
                key={ruta}
                path={ruta}
                element={<Requiere authority={requiere}>{Pagina !== undefined && <Pagina />}</Requiere>}
              />
            ))}
            <Route path="*" element={<NoEncontrado />} />
          </Route>
        ) : (
          <>
            {secciones.map(({ ruta }) => (
              <Route key={ruta} path={ruta} element={<AlLogin />} />
            ))}
            <Route path="*" element={<NoEncontrado />} />
          </>
        )}
      </Routes>
    </div>
  );
}
