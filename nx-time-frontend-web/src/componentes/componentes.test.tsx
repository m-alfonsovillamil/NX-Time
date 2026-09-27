/**
 * Los componentes con comportamiento propio: la tabla, las pestañas, el final de
 * una lista y el diálogo que no se deja cerrar.
 */

import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { T } from '../i18n/es';
import { Dialogo } from './Dialogo';
import { FinDeLista } from './Estados';
import { Pestanas } from './Pestanas';
import { Tabla } from './Tabla';

describe('Tabla', () => {
  /* Lo que hace que en el móvil cada dato lleve delante el nombre de su columna. */
  it('cada celda lleva el nombre de su columna', () => {
    render(
      <Tabla
        titulo="Horas del equipo"
        columnas={[
          { clave: 'nombre', cabecera: 'Persona', celda: (f: { n: string; h: string }) => f.n },
          { clave: 'horas', cabecera: 'Horas', celda: (f) => f.h, numerica: true },
        ]}
        filas={[{ n: 'Ana', h: '7h 30m' }]}
        claveDeFila={(f) => f.n}
      />,
    );

    expect(screen.getByRole('table', { name: 'Horas del equipo' })).toBeTruthy();
    expect(screen.getByRole('cell', { name: '7h 30m' }).getAttribute('data-etiqueta')).toBe('Horas');
  });
});

describe('Pestanas', () => {
  function Prueba() {
    const [activa, setActiva] = useState<'a' | 'b' | 'c'>('a');
    return (
      <Pestanas
        etiqueta="Estado"
        pestanas={[
          { clave: 'a', texto: 'Pendientes' },
          { clave: 'b', texto: 'Aprobadas' },
          { clave: 'c', texto: 'Rechazadas' },
        ]}
        activa={activa}
        alCambiar={setActiva}
      >
        <p>Panel {activa}</p>
      </Pestanas>
    );
  }

  it('solo la activa entra en el orden de tabulación', () => {
    render(<Prueba />);
    const pestanas = screen.getAllByRole('tab');
    expect(pestanas.map((p) => p.tabIndex)).toEqual([0, -1, -1]);
  });

  it('las flechas cambian de pestaña y dan la vuelta', async () => {
    render(<Prueba />);
    screen.getByRole('tab', { name: 'Pendientes' }).focus();

    await userEvent.keyboard('{ArrowLeft}');
    expect(screen.getByRole('tab', { name: 'Rechazadas' }).getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(screen.getByRole('tab', { name: 'Rechazadas' }));
    expect(screen.getByRole('tabpanel').textContent).toBe('Panel c');

    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Pendientes' }).getAttribute('aria-selected')).toBe('true');
  });
});

describe('FinDeLista', () => {
  it('sin más páginas no pinta nada', () => {
    const { container } = render(<FinDeLista hayMas={false} cargando={false} fallo={false} alPedirMas={vi.fn()} />);
    expect(container.textContent).toBe('');
  });

  /* En jsdom no hay IntersectionObserver: queda el botón. */
  it('sin IntersectionObserver ofrece cargar más', async () => {
    const pedir = vi.fn();
    render(<FinDeLista hayMas cargando={false} fallo={false} alPedirMas={pedir} />);
    await userEvent.click(screen.getByRole('button', { name: T.listas.cargarMas }));
    expect(pedir).toHaveBeenCalledTimes(1);
  });

  it('si la página falla, se para y ofrece reintentar', async () => {
    const pedir = vi.fn();
    render(<FinDeLista hayMas cargando={false} fallo alPedirMas={pedir} />);
    expect(screen.getByRole('alert').textContent).toContain(T.listas.falloAlCargarMas);
    await userEvent.click(screen.getByRole('button', { name: T.app.reintentar }));
    expect(pedir).toHaveBeenCalledTimes(1);
  });
});

describe('Dialogo obligatorio', () => {
  /* El código de una denuncia anónima no se vuelve a ver: cerrarlo sin querer no puede pasar. */
  it('ni Escape ni un clic fuera lo cierran; su botón sí', async () => {
    const alCerrar = vi.fn();
    render(
      <Dialogo abierto titulo="Tu código" alCerrar={alCerrar} obligatorio acciones={<button onClick={alCerrar}>Ya lo he guardado</button>}>
        <p>ABC-123</p>
      </Dialogo>,
    );
    const dialogo = screen.getByRole('dialog');

    const escape = new Event('cancel', { cancelable: true });
    dialogo.dispatchEvent(escape);
    expect(escape.defaultPrevented).toBe(true);
    fireEvent.click(dialogo);
    expect(alCerrar).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Ya lo he guardado' }));
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });

  it('uno normal sí se cierra con un clic fuera', () => {
    const alCerrar = vi.fn();
    render(
      <Dialogo abierto titulo="Algo" alCerrar={alCerrar}>
        <p>Contenido</p>
      </Dialogo>,
    );
    fireEvent.click(screen.getByRole('dialog'));
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });
});
