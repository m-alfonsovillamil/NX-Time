/**
 * Subir mi CV o mi foto: `multipart/form-data` con un campo `fichero`.
 *
 * Aparte de la página por una razón de pruebas: en Vitest (jsdom) el
 * `FormData` de jsdom no se puede meter en un `Request` de Node —falla con
 * «reading '_buffer'»—, así que el viaje entero no se puede simular ahí. Lo
 * que sí se prueba en Vitest es el formulario ([formularioDeSubida]) y que la
 * página llama a [subirAdjunto] con el fichero elegido; que sale como
 * multipart con su separador lo comprueba Playwright en un navegador real.
 */

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';

export function formularioDeSubida(fichero: File): FormData {
  const formulario = new FormData();
  formulario.append('fichero', fichero);
  return formulario;
}

export function subirAdjunto(tipo: 'CV' | 'FOTO', fichero: File) {
  return pedir(
    cliente.POST('/api/v1/perfil/adjuntos', {
      params: { query: { tipo } },
      // El contrato lo describe como texto binario; lo que viaja es el fichero.
      body: { fichero: fichero.name },
      // FormData y no JSON: con FormData, openapi-fetch no pone Content-Type y
      // el navegador añade el suyo, con el separador de las partes.
      bodySerializer: () => formularioDeSubida(fichero),
    }),
  );
}
