/**
 * Importar la plantilla desde un CSV, de punta a punta (plan K; ADR 052): el
 * ADMIN sube el fichero, ve la comprobación, crea a las personas y les cambia
 * el rol, con su código de empleado (ADR 053). Las reglas finas (todo o nada, permisos de cada columna, tandas de
 * 50 invitaciones) las prueba `PlantillaImportIT`.
 *
 * Escribe en la base: cada vuelta con correos nuevos, para poder repetirla.
 */

import { expect, test } from '@playwright/test';

import { entrarYCerrarElAviso, irASeccion } from './ayudas';

test('un ADMIN importa dos personas desde un CSV y le cambia el rol a una', async ({ page }) => {
  const vuelta = Date.now();
  const csv = [
    'Nombre;Apellidos;Correo electrónico;Rol;Departamento;Horas;Vacaciones;Nº empleado',
    `Irene;Importada ${vuelta};irene.${vuelta}@techcorp.demo;;ingenieria;37,5;23;E2E-${vuelta}`,
    `Óscar;Importado ${vuelta};oscar.${vuelta}@techcorp.demo;gestor;Producto;;;`,
  ].join('\n');

  await entrarYCerrarElAviso(page, 'raul.ortega@techcorp.demo');
  await irASeccion(page, 'Plantilla');
  await page.getByRole('button', { name: 'Importar desde Excel o CSV' }).click();

  const dialogo = page.getByRole('dialog', { name: 'Importar la plantilla' });
  await dialogo.getByLabel('Fichero').setInputFiles({
    name: 'plantilla.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf-8'),
  });
  await expect(dialogo.getByText('2 personas, todo correcto.')).toBeVisible();
  await expect(dialogo.getByRole('cell', { name: `oscar.${vuelta}@techcorp.demo` })).toBeVisible();
  // El código de empleado (ADR 053), con el nombre que le da un programa de nóminas.
  await expect(dialogo.getByRole('cell', { name: `E2E-${vuelta}` })).toBeVisible();

  await dialogo.getByRole('button', { name: 'Crear 2 personas' }).click();
  await expect(dialogo.getByText('2 personas creadas.')).toBeVisible();
  await dialogo.getByRole('button', { name: 'Cerrar' }).click();

  const tabla = page.getByRole('table', { name: 'Personas de la empresa' });
  const irene = tabla.getByRole('row').filter({ hasText: `irene.${vuelta}@techcorp.demo` });
  await expect(irene).toContainText('Ingeniería');
  await expect(irene).toContainText('37,5');
  await expect(irene).toContainText(`E2E-${vuelta}`);
  await expect(tabla.getByRole('row').filter({ hasText: `oscar.${vuelta}@techcorp.demo` })).toContainText('Gestor');

  await irene.getByRole('button', { name: 'Cambiar rol' }).click();
  const rol = page.getByRole('dialog', { name: 'Rol de Irene' });
  await rol.getByLabel('Rol').selectOption('RRHH');
  await rol.getByRole('button', { name: 'Guardar' }).click();
  await expect(page.getByRole('status').getByText('Irene es ahora RRHH.')).toBeVisible();
  await expect(irene).toContainText('RRHH');
});
