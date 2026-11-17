/**
 * Pedir un medio día (ADR 063) en un navegador y contra el backend de verdad:
 * la casilla solo se deja marcar con un único día, la petición sale contando
 * «Medio día» y el saldo pasa a llevar decimales.
 *
 * Escribe en la base de demo, pero lo deja como estaba: Diego retira la
 * petición al acabar, y una pendiente propia retirada no avisa a nadie.
 */

import { expect, test } from '@playwright/test';

import { entrar, irASeccion } from './ayudas';

/** Festivos nacionales de fecha fija: el día elegido tiene que ser hábil. */
const FESTIVOS = new Set(['01-01', '01-06', '05-01', '08-15', '10-12', '11-01', '12-06', '12-08', '12-25']);

/**
 * El primer martes, a partir de dentro de un mes, que no sea festivo. Martes,
 * porque los festivos que se mueven (Semana Santa) caen en jueves y viernes.
 */
function unMartesHabil(): string {
  const hoy = new Date();
  // A un mes: las peticiones que siembra la demo caen entre hoy+15 y hoy+18.
  const dia = new Date(Date.UTC(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() + 30));
  for (;;) {
    const iso = dia.toISOString().slice(0, 10);
    if (dia.getUTCDay() === 2 && !FESTIVOS.has(iso.slice(5))) return iso;
    dia.setUTCDate(dia.getUTCDate() + 1);
  }
}

test('una persona pide un medio día, lo ve contado como medio y lo retira', async ({ page }) => {
  const martes = unMartesHabil();
  const miercoles = new Date(Date.parse(martes) + 86_400_000).toISOString().slice(0, 10);
  const anio = martes.slice(0, 4);
  // Como la pinta la tabla (`fechaCorta`): «mar, 1 dic».
  const fecha = new Intl.DateTimeFormat('es-ES', { timeZone: 'UTC', weekday: 'short', day: 'numeric', month: 'short' }).format(
    new Date(`${martes}T00:00:00Z`),
  );

  await entrar(page, 'diego.vazquez@iberica.demo');
  await irASeccion(page, 'Ausencias');
  await expect(page.getByRole('heading', { name: /^Vacaciones de \d{4}$/ })).toBeVisible();

  await page.getByRole('button', { name: 'Solicitar ausencia' }).click();
  const dialogo = page.getByRole('dialog', { name: 'Solicitar ausencia' });
  const casilla = dialogo.getByLabel('Solo medio día');
  // Sin fechas no hay un único día del que pedir la mitad.
  await expect(casilla).toBeDisabled();

  await dialogo.getByLabel('Fecha de inicio').fill(martes);
  await expect(dialogo.getByLabel('Fecha de fin')).toHaveValue(martes);
  await casilla.check();
  // Con dos días se apaga sola; de vuelta a uno, sigue como se dejó.
  await dialogo.getByLabel('Fecha de fin').fill(miercoles);
  await expect(casilla).toBeDisabled();
  await expect(casilla).not.toBeChecked();
  await dialogo.getByLabel('Fecha de fin').fill(martes);
  await expect(casilla).toBeChecked();

  await dialogo.getByRole('button', { name: 'Enviar solicitud' }).click();
  await expect(page.getByRole('status').getByText(/^Solicitud enviada/)).toBeVisible();

  const tabla = page.getByRole('table', { name: 'Mis solicitudes de ausencia' });
  // La más reciente primero: si la base ya tenía una retirada de ese día
  // (otra pasada de esta spec), la nueva es la de arriba.
  const fila = tabla.getByRole('row').filter({ hasText: fecha }).first();
  try {
    await expect(fila).toContainText('Medio día');
    await expect(fila).toContainText('Vacaciones');
    await expect(fila).toContainText('Pendiente');
    // Si el martes es de este año, el saldo lo enseña ya con su medio día.
    if (anio === String(new Date().getFullYear())) {
      await expect(page.getByText('0,5 días', { exact: true })).toBeVisible();
    }
  } finally {
    await fila.getByRole('button', { name: 'Cancelar la ausencia' }).click();
    const confirmar = page.getByRole('dialog', { name: '¿Cancelar esta ausencia?' });
    await confirmar.getByRole('button', { name: 'Cancelar la ausencia' }).click();
    await expect(page.getByRole('status').getByText('Ausencia cancelada.')).toBeVisible();
  }
  await expect(fila).toContainText('Cancelada');
});
