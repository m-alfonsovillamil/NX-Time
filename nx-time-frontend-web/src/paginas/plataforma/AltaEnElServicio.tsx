/**
 * El alta de una empresa en el servicio, en su ficha del panel de plataforma
 * (ADR 046): en qué estado está, qué condiciones tiene aceptadas, lo que el
 * operador puede hacer con ella y el historial de lo que se ha hecho.
 *
 * Rechazar y suspender piden un motivo: le llega a la empresa por correo.
 */

import { useState } from 'react';
import { useNavigate } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Insignia, Tarjeta, type Tono } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { plataforma } from '../../i18n/es/plataforma';
import { fechaHoraCorta } from '../../util/fechas';
import { CLAVE_PLATAFORMA, diaDe } from './Plataforma';

const S = plataforma.detalle.servicio;
const H = plataforma.detalle.historial;

type Detalle = components['schemas']['PlatformCompanyDetailResponse'];
type Accion = NonNullable<Detalle['historial']>[number];
type Verbo = 'aprobar' | 'rechazar' | 'suspender' | 'reactivar';

const TONOS: Record<string, Tono> = { PENDIENTE: 'aviso', ACTIVA: 'exito', SUSPENDIDA: 'error' };

const COLUMNAS: readonly Columna<Accion>[] = [
  { clave: 'cuando', cabecera: H.cuando, celda: (a) => fechaHoraCorta(a.en) },
  { clave: 'quien', cabecera: H.quien, celda: (a) => a.operador ?? '' },
  {
    clave: 'que',
    cabecera: H.que,
    celda: (a) => (
      <>
        {H.acciones[a.accion ?? ''] ?? a.accion}
        {a.motivo !== undefined && a.motivo !== null && <span className="nx-sutil"> · {a.motivo}</span>}
      </>
    ),
  },
];

export function AltaEnElServicio({ empresa: e }: { empresa: Detalle }) {
  const navegar = useNavigate();
  const id = e.id ?? 0;
  const [abierto, setAbierto] = useState<Verbo | null>(null);

  const actuar = useMutacion(
    ({ verbo, motivo }: { verbo: Verbo; motivo?: string }) => {
      const ruta = { params: { path: { id } } };
      switch (verbo) {
        case 'aprobar':
          return pedir(cliente.POST('/api/v1/plataforma/empresas/{id}/aprobar', ruta));
        case 'reactivar':
          return pedir(cliente.POST('/api/v1/plataforma/empresas/{id}/reactivar', ruta));
        case 'rechazar':
          return pedir(cliente.POST('/api/v1/plataforma/empresas/{id}/rechazar', { ...ruta, body: { motivo: motivo ?? '' } }));
        case 'suspender':
          return pedir(cliente.POST('/api/v1/plataforma/empresas/{id}/suspender', { ...ruta, body: { motivo: motivo ?? '' } }));
      }
    },
    {
      invalida: [CLAVE_PLATAFORMA],
      alTerminar: (_, { verbo }) => {
        setAbierto(null);
        // Rechazada se borra: ya no hay ficha que enseñar.
        if (verbo === 'rechazar') navegar('/plataforma', { replace: true });
      },
    },
  );
  const error = actuar.error?.message ?? null;
  const estado = e.estado ?? 'ACTIVA';

  return (
    <>
      <Tarjeta titulo={S.titulo} icono="verificado">
        <dl className="nx-datos">
          <dt>{S.estado}</dt>
          <dd>
            <Insignia tono={TONOS[estado] ?? 'neutro'}>{S.estados[estado] ?? estado}</Insignia>
          </dd>
          <dt>{S.aprobadaEn}</dt>
          <dd>{e.aprobadaEn !== undefined && e.aprobadaEn !== null ? diaDe(e.aprobadaEn, S.noAprobada) : S.noAprobada}</dd>
          <dt>{S.condiciones}</dt>
          <dd>
            {e.condicionesVersion !== undefined && e.condicionesVersion !== null
              ? S.condicionesDe(e.condicionesVersion, diaDe(e.condicionesAceptadasEn, ''))
              : S.sinCondiciones}
          </dd>
        </dl>
        {estado === 'PENDIENTE' && <p className="nx-sutil">{S.pendienteAyuda}</p>}
        <div className="nx-acciones">
          {estado === 'PENDIENTE' && (
            <>
              <Boton onClick={() => setAbierto('aprobar')}>{S.aprobar}</Boton>
              <Boton variante="peligro" onClick={() => setAbierto('rechazar')}>
                {S.rechazar}
              </Boton>
            </>
          )}
          {estado === 'ACTIVA' && (
            <Boton variante="peligro" onClick={() => setAbierto('suspender')}>
              {S.suspender}
            </Boton>
          )}
          {estado === 'SUSPENDIDA' && <Boton onClick={() => setAbierto('reactivar')}>{S.reactivar}</Boton>}
        </div>
        {abierto === null && error !== null && <Aviso>{error}</Aviso>}
      </Tarjeta>

      <Tarjeta titulo={H.titulo} icono="historial" descripcion={H.descripcion}>
        {(e.historial ?? []).length === 0 ? (
          <Vacio icono="historial" titulo={H.vacio} />
        ) : (
          <Tabla titulo={H.tabla} columnas={COLUMNAS} filas={e.historial ?? []} claveDeFila={(a) => `${a.en}-${a.accion}`} />
        )}
      </Tarjeta>

      {(['aprobar', 'reactivar'] as const).map((verbo) => (
        <Dialogo
          key={verbo}
          abierto={abierto === verbo}
          titulo={verbo === 'aprobar' ? S.aprobarTitulo : S.reactivarTitulo}
          alCerrar={() => setAbierto(null)}
          acciones={
            <>
              <Boton variante="texto" onClick={() => setAbierto(null)}>
                {S.cancelar}
              </Boton>
              <Boton ocupado={actuar.isPending} onClick={() => actuar.mutate({ verbo })}>
                {S.confirmar}
              </Boton>
            </>
          }
        >
          <p>{verbo === 'aprobar' ? S.aprobarTexto : S.reactivarTexto}</p>
          {error !== null && <Aviso>{error}</Aviso>}
        </Dialogo>
      ))}

      {(['rechazar', 'suspender'] as const).map((verbo) => (
        <DialogoDeTexto
          key={verbo}
          abierto={abierto === verbo}
          titulo={verbo === 'rechazar' ? S.rechazarTitulo : S.suspenderTitulo}
          ayuda={verbo === 'rechazar' ? S.rechazarAyuda : S.suspenderAyuda}
          etiqueta={S.motivo}
          vacio={S.faltaMotivo}
          boton={verbo === 'rechazar' ? S.rechazar : S.suspender}
          ocupado={actuar.isPending}
          error={error}
          peligro
          alEnviar={(motivo) => actuar.mutate({ verbo, motivo })}
          alCerrar={() => setAbierto(null)}
        />
      ))}
    </>
  );
}
