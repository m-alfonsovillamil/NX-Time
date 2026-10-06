/**
 * Las piezas del sistema visual (plan del 5/10/2026): la cabecera de página,
 * las cifras, el estado vacío y las iniciales. Lo que se prueba es lo que
 * oye un lector de pantalla y a dónde lleva cada cosa, no el aspecto.
 */

import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { T } from '../i18n/es';
import { CabeceraDePagina } from './CabeceraDePagina';
import { Cifra, Cifras } from './Cifra';
import { Vacio } from './Estados';
import { iniciales } from './Iniciales';

const N = T.navegacion;

function enRuta(ruta: string, ui: React.ReactElement) {
  return render(<MemoryRouter initialEntries={[ruta]}>{ui}</MemoryRouter>);
}

describe('CabeceraDePagina', () => {
  it('dice de qué grupo y apartado cuelga la página, por la URL', () => {
    enRuta('/integridad', <CabeceraDePagina titulo="Integridad de la auditoría" />);

    expect(screen.getByRole('heading', { level: 1, name: 'Integridad de la auditoría' })).toBeTruthy();
    const miga = screen.getByText((_, el) => el?.classList.contains('nx-cabecera-pagina__miga') === true);
    expect(miga.textContent).toContain(N.grupos.gestion);
    expect(miga.textContent).toContain(N.subgrupos.control);
  });

  it('en una ruta más larga (un detalle) sigue encontrando su sección', () => {
    enRuta('/cuadrantes/3', <CabeceraDePagina titulo="Turno de mañana" />);
    expect(screen.getByText((_, el) => el?.classList.contains('nx-cabecera-pagina__miga') === true).textContent).toContain(
      N.subgrupos.organizacion,
    );
  });

  it('sin sección conocida no inventa miga, y la descripción y los botones salen', () => {
    enRuta('/no-existe', <CabeceraDePagina titulo="Algo" descripcion="Para qué sirve." acciones={<button>Hacer</button>} />);

    expect(document.querySelector('.nx-cabecera-pagina__miga')).toBeNull();
    expect(screen.getByText('Para qué sirve.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Hacer' })).toBeTruthy();
  });
});

describe('Cifra', () => {
  it('se oye «2 Ausencias por aprobar»: la cifra delante, aunque se pinte debajo', () => {
    enRuta(
      '/',
      <Cifras>
        <Cifra etiqueta="Ausencias por aprobar" valor={2} icono="ausencia" a="/ausencias-equipo/pendientes" />
      </Cifras>,
    );

    const enlace = screen.getByRole('link', { name: '2 Ausencias por aprobar' });
    expect(enlace.getAttribute('href')).toBe('/ausencias-equipo/pendientes');
  });

  it('sin enlace no es un enlace, y el tono de alerta se marca', () => {
    enRuta('/', <Cifras><Cifra etiqueta="Incidencias abiertas" valor={3} tono="alerta" detalle="Sin fichaje de salida" /></Cifras>);

    expect(screen.queryByRole('link')).toBeNull();
    expect(screen.getByText('Incidencias abiertas').closest('.nx-cifra--alerta')).not.toBeNull();
    expect(screen.getByText('Sin fichaje de salida')).toBeTruthy();
  });

  it('la barra de progreso no se pasa del 100 % aunque el dato sí', () => {
    enRuta('/', <Cifras><Cifra etiqueta="Esta semana" valor="45h" progreso={1.2} /></Cifras>);

    const relleno = document.querySelector<HTMLElement>('.nx-cifra__relleno');
    expect(relleno?.style.width).toBe('100%');
    expect(relleno?.classList.contains('nx-cifra__relleno--pasado')).toBe(true);
  });
});

describe('Vacio', () => {
  it('lleva título, detalle y su acción', () => {
    render(<Vacio titulo="No hay nada pendiente" detalle="Cuando llegue algo, sale aquí." accion={<button>Crear</button>} />);

    expect(screen.getByText('No hay nada pendiente')).toBeTruthy();
    expect(screen.getByText('Cuando llegue algo, sale aquí.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Crear' })).toBeTruthy();
    // El icono es decoración: no añade nada a lo que se lee.
    expect(document.querySelector('.nx-vacio svg')?.getAttribute('aria-hidden')).toBe('true');
  });
});

describe('iniciales', () => {
  it('las dos primeras palabras, en mayúscula', () => {
    expect(iniciales('Ana Fernández López')).toBe('AF');
    expect(iniciales('raúl')).toBe('R');
    expect(iniciales('  Lucía   Moreno ')).toBe('LM');
    expect(iniciales('')).toBe('');
  });
});
