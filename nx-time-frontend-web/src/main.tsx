import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';

import '@fontsource/sora/400.css';
import '@fontsource/sora/600.css';
import '@fontsource/sora/700.css';
import './estilos/tokens.css';
import './estilos/base.css';

import { crearClienteDeConsultas } from './api/consultas';
import { aplicarTema, temaGuardado } from './util/tema';
import { App } from './rutas/rutas';

/*
 * La web tiene una sola dirección buena (ADR 030). Quien entre por otra —la de
 * Render, nxtime-web.onrender.com, que sigue existiendo— acaba en la buena:
 * desde otra dirección la sesión en cookie no funcionaría, y el CORS del
 * backend ni siquiera la admite. En local no hay dirección canónica y no pasa
 * nada.
 */
const canonica = import.meta.env['VITE_URL_CANONICA'];
if (canonica && globalThis.location.origin !== canonica) {
  const { pathname, search, hash } = globalThis.location;
  globalThis.location.replace(`${canonica}${pathname}${search}${hash}`);
}

// Antes de pintar nada: si no, quien eligió el oscuro vería un fogonazo claro.
aplicarTema(temaGuardado(), { guardar: false });

const raiz = document.getElementById('raiz');
if (raiz === null) throw new Error('Falta <div id="raiz"> en index.html.');

createRoot(raiz).render(
  <StrictMode>
    <QueryClientProvider client={crearClienteDeConsultas()}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
