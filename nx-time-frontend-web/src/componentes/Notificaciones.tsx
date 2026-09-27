/**
 * Las confirmaciones que aparecen un momento y se van: «Ausencia solicitada».
 *
 * Solo para lo que ha ido **bien**. Un error se enseña al lado de lo que lo
 * ha causado y se queda ahí (ver `useMutacion`): un mensaje que desaparece a
 * los cinco segundos es justo lo contrario de lo que necesita quien tiene que
 * arreglar algo.
 *
 * Es un módulo con estado, como la sesión, para que [notificar] se pueda
 * llamar desde fuera de un componente (lo llama `useMutacion` al terminar).
 * `aria-live="polite"` hace que un lector de pantalla lo lea sin interrumpir.
 *
 * No se llama «avisos» a propósito: en NX Time un aviso es otra cosa, lo que
 * llega a la campana y se guarda en el servidor.
 */

import { useSyncExternalStore } from 'react';

const DURACION_MS = 5_000;

interface Notificacion {
  id: number;
  texto: string;
}

let lista: readonly Notificacion[] = [];
let siguienteId = 1;
const oyentes = new Set<() => void>();

function cambiar(nueva: readonly Notificacion[]): void {
  lista = nueva;
  for (const oyente of oyentes) oyente();
}

export function notificar(texto: string): void {
  const id = siguienteId++;
  cambiar([...lista, { id, texto }]);
  setTimeout(() => cambiar(lista.filter((n) => n.id !== id)), DURACION_MS);
}

function suscribirse(oyente: () => void): () => void {
  oyentes.add(oyente);
  return () => {
    oyentes.delete(oyente);
  };
}

export function Notificaciones() {
  const actuales = useSyncExternalStore(
    suscribirse,
    () => lista,
    () => lista,
  );
  // La región existe siempre, vacía o no: un lector de pantalla solo anuncia
  // cambios en una región viva que ya estaba en la página.
  return (
    <div className="nx-notificaciones" role="status" aria-live="polite">
      {actuales.map((n) => (
        <p key={n.id} className="nx-notificacion">
          {n.texto}
        </p>
      ))}
    </div>
  );
}
