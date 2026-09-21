import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';

import '@fontsource/sora/400.css';
import '@fontsource/sora/600.css';
import '@fontsource/sora/700.css';
import './estilos/tokens.css';
import './estilos/base.css';

import { App } from './rutas/rutas';

const raiz = document.getElementById('raiz');
if (raiz === null) throw new Error('Falta <div id="raiz"> en index.html.');

createRoot(raiz).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
