/**
 * Repartir las horas de una jornada entre proyectos (ADR 017).
 *
 * Mover horas entre proyectos no es corregir cuánto se trabajó, así que no
 * pasa por «Pedir corrección»: tiene su propio diálogo. **El servidor decide
 * si se aplica o se pide**, y el código lo dice: 200 aplicado, 202 pendiente
 * de un gestor. Aquí solo se anticipa, con `repartoLibre` y el total, para
 * que el botón diga lo que va a pasar antes de pulsarlo: descubrirlo después
 * es lo que hace dudar de si se ha guardado.
 *
 * Sumar menos de lo trabajado no se puede enviar (el servidor daría 400);
 * sumar más, o repartir una jornada de otra semana, necesita motivo.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, pedirConEstado, useMutacion } from '../../api/consultas';
import { AreaDeTexto, Aviso, Boton } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta } from '../../componentes/Estados';
import type { components } from '../../api/schema';
import { T } from '../../i18n/es';
import { historial } from '../../i18n/es/historial';
import { minutos } from '../../util/fechas';
import { CLAVES, TRAS_CAMBIAR_TIEMPO } from '../jornada/consultas';

const R = historial.reparto;

type Imputaciones = components['schemas']['AllocationsResponse'];

function Formulario({
  fichajeId,
  imputaciones,
  alTerminar,
}: {
  fichajeId: number;
  imputaciones: Imputaciones;
  alTerminar: () => void;
}) {
  const neto = imputaciones.netoMinutos ?? 0;
  const disponibles = imputaciones.disponibles ?? [];
  const [reparto, setReparto] = useState<Record<number, number>>(() =>
    Object.fromEntries((imputaciones.lineas ?? []).map((l) => [l.proyectoId ?? 0, l.minutos ?? 0])),
  );
  const [motivo, setMotivo] = useState('');

  const total = Object.values(reparto).reduce((s, m) => s + m, 0);
  const diferencia = total - neto;
  const necesitaAprobacion = diferencia > 0 || imputaciones.repartoLibre === false;
  const conSolicitud = imputaciones.solicitudPendienteId !== undefined;
  const puedeEnviar =
    !conSolicitud && total > 0 && diferencia >= 0 && (!necesitaAprobacion || motivo.trim() !== '');

  const guardar = useMutacion(
    (cuerpo: { lineas: { proyectoId: number; minutos: number }[]; motivo?: string }) =>
      pedirConEstado(
        cliente.PUT('/api/v1/fichaje/{id}/imputaciones', { params: { path: { id: fichajeId } }, body: cuerpo }),
      ),
    {
      invalida: [...TRAS_CAMBIAR_TIEMPO, ['proyectos'], ['correcciones']],
      exito: (r) => (r.status === 200 ? R.aplicado : R.pedido),
      alTerminar,
    },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!puedeEnviar) return;
    guardar.mutate({
      lineas: Object.entries(reparto)
        .filter(([, m]) => m > 0)
        .map(([proyectoId, m]) => ({ proyectoId: Number(proyectoId), minutos: m })),
      ...(motivo.trim() !== '' ? { motivo: motivo.trim() } : {}),
    });
  }

  if (disponibles.length === 0) {
    return (
      <div className="nx-formulario-dialogo">
        <p>{R.sinProyectos}</p>
        <div className="nx-dialogo__acciones">
          <Boton variante="texto" onClick={alTerminar}>
            {T.app.cerrar}
          </Boton>
        </div>
      </div>
    );
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p>{R.neto(minutos(neto))}</p>
      {conSolicitud && <Aviso>{R.conSolicitud}</Aviso>}

      <ul className="nx-lista-simple nx-reparto">
        {disponibles.map((p) => {
          const id = p.id ?? 0;
          const nombre = `${p.codigo ?? ''} · ${p.nombre ?? ''}`;
          return (
            <li key={id}>
              <span className="nx-reparto__proyecto">{nombre}</span>
              <input
                id={`reparto-${id}`}
                className="nx-reparto__minutos"
                type="number"
                min={0}
                step={1}
                inputMode="numeric"
                aria-label={R.minutos(nombre)}
                value={reparto[id] ?? 0}
                disabled={conSolicitud}
                onChange={(e) => setReparto((r) => ({ ...r, [id]: Math.max(0, Math.floor(Number(e.target.value) || 0)) }))}
              />
              <Boton
                variante="texto"
                aria-label={R.todoEn(nombre)}
                disabled={conSolicitud}
                onClick={() => setReparto({ [id]: neto })}
              >
                {R.todoAqui}
              </Boton>
            </li>
          );
        })}
      </ul>

      <p className="nx-grafico__total">{R.total(minutos(total), minutos(neto))}</p>
      {diferencia < 0 && <p className="nx-sutil">{R.faltan(minutos(-diferencia))}</p>}
      {diferencia > 0 && <p className="nx-sutil">{R.deMas(minutos(diferencia))}</p>}
      {diferencia <= 0 && (
        <p className="nx-sutil">{imputaciones.repartoLibre === false ? R.loApruebaUnGestor : R.seAplicaYa}</p>
      )}

      {necesitaAprobacion && !conSolicitud && (
        <AreaDeTexto id="reparto-motivo" etiqueta={R.motivo} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
      )}

      {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}

      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={guardar.isPending} disabled={!puedeEnviar}>
          {necesitaAprobacion ? R.pedir : R.guardar}
        </Boton>
      </div>
    </form>
  );
}

function Contenido({ fichajeId, alTerminar }: { fichajeId: number; alTerminar: () => void }) {
  const imputaciones = useQuery({
    queryKey: CLAVES.imputaciones(fichajeId),
    queryFn: () =>
      pedir(cliente.GET('/api/v1/fichaje/{id}/imputaciones', { params: { path: { id: fichajeId } } })),
    // Lo que se reparte parte de lo que hay ahora, no de hace medio minuto.
    staleTime: 0,
  });
  return (
    <EstadoDeConsulta consulta={imputaciones}>
      {(datos) => <Formulario fichajeId={fichajeId} imputaciones={datos} alTerminar={alTerminar} />}
    </EstadoDeConsulta>
  );
}

export function DialogoReparto({ fichajeId, alCerrar }: { fichajeId: number | null; alCerrar: () => void }) {
  return (
    <Dialogo abierto={fichajeId !== null} titulo={R.titulo} alCerrar={alCerrar} acciones={null}>
      {fichajeId !== null && <Contenido fichajeId={fichajeId} alTerminar={alCerrar} />}
    </Dialogo>
  );
}
