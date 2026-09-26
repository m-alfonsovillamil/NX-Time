/**
 * Añadir a mano una pausa que no se fichó (ADR 015): «se me olvidó darle a pausar para comer».
 *
 * **Lo que pasa al guardar lo decide el servidor, y la respuesta lo dice**:
 * con la jornada abierta, o cerrada que empezó hoy, se aplica en el acto
 * (201); de un día pasado se pide como corrección (202). Aquí se anuncia antes
 * de enviar con la misma regla, solo para poner el texto y el botón correctos;
 * el mensaje final sale de `aplicada`, no de lo que se anunció.
 *
 * Las pausas ya añadidas a esa jornada se ven a la vista: es la única defensa
 * contra añadir dos veces la misma comida. El servidor rechaza el solape entre
 * pausas añadidas, pero no con las fichadas con el botón, porque de esas no se
 * guarda el intervalo. Deshacer solo se ofrece con la jornada abierta: cerrada,
 * quitar una pausa sube lo trabajado y eso ya es una corrección.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { AreaDeTexto, Aviso, Boton, Campo } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { T } from '../../i18n/es';
import { aInstante, diaEnEspana, hora, hoyEnEspana, minutos, sumarDias } from '../../util/fechas';
import { CLAVES, TRAS_CAMBIAR_TIEMPO } from './consultas';

const P = T.fichar.pausa;

export interface JornadaParaPausa {
  id: number;
  horaEntrada: string;
  horaSalida?: string | undefined;
}

function Formulario({ jornada, alTerminar }: { jornada: JornadaParaPausa; alTerminar: () => void }) {
  const abierta = jornada.horaSalida === undefined;
  const dia = diaEnEspana(jornada.horaEntrada);
  const vaDirecta = abierta || dia === hoyEnEspana();

  // 14:00-15:00 de partida: el caso que motivó esto es la comida.
  const [inicio, setInicio] = useState('14:00');
  const [fin, setFin] = useState('15:00');
  const [finOtroDia, setFinOtroDia] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState<string | null>(null);

  const pausas = useQuery({
    queryKey: CLAVES.pausas(jornada.id),
    queryFn: () =>
      pedir(cliente.GET('/api/v1/fichaje/{id}/pausas', { params: { path: { id: jornada.id } } })),
  });

  const anadir = useMutacion(
    (cuerpo: { inicio: string; fin: string; motivo: string }) =>
      pedir(cliente.POST('/api/v1/fichaje/{id}/pausas', { params: { path: { id: jornada.id } }, body: cuerpo })),
    {
      invalida: TRAS_CAMBIAR_TIEMPO,
      exito: (r) => (r.aplicada === true ? P.aplicada : P.pedida),
      alTerminar,
    },
  );

  const deshacer = useMutacion(
    (pausaId: number) =>
      pedir(
        cliente.DELETE('/api/v1/fichaje/{id}/pausas/{pausaId}', {
          params: { path: { id: jornada.id, pausaId } },
        }),
      ),
    { invalida: TRAS_CAMBIAR_TIEMPO },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (motivo.trim() === '') return setError(P.motivoVacio);
    const instanteInicio = aInstante(dia, inicio);
    const instanteFin = aInstante(finOtroDia ? sumarDias(dia, 1) : dia, fin);
    // Se mira aquí porque la pantalla ya tiene las dos horas delante; que
    // quepa en la jornada o no solape lo sabe el servidor.
    if (instanteFin <= instanteInicio) return setError(P.finAnterior);
    setError(null);
    anadir.mutate({ inicio: instanteInicio, fin: instanteFin, motivo: motivo.trim() });
  }

  const mensaje = error ?? anadir.error?.message ?? deshacer.error?.message ?? null;

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p>{vaDirecta ? P.explicacionDirecta : P.explicacionAprobacion}</p>

      <div className="nx-fila-campos">
        <Campo id="pausa-inicio" etiqueta={P.inicio} type="time" required value={inicio} onChange={(e) => setInicio(e.target.value)} />
        <Campo id="pausa-fin" etiqueta={P.fin} type="time" required value={fin} onChange={(e) => setFin(e.target.value)} />
      </div>
      <label className="nx-casilla">
        <input type="checkbox" checked={finOtroDia} onChange={(e) => setFinOtroDia(e.target.checked)} />
        {P.finOtroDia}
      </label>
      <AreaDeTexto
        id="pausa-motivo"
        etiqueta={P.motivo}
        ayuda={P.motivoAyuda}
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
      />

      {mensaje !== null && <Aviso>{mensaje}</Aviso>}

      {(pausas.data?.length ?? 0) > 0 && (
        <section>
          <h3 className="nx-subtitulo">{P.yaAnadidas}</h3>
          <ul className="nx-lista-simple">
            {pausas.data?.map((p) => (
              <li key={p.id}>
                <span>{P.rango(hora(p.inicio), hora(p.fin), minutos(p.minutos ?? 0))}</span>
                {abierta && p.id !== undefined && (
                  <Boton
                    variante="texto"
                    ocupado={deshacer.isPending && deshacer.variables === p.id}
                    onClick={() => p.id !== undefined && deshacer.mutate(p.id)}
                  >
                    {P.deshacer}
                  </Boton>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={anadir.isPending}>
          {vaDirecta ? P.guardarDirecta : P.guardarAprobacion}
        </Boton>
      </div>
    </form>
  );
}

export function DialogoPausa({ jornada, alCerrar }: { jornada: JornadaParaPausa | null; alCerrar: () => void }) {
  return (
    <Dialogo abierto={jornada !== null} titulo={P.titulo} alCerrar={alCerrar} acciones={null}>
      {jornada !== null && <Formulario jornada={jornada} alTerminar={alCerrar} />}
    </Dialogo>
  );
}
