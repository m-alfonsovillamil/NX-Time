/**
 * Las rutas, y la que protege al resto.
 *
 * `useSyncExternalStore` y no un contexto: la sesión vive en un módulo porque
 * `cliente.ts` tiene que leerla y escribirla desde fuera de React (ver
 * `api/sesion.ts`). Esto es lo que conecta ese módulo con el árbol de
 * componentes sin duplicar el estado en los dos sitios.
 *
 * Con eso, cuando el refresco falla y `cliente.ts` cierra la sesión, **esta
 * pantalla se entera sola** y lleva al login. Sin ello pasaría lo que pasaba
 * en la app antes de que existiera `sesionCaducada`: la sesión muerta y la
 * pantalla enseñando el nombre cacheado, sin forma de volver a entrar.
 */

import { useSyncExternalStore, type ReactNode } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router';

import { haySesion, suscribirse } from '../api/sesion';
import { ServidorDespertando } from '../componentes/ServidorDespertando';
import { T } from '../i18n/es';
import { Fichar } from '../paginas/Fichar';
import { Login } from '../paginas/Login';

export function useSesionIniciada(): boolean {
  return useSyncExternalStore(suscribirse, haySesion, () => false);
}

function Protegida({ children }: { children: ReactNode }) {
  return useSesionIniciada() ? <>{children}</> : <Navigate to="/" replace />;
}

function NoEncontrado() {
  return (
    <main className="nx-centrado">
      <div className="nx-tarjeta">
        <h1>{T.noEncontrado.titulo}</h1>
        <Link to="/fichar">{T.noEncontrado.volver}</Link>
      </div>
    </main>
  );
}

export function App() {
  const dentro = useSesionIniciada();

  return (
    <div className="nx-fondo">
      <ServidorDespertando />
      <Routes>
        <Route path="/" element={dentro ? <Navigate to="/fichar" replace /> : <Login />} />
        <Route
          path="/fichar"
          element={
            <Protegida>
              <Fichar />
            </Protegida>
          }
        />
        <Route path="*" element={<NoEncontrado />} />
      </Routes>
    </div>
  );
}
