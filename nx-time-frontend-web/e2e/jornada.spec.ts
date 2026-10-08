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
    if (await page.getByRole('button', { name: 'Reanudar' }).isVisible()) {
      await page.getByRole('button', { name: 'Reanudar' }).click();
    }
    await page.getByRole('button', { name: 'Fichar salida' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Terminar' }).click();
    await expect(page.getByRole('button', { name: 'Fichar entrada' })).toBeVisible();
  }

  await test.step('fichar la entrada arranca el cronómetro', async () => {
    await page.getByRole('button', { name: 'Fichar entrada' }).click();
    await expect(page.getByText('Trabajando')).toBeVisible();
    // El cronómetro pinta algo con forma de duración, no un guion.
    await expect(page.locator('.nx-jornada__cronometro')).toHaveText(/^\d+[hm]/);
  });

  await test.step('pausar y reanudar lo dice el servidor, no la pantalla', async () => {
    await page.getByRole('button', { name: 'Pausar' }).click();
    await expect(page.getByText('En pausa', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Reanudar' }).click();
    await expect(page.getByText('Trabajando')).toBeVisible();
  });

  await test.step('pausado, el cronómetro no avanza', async () => {
    await page.getByRole('button', { name: 'Pausar' }).click();
    await expect(page.getByText(/^En pausa desde las/)).toBeVisible();
    const congelado = await page.locator('.nx-jornada__cronometro').textContent();
    await page.waitForTimeout(2_000);
    await expect(page.locator('.nx-jornada__cronometro')).toHaveText(congelado ?? '');
    await page.getByRole('button', { name: 'Reanudar' }).click();
    await expect(page.getByText('Trabajando')).toBeVisible();
  });

  await test.step('fichar la salida pide confirmación y cierra la jornada', async () => {
    await page.getByRole('button', { name: 'Fichar salida' }).click();
    const dialogo = page.getByRole('dialog', { name: '¿Terminas la jornada?' });
    await expect(dialogo).toBeVisible();
    await dialogo.getByRole('button', { name: 'Terminar' }).click();
    await expect(page.getByText('Sin fichar')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Fichar entrada' })).toBeVisible();
  });
});

/*
 * Lo que cambia con la fase W1 (ADR 030, que sustituye al 020): el refresh va
 * en una cookie HttpOnly, así que recargar ya no cierra la sesión. Hasta W1
 * este test decía lo contrario, y su comentario avisaba de que el día de la
 * cookie tendría que cambiar.
 */
/**
 * «Mi jornada» se abre para fichar y ver cómo va el día: en un portátil tiene
 * que verse entera, sin desplazarse. A 1440×900 no cabía (sobraban 58 px sin
 * fichar y 146 con la jornada en curso) hasta que se apretó para ventanas
 * anchas y bajas (8/10/2026, `.nx-pagina--jornada` en base.css).
 *
 * Contra el navegador de verdad porque es lo único que lo mide: jsdom no
 * calcula alturas, así que un test de Vitest no vería que una tarjeta nueva
 * vuelve a empujar el resumen fuera de la pantalla.
 */
test('en un portatil a 1440×900 «Mi jornada» cabe entera sin desplazarse', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();

  // Con el resumen ya pintado y las fuentes cargadas: antes, la página mide menos.
  await expect(page.getByRole('heading', { name: 'Mi tiempo' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Mis horas' })).toBeVisible();
  await page.evaluate(() => document.fonts.ready);

  const sobra = await page.evaluate(() => document.documentElement.scrollHeight - window.innerHeight);
  expect(sobra, 'píxeles que hay que desplazarse para ver el final de la página').toBeLessThanOrEqual(0);
});

test('recargar la pagina mantiene la sesion', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  // Una pestaña nueva también entra: la cookie es del navegador, no de la pestaña.
  const otra = await page.context().newPage();
  await otra.goto('/fichar');
  await expect(otra.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  // El refresh no está al alcance del JavaScript de la página: es HttpOnly.
  const cookies = await page.context().cookies();
  expect(cookies.find((c) => c.name === 'nx_refresh')?.httpOnly).toBe(true);
  expect(await page.evaluate(() => document.cookie)).not.toContain('nx_refresh');
});

test('cerrar sesion y recargar deja fuera', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(EMPLEADO.email);
  await page.getByLabel('Contraseña').fill(EMPLEADO.contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();

  await page.getByLabel(/^Menú de /).click();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();

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
