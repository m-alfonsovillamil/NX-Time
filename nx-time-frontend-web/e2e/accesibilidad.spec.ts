/**
 * Accesibilidad de TODAS las páginas, con axe, contra el backend de verdad
 * (fase W8).
 *
 * No hay una lista de páginas escrita aquí: cada cuenta de demo entra y recorre
 * los enlaces de su propio menú, que sale del catálogo (`secciones.ts`) filtrado
 * por sus authorities. Una página nueva entra sola en la comprobación, y las
 * cuatro cuentas juntas (EMPLEADO, GESTOR, RRHH y ADMIN) cubren todas.
 *
 * Se mira en tema claro y en oscuro, porque el contraste de uno no dice nada
 * del otro: son dos juegos de colores distintos en `tokens.css`.
 *
 * Reglas: WCAG 2.1 A y AA, que es lo que pide la norma europea (EN 301 549) y
 * lo que el plan fijó. `expect.soft` para que una ejecución enseñe TODO lo que
 * falla y no solo la primera página.
 */

import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const REGLAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const CUENTAS = [
  { rol: 'EMPLEADO', email: 'javier.lopez@techcorp.demo' },
  { rol: 'GESTOR', email: 'marta.sanchez@techcorp.demo' },
  { rol: 'RRHH', email: 'elena.rios@techcorp.demo' },
  { rol: 'ADMIN', email: 'raul.ortega@techcorp.demo' },
];

/** Espera a que la página haya terminado de cargar lo que pide. */
async function asentada(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle');
  await expect(page.locator('.nx-esqueleto')).toHaveCount(0);
}

async function sinInfracciones(page: Page, donde: string): Promise<void> {
  const { violations } = await new AxeBuilder({ page }).withTags(REGLAS).analyze();
  const resumen = violations.map(
    (v) =>
      `${v.id} (${v.impact ?? '?'}): ${v.help}\n` +
      v.nodes
        .slice(0, 3)
        .map((n) => `    ${n.target.join(' ')}\n      ${n.failureSummary?.split('\n').join('\n      ') ?? ''}`)
        .join('\n'),
  );
  expect.soft(resumen, `accesibilidad de ${donde}`).toEqual([]);
}

async function entrar(page: Page, email: string): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill('demo1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Mi jornada' })).toBeVisible();
}

for (const tema of ['light', 'dark'] as const) {
  test.describe(`tema ${tema === 'light' ? 'claro' : 'oscuro'}`, () => {
    test.use({ colorScheme: tema });

    test('las páginas sin sesión', async ({ page }) => {
      for (const ruta of ['/', '/recuperar-acceso', '/registro']) {
        await page.goto(ruta);
        await asentada(page);
        await sinInfracciones(page, ruta);
      }
    });

    // El recorrido de arriba ve las páginas, no lo que se abre encima. Estos
    // dos son los formularios en diálogo más cargados (horas, proyectos).
    test('los formularios en diálogo de una jornada', async ({ page }) => {
      await entrar(page, 'javier.lopez@techcorp.demo');
      await page.getByRole('navigation', { name: 'Menú principal' }).first().getByRole('link', { name: 'Historial' }).click();
      await expect(page.getByRole('table', { name: 'Mis jornadas' })).toBeVisible();
      const jornada = page.getByRole('group', { name: /^Acciones de la jornada del / }).first();

      for (const [boton, titulo] of [
        ['Pedir corrección', 'Pedir corrección'],
        ['Proyectos', 'Repartir por proyecto'],
      ] as const) {
        await jornada.getByRole('button', { name: boton }).click();
        const dialogo = page.getByRole('dialog', { name: titulo });
        await expect(dialogo).toBeVisible();
        await asentada(page);
        await sinInfracciones(page, `el diálogo «${titulo}»`);
        await page.keyboard.press('Escape');
        await expect(dialogo).toBeHidden();
      }
    });

    for (const { rol, email } of CUENTAS) {
      test(`todas las páginas del menú de ${rol}`, async ({ page }) => {
        await entrar(page, email);
        const menu = page.getByRole('navigation', { name: 'Menú principal' }).first();
        const rutas = await menu
          .getByRole('link')
          .evaluateAll((enlaces) => enlaces.map((a) => a.getAttribute('href') ?? ''));
        expect(rutas.length).toBeGreaterThan(3);

        for (const ruta of rutas) {
          await menu.locator(`a[href="${ruta}"]`).click();
          await expect(page).toHaveURL(new RegExp(`${ruta}(\\?.*)?$`));
          await asentada(page);
          await sinInfracciones(page, `${ruta} (${rol})`);
        }
      });
    }
  });
}
