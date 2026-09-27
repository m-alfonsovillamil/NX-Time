/**
 * Pedir que se corrija una jornada propia ya cerrada (ADR 010).
 *
 * **Se pide, no se corrige**: el fichaje no cambia hasta que lo aprueba quien
 * tiene `correccion:aprobar`. Solo se aplica en el acto si quien pide puede
 * aprobarse a sí mismo, y lo que ha pasado lo dice el `estado` de la
 * respuesta, no lo que la pantalla supone.
 *
 * Las horas se escriben en hora de España sobre el día de la entrada, y la
 * salida puede ser del día siguiente: es lo que hace corregible un turno de
 * noche. Viene marcada si la jornada ya acababa otro día.
 */

import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { AreaDeTexto, Aviso, Boton, Campo } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { T } from '../../i18n/es';
import { historial } from '../../i18n/es/historial';
import { aInstante, diaEnEspana, diasEntre, fechaCorta, horaEnEspana, sumarDias } from '../../util/fechas';
import { TRAS_CAMBIAR_TIEMPO } from '../jornada/consultas';

const C = historial.correccion;

export interface JornadaCerrada {
  id: number;
  horaEntrada: string;
  horaSalida: string;
}

function Formulario({ jornada, alTerminar }: { jornada: JornadaCerrada; alTerminar: () => void }) {
  const dia = diaEnEspana(jornada.horaEntrada);
  const [entrada, setEntrada] = useState(horaEnEspana(jornada.horaEntrada));
  const [salida, setSalida] = useState(horaEnEspana(jornada.horaSalida));
  const [salidaOtroDia, setSalidaOtroDia] = useState(diasEntre(dia, diaEnEspana(jornada.horaSalida)) > 0);
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState<string | null>(null);

  const pedirCorreccion = useMutacion(
    (cuerpo: { horaEntrada: string; horaSalida: string; motivo: string }) =>
      pedir(cliente.POST('/api/v1/fichaje/{id}/correcciones', { params: { path: { id: jornada.id } }, body: cuerpo })),
    {
      invalida: [...TRAS_CAMBIAR_TIEMPO, ['correcciones']],
      exito: (r) => (r.estado === 'APROBADA' ? C.aplicada : C.pedida),
      alTerminar,
    },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (motivo.trim() === '') return setError(C.motivoVacio);
    const horaEntrada = aInstante(dia, entrada);
    const horaSalida = aInstante(salidaOtroDia ? sumarDias(dia, 1) : dia, salida);
    // Sigue haciendo falta con la casilla: marcar «otro día» con 23:00 de
    // salida para una entrada de las 22:52 es correcto; desmarcarla, no.
    if (horaSalida <= horaEntrada) return setError(C.salidaAnterior);
    setError(null);
    pedirCorreccion.mutate({ horaEntrada, horaSalida, motivo: motivo.trim() });
  }

  const mensaje = error ?? pedirCorreccion.error?.message ?? null;

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p className="nx-sutil">{fechaCorta(dia)}</p>
      <p>{C.explicacion}</p>
      <div className="nx-fila-campos">
        <Campo id="correccion-entrada" etiqueta={C.entrada} type="time" value={entrada} onChange={(e) => setEntrada(e.target.value)} />
        <Campo id="correccion-salida" etiqueta={C.salida} type="time" value={salida} onChange={(e) => setSalida(e.target.value)} />
      </div>
      <label className="nx-casilla">
        <input type="checkbox" checked={salidaOtroDia} onChange={(e) => setSalidaOtroDia(e.target.checked)} />
        {C.salidaOtroDia}
      </label>
      <AreaDeTexto
        id="correccion-motivo"
        etiqueta={C.motivo}
        ayuda={C.motivoAyuda}
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
      />
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={pedirCorreccion.isPending}>
          {C.enviar}
        </Boton>
      </div>
    </form>
  );
}

export function DialogoCorreccion({ jornada, alCerrar }: { jornada: JornadaCerrada | null; alCerrar: () => void }) {
  return (
    <Dialogo abierto={jornada !== null} titulo={C.titulo} alCerrar={alCerrar} acciones={null}>
      {jornada !== null && <Formulario jornada={jornada} alTerminar={alCerrar} />}
    </Dialogo>
  );
}
