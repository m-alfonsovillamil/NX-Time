/**
 * Un círculo con las iniciales de alguien: en las tablas de personas y en el menú de usuario.
 *
 * Una lista de nombres en texto plano se escanea mal; con un círculo delante
 * cada fila tiene un ancla para el ojo, como en la app. Es decorativo
 * (`aria-hidden`): el nombre completo va siempre al lado.
 */

/** «Ana Fernández López» -> «AF»; «Raúl» -> «R». */
export function iniciales(nombre: string): string {
  return nombre
    .trim()
    .split(/\s+/)
    .filter((p) => p !== '')
    .slice(0, 2)
    .map((p) => p.charAt(0).toLocaleUpperCase('es'))
    .join('');
}

/**
 * @param sinTexto las iniciales las pinta el CSS (`::before`) y no son texto
 *   del documento. Para dentro de un control con `aria-label`: si no, cuentan
 *   como texto visible que no está en su nombre («JL Javier» frente a «Menú de
 *   Javier»), y eso incumple WCAG 2.5.3 (regla label-content-name-mismatch).
 */
export function Iniciales({
  nombre,
  tamano = 'm',
  sinTexto = false,
}: {
  nombre: string;
  tamano?: 's' | 'm';
  sinTexto?: boolean;
}) {
  if (sinTexto) {
    return (
      <span className={`nx-iniciales nx-iniciales--${tamano} nx-iniciales--css`} data-iniciales={iniciales(nombre)} aria-hidden="true" />
    );
  }
  return (
    <span className={`nx-iniciales nx-iniciales--${tamano}`} aria-hidden="true">
      {iniciales(nombre)}
    </span>
  );
}
