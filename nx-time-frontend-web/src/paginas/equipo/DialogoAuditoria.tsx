/**
 * La traza de auditoría de un fichaje (RD-ley 8/2019): todo lo que le ha
 * pasado, de la creación a la última corrección, con quién, desde dónde y por
 * qué. Es la `AuditoriaScreen` de la app.
 *
 * **Arriba del todo, si la traza es de fiar**: la pregunta que trae a alguien
 * aquí es esa, y la respuesta no puede quedar al final de una lista. Lo dice la
 * última comprobación nocturna de la cadena de hashes, y también cuántos
 * movimientos son posteriores y todavía no se han comprobado: sin eso,
 * «comprobada anoche» sonaría a que está comprobado todo.
 *
 * **Solo se pintan las horas que cambian**: «Entrada: 09:00 h → 09:00 h» en
 * cada paso escondería el cambio que importa. Y al fijarse por primera vez
 * (cerrar una jornada abierta) no hay flecha: «Salida: — → 18:00 h» obliga a
 * descifrar qué significa ese guion en un registro legal.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir, pedirOpcional } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { equipo } from '../../i18n/es/equipo';
import { fechaHoraCorta, hora } from '../../util/fechas';

const A = equipo.auditoria;

type Paso = components['schemas']['TimeEntryAuditResponse'];

interface Instantanea {
  horaEntrada?: string;
  horaSalida?: string;
}

/**
 * Lee la foto del fichaje que guarda cada paso. Una vieja o mal formada no
 * tira la traza: el resto de los pasos siguen valiendo.
 */
export function leerInstantanea(valor: string | undefined): Instantanea | null {
  if (!valor) return null;
  try {
    const objeto: unknown = JSON.parse(valor);
    if (typeof objeto !== 'object' || objeto === null) return null;
    const { horaEntrada, horaSalida } = objeto as Record<string, unknown>;
    return {
      ...(typeof horaEntrada === 'string' ? { horaEntrada } : {}),
      ...(typeof horaSalida === 'string' ? { horaSalida } : {}),
    };
  } catch {
    return null;
  }
}

/** Qué horas cambiaron en este paso, en frases. */
export function cambiosDeHora(p: Pick<Paso, 'valorAnterior' | 'valorNuevo'>): string[] {
  const antes = leerInstantanea(p.valorAnterior);
  const despues = leerInstantanea(p.valorNuevo);
  if (despues === null) return [];
  const lineas: string[] = [];
  const linea = (anterior: string | undefined, nuevo: string | undefined, cambio: typeof A.entradaCambio, fijada: typeof A.entradaFijada) => {
    if (nuevo === undefined || anterior === nuevo) return;
    lineas.push(anterior === undefined ? fijada(hora(nuevo)) : cambio(hora(anterior), hora(nuevo)));
  };
  linea(antes?.horaEntrada, despues.horaEntrada, A.entradaCambio, A.entradaFijada);
  linea(antes?.horaSalida, despues.horaSalida, A.salidaCambio, A.salidaFijada);
  return lineas;
}

function EstadoDeLaCadena() {
  const punto = useQuery({
    queryKey: ['auditoria', 'integridad', 'ultima'],
    queryFn: () => pedirOpcional(cliente.GET('/api/v1/auditoria/integridad/ultima', {})),
  });
  if (punto.isPending || punto.isError) return null;
  const p = punto.data;
  return (
    <section className="nx-cadena" aria-label={A.cadenaIntacta}>
      {p === null ? (
        <p className="nx-sutil">{A.cadenaSinComprobar}</p>
      ) : (
        <>
          <strong>{A.cadenaIntacta}</strong>
          <p className="nx-sutil">{A.cadenaComprobada(fechaHoraCorta(p.verificadoEn), p.movimientos ?? 0)}</p>
          {(p.pendientes ?? 0) > 0 && <p className="nx-sutil">{A.cadenaPendientes(p.pendientes ?? 0)}</p>}
        </>
      )}
    </section>
  );
}

function Traza({ fichajeId }: { fichajeId: number }) {
  const traza = useQuery({
    queryKey: ['auditoria', 'fichaje', fichajeId],
    queryFn: () => pedir(cliente.GET('/api/v1/auditoria/fichaje/{id}', { params: { path: { id: fichajeId } } })),
  });

  return (
    <EstadoDeConsulta consulta={traza} cargando={<Esqueleto lineas={4} />}>
      {(pasos) =>
        pasos.length === 0 ? (
          <Vacio titulo={A.vacioTitulo} detalle={A.vacioTexto} />
        ) : (
          <ol className="nx-traza">
            {pasos.map((p) => (
              <li key={p.id} className={`nx-traza__paso${p.accion === 'CORRECCION' || p.accion === 'ANULACION' ? ' nx-traza__paso--fuerte' : ''}`}>
                {/* Una acción que esta versión no conoce sale en crudo: en un
                    registro de cumplimiento, callar una línea es peor que
                    pintarla fea. */}
                <strong>{A.acciones[p.accion ?? ''] ?? p.accion}</strong>
                <span className="nx-sutil">{fechaHoraCorta(p.fechaHora)}</span>
                {cambiosDeHora(p).map((l) => (
                  <span key={l}>{l}</span>
                ))}
                {p.motivo && <span className="nx-texto-largo">{p.motivo}</span>}
                {p.modificadoPor?.nombre && <span className="nx-sutil">{A.por(p.modificadoPor.nombre)}</span>}
                {/* La IP forma parte de la traza que pide la norma: desde dónde, no solo quién. */}
                {p.ip && <span className="nx-sutil">{A.desdeIp(p.ip)}</span>}
              </li>
            ))}
          </ol>
        )
      }
    </EstadoDeConsulta>
  );
}

export function DialogoAuditoria({ fichajeId, alCerrar }: { fichajeId: number | null; alCerrar: () => void }) {
  return (
    <Dialogo abierto={fichajeId !== null} titulo={A.titulo} alCerrar={alCerrar}>
      {fichajeId !== null && (
        <div className="nx-expediente">
          <EstadoDeLaCadena />
          <Traza fichajeId={fichajeId} />
        </div>
      )}
    </Dialogo>
  );
}
