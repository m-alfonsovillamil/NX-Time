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

export function Iniciales({ nombre, tamano = 'm' }: { nombre: string; tamano?: 's' | 'm' }) {
  return (
    <span className={`nx-iniciales nx-iniciales--${tamano}`} aria-hidden="true">
      {iniciales(nombre)}
    </span>
  );
}
