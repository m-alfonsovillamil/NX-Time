/**
 * Una jornada entera desde el navegador, contra el backend de verdad.
 *
 * Entra, ficha, pausa, reanuda y sale. Lo que demuestra no es que los botones
 * se pinten —eso ya lo dicen los tests de Vitest— sino que **la web y el
 * backend hablan el mismo idioma**: el cuerpo del login, los cuatro tipos de
 * fichaje, el 204 de "no hay jornada activa" y la forma de la respuesta.
 *
 * Ninguna respuesta está simulada aquí. En este proyecto los defectos que se
 * han escapado —el `contrasena` que no era `password`, la revocación que la
 * excepción deshacía, la cadena de auditoría con carrera— salieron todos
 * ejecutando el sistema, no leyendo el código.
 *
 * Usa un EMPLEADO y no el ADMIN a propósito: es el rol con menos permisos, así
 * que si esta pantalla necesitara alguno que no tiene, aquí se vería.
 */

import { expect, test } from '@playwright/test';

const EMPLEADO = { email: 'javier.lopez@techcorp.demo', contrasena: 'demo1234' };

test('una jornada completa: entrar, fichar, pausar, reanudar y salir', async ({ page }) => {
  await page.goto('/');

  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  // Si el usuario de demostración viene con una jornada abierta de otra
  // ejecución, se cierra antes: el backend impide dos abiertas a la vez, y el
  // test tiene que poder repetirse sin limpiar la base a mano.
  if (await page.getByRole('button', { name: 'Fichar salida' }).isVisible()) {
    await page.getByRole('button', { name: 'Fichar salida' }).click();
    await expect(page.getByRole('button', { name: 'Fichar entrada' })).toBeVisible();
  }

  await test.step('fichar la entrada arranca el cronómetro', async () => {
    await page.getByRole('button', { name: 'Fichar entrada' }).click();
    await expect(page.getByText('Trabajando')).toBeVisible();
    // El cronómetro pinta algo con forma de duración, no un guion.
    await expect(page.getByText(/^\d+[hm]/)).toBeVisible();
  });

  await test.step('pausar y reanudar lo dice el servidor, no la pantalla', async () => {
    await page.getByRole('button', { name: 'Pausar' }).click();
    await expect(page.getByText('En pausa')).toBeVisible();

    await page.getByRole('button', { name: 'Reanudar' }).click();
    await expect(page.getByText('Trabajando')).toBeVisible();
  });

  await test.step('fichar la salida cierra la jornada', async () => {
    await page.getByRole('button', { name: 'Fichar salida' }).click();
    await expect(page.getByText('Sin fichar')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Fichar entrada' })).toBeVisible();
  });
});

/*
 * La otra mitad de la decisión del ADR 020: los tokens viven en memoria, así
 * que recargar cierra la sesión. Se comprueba porque es una consecuencia
 * asumida y no un descuido -- el día que se cambie a cookie con dominio
 * propio, este test tendrá que cambiar, y eso es justo lo que se quiere.
 */
test('recargar la pagina cierra la sesion, como dice el ADR 020', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.reload();

  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
});

/*
 * El armazón de W0 (ADR 029) contra el backend de verdad: el menú sale de
 * las authorities que manda el login, la campana pide su contador, y cerrar
 * sesión está en el menú de usuario.
 */
test('el marco: menu por authorities, campana y cerrar sesion', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  // Escritorio: se ve la barra lateral, con la sección actual marcada.
  const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
  await expect(menu.getByRole('link', { name: 'Mi jornada' })).toHaveAttribute('aria-current', 'page');
  // Un EMPLEADO no tiene ninguna authority de gestión.
  await expect(page.getByRole('heading', { name: 'Gestión' })).toHaveCount(0);

  // El contador de la campana viene de /avisos/no-leidos: la etiqueta dice cuántos.
  await expect(page.getByRole('button', { name: /^Avisos: / })).toBeVisible();

  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
});

test('un enlace a una seccion sin sesion pasa por el login y vuelve a ella', async ({ page }) => {
  await page.goto('/fichar');
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();

  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page).toHaveURL(/\/fichar$/);
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
});

test('una ruta que no existe no rompe la aplicacion', async ({ page }) => {
  await page.goto('/no-existe');
  await expect(page.getByRole('heading', { name: 'Esta página no existe' })).toBeVisible();
});
