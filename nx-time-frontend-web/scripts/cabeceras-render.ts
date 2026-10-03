/**
 * Las cabeceras que Render pone a toda la web (las de `path: /*` en
 * `render.yaml`), para que `vite preview` mande las mismas.
 *
 * Hasta el 1/10/2026 la CSP solo existía en Render: los E2E corrían sin ella y
 * la foto de perfil (un `blob:` que la CSP no admitía) salía bien en todos los
 * tests y rota en producción. Con esto, los E2E del CI corren con la misma
 * política que producción.
 *
 * No es un lector de YAML: entiende el bloque `headers` tal como está escrito,
 * con `path`, `name` y `value` en tres líneas seguidas. Su test comprueba que
 * del `render.yaml` de verdad salen la CSP y la `Permissions-Policy`, así que
 * si alguien cambia el formato, falla ahí y no en silencio.
 */
export function cabecerasDeRender(renderYaml: string): Record<string, string> {
  const cabeceras: Record<string, string> = {};
  const regla = /-\s*path:\s*(\S+)\s*\n\s*name:\s*(.+?)\s*\n\s*value:\s*(.+?)\s*$/gm;
  for (const [, ruta, nombre, valor] of renderYaml.matchAll(regla)) {
    if (ruta !== '/*' || nombre === undefined || valor === undefined) continue;
    cabeceras[nombre] = valor.replace(/^"(.*)"$/, '$1');
  }
  return cabeceras;
}
