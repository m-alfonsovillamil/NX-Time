/**
 * Entrar con Google o con Microsoft (ADR 036): qué proveedores hay y qué decir
 * cuando no sale.
 *
 * **La web no hace el SSO: lo empieza.** Manda el navegador a la URL que da el
 * servidor (una navegación de página entera, no un `fetch`), y el servidor lo
 * devuelve aquí con la sesión ya puesta en sus cookies o con un motivo en la
 * URL (`?sso=sin-cuenta`). Por eso aquí no hay ni un token ni un secreto.
 */

import { useEffect, useState } from 'react';

import { acceso } from '../i18n/es/acceso';
import { cliente } from './cliente';

export interface ProveedorSso {
  id: string;
  nombre: string;
  /** Adónde hay que mandar el navegador. Absoluta: la da el servidor. */
  inicio: string;
}

/**
 * Los proveedores configurados en el servidor. **Vacío mientras no se sabe y
 * vacío si falla**: sin la respuesta no hay botones, y la pantalla de acceso es
 * la de siempre. Entrar con contraseña no puede depender de esto.
 */
export function useProveedoresSso(): ProveedorSso[] {
  const [proveedores, setProveedores] = useState<ProveedorSso[]>([]);
  useEffect(() => {
    let vigente = true;
    void (async () => {
      try {
        const { data } = await cliente.GET('/auth/sso/proveedores');
        if (!vigente || !Array.isArray(data)) return;
        setProveedores(
          data.flatMap((p) =>
            p.id !== undefined && p.nombre !== undefined && p.inicio !== undefined
              ? [{ id: p.id, nombre: p.nombre, inicio: p.inicio }]
              : [],
          ),
        );
      } catch {
        // Sin botones: ver arriba.
      }
    })();
    return () => {
      vigente = false;
    };
  }, []);
  return proveedores;
}

/** Lo que se le dice a quien vuelve del proveedor sin haber entrado, o `null` si no viene de ahí. */
export function mensajeDeSso(motivo: string | null): string | null {
  if (motivo === null || motivo === '') return null;
  const motivos: Record<string, string> = acceso.sso.motivos;
  return motivos[motivo] ?? acceso.sso.motivos.fallo;
}

/**
 * Lee el `?sso=` con el que el servidor devuelve al navegador y lo quita de la
 * URL, para que recargar o compartir la dirección no repita el mensaje.
 */
export function useVueltaDeSso(): string | null {
  const [motivo] = useState(() => new URLSearchParams(globalThis.location?.search ?? '').get('sso'));
  useEffect(() => {
    if (motivo === null) return;
    const url = new URL(globalThis.location.href);
    url.searchParams.delete('sso');
    globalThis.history.replaceState(globalThis.history.state, '', `${url.pathname}${url.search}${url.hash}`);
  }, [motivo]);
  return motivo;
}
