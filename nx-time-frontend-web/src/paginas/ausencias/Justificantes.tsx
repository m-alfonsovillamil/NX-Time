/**
 * El justificante de una ausencia (ADR 062): un parte, un justificante médico.
 *
 * Es un dato de salud, así que el servidor solo devuelve los que quien mira
 * puede ver (los suyos y, con `ausencia:justificante:leer`, los de su empresa)
 * y solo deja subirlos a la persona, si la empresa los tiene activados. Aquí no
 * se decide nada de eso: se pinta lo que llega.
 */

import { useQuery } from '@tanstack/react-query';
import { useRef, useState } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton } from '../../componentes/Basicos';
import { ausencias } from '../../i18n/es/ausencias';
import { descargar } from '../../util/descargar';
import { formularioDeSubida } from '../cuenta/adjuntos';

const J = ausencias.justificante;
const CINCO_MB = 5 * 1024 * 1024;

type Justificante = components['schemas']['JustificanteResponse'];

export const CLAVE_JUSTIFICANTES = ['justificantes'] as const;

/** Si la empresa los tiene activados y los de estas ausencias, por id. */
export function useJustificantes(ids: readonly (number | undefined)[]) {
  const peticiones = ids.filter((id): id is number => id !== undefined).slice(0, 200);
  const consulta = useQuery({
    queryKey: [...CLAVE_JUSTIFICANTES, peticiones],
    queryFn: () => pedir(cliente.GET('/api/v1/ausencias/justificantes', { params: { query: { peticiones } } })),
    enabled: peticiones.length > 0,
  });
  const porId = new Map<number, Justificante>();
  for (const j of consulta.data?.justificantes ?? []) {
    if (j.peticionId !== undefined) porId.set(j.peticionId, j);
  }
  return { activo: consulta.data?.activo === true, porId };
}

export function problemaDelJustificante(fichero: File): string | null {
  if (fichero.size === 0) return J.vacio;
  if (fichero.size > CINCO_MB) return J.grande;
  return null;
}

/**
 * El justificante de una ausencia: descargarlo si lo hay y, en las propias con
 * la empresa activada, subirlo, cambiarlo o quitarlo.
 *
 * `admite`: si la ausencia sigue en pie. En una cancelada o rechazada ya no se
 * adjunta ni se cambia (el servidor da 409), pero el que tuviera se puede
 * quitar: es un documento de la persona, a menudo médico.
 */
export function JustificanteDeAusencia({
  peticionId,
  justificante,
  propia,
  activo,
  admite = true,
}: {
  peticionId: number;
  justificante: Justificante | undefined;
  propia: boolean;
  activo: boolean;
  admite?: boolean;
}) {
  const entrada = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const subir = useMutacion(
    (fichero: File) =>
      pedir(
        cliente.POST('/api/v1/ausencias/{id}/justificante', {
          params: { path: { id: peticionId } },
          body: { fichero: fichero.name },
          bodySerializer: () => formularioDeSubida(fichero),
        }),
      ),
    { invalida: [CLAVE_JUSTIFICANTES], exito: J.subido },
  );
  const quitar = useMutacion(
    () => pedir(cliente.DELETE('/api/v1/ausencias/{id}/justificante', { params: { path: { id: peticionId } } })),
    { invalida: [CLAVE_JUSTIFICANTES], exito: J.quitado },
  );

  const puedeCambiar = propia && activo && admite;
  const puedeQuitar = propia && activo && justificante !== undefined;
  if (justificante === undefined && !puedeCambiar) return null;

  async function abrir() {
    setError(null);
    try {
      await descargar(
        cliente.GET('/api/v1/ausencias/{id}/justificante', { params: { path: { id: peticionId } }, parseAs: 'blob' }),
        justificante?.nombre ?? 'justificante',
      );
    } catch (fallo) {
      setError(fallo instanceof Error ? fallo.message : J.errorDescarga);
    }
  }

  function elegir(fichero: File | undefined) {
    if (fichero === undefined) return;
    const problema = problemaDelJustificante(fichero);
    setError(problema);
    if (problema === null) subir.mutate(fichero);
  }

  const mensaje = error ?? subir.error?.message ?? quitar.error?.message ?? null;
  return (
    <span className="nx-acciones-fila">
      {justificante !== undefined && (
        <Boton variante="texto" onClick={() => void abrir()}>
          {J.ver(justificante.nombre ?? '')}
        </Boton>
      )}
      {puedeCambiar && (
        <>
          <input
            ref={entrada}
            id={`justificante-${peticionId}`}
            type="file"
            accept="application/pdf,image/jpeg,image/png"
            hidden
            onChange={(e) => {
              elegir(e.target.files?.[0]);
              e.target.value = '';
            }}
          />
          <Boton variante="texto" ocupado={subir.isPending} onClick={() => entrada.current?.click()}>
            {justificante === undefined ? J.adjuntar : J.cambiar}
          </Boton>
        </>
      )}
      {puedeQuitar && (
        <Boton variante="texto" ocupado={quitar.isPending} onClick={() => quitar.mutate()}>
          {J.quitar}
        </Boton>
      )}
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
    </span>
  );
}
