/**
 * Firmar mi registro de cada mes (ADR 025): los últimos meses terminados, si
 * se pueden firmar y por qué no, y la firma vigente o la que quedó sin efecto.
 *
 * **La firma no bloquea la corrección; la corrección invalida la firma.** Si
 * después de firmar se corrige un fichaje de ese mes, la firma pasa a
 * INVALIDADA con su motivo y el mes vuelve a pedir firma. La pantalla lo dice
 * así, y la huella se puede comprobar: el servidor recalcula hoy el resumen
 * del mes y lo compara con el firmado.
 *
 * Firmar puede fallar por tres motivos, y cada uno llega con su mensaje: el
 * mes no ha terminado (400), ya está firmado (409) o tiene jornadas abiertas o
 * cerradas por el sistema (422). `bloqueo` los anticipa, y si aun así el
 * servidor dice que no, se enseña lo que diga.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Insignia } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { cuadrante } from '../../i18n/es/cuadrante';
import { diaEnEmpresa, duracion, fechaCorta, mesYAnio } from '../../util/fechas';

const F = cuadrante.firmas;

type Mes = components['schemas']['SignableMonthResponse'];

const CLAVE = ['firmas', 'mias'] as const;

function nombreDelMes(m: Mes): string {
  return mesYAnio(m.anio ?? 0, m.mes ?? 1);
}

export function Comprobacion({ firmaId }: { firmaId: number }) {
  const [pedida, setPedida] = useState(false);
  const verificacion = useQuery({
    queryKey: ['firmas', 'verificacion', firmaId],
    queryFn: () => pedir(cliente.GET('/api/v1/firmas/{id}/verificacion', { params: { path: { id: firmaId } } })),
    enabled: pedida,
    // Se recalcula en el servidor en cada consulta: siempre la de ahora.
    staleTime: 0,
  });

  if (!pedida) {
    return (
      <Boton variante="texto" onClick={() => setPedida(true)}>
        {F.comprobar}
      </Boton>
    );
  }
  if (verificacion.isPending) return <Esqueleto lineas={1} />;
  if (verificacion.isError) return <Aviso>{verificacion.error.message}</Aviso>;
  return (
    <p role="status" className={verificacion.data.coincide === true ? 'nx-sutil' : 'nx-texto-error'}>
      {verificacion.data.coincide === true ? F.coincide : F.noCoincide}
    </p>
  );
}

function FilaDelMes({ m, alFirmar }: { m: Mes; alFirmar: (m: Mes) => void }) {
  const firma = m.firma;
  const vigente = firma?.estado === 'VIGENTE';
  const invalidada = firma?.estado === 'INVALIDADA';

  return (
    <li className="nx-tarjeta nx-incidencia nx-incidencia--tarjeta">
      <div className="nx-incidencia__cabecera">
        <strong className="nx-incidencia__titulo">{nombreDelMes(m)}</strong>
        <Insignia tono={vigente ? 'exito' : invalidada ? 'error' : 'aviso'}>
          {vigente ? F.firmado : invalidada ? F.invalidada : F.pendiente}
        </Insignia>
      </div>
      <span className="nx-sutil">{F.jornadas(m.jornadas ?? 0, duracion(m.segundosNetos ?? 0))}</span>
      {vigente && firma?.firmadaEn && (
        <span className="nx-sutil">{F.firmadoEl(fechaCorta(diaEnEmpresa(firma.firmadaEn)), (firma.hash ?? '').slice(0, 12))}</span>
      )}
      {vigente && firma?.visadaPor && <span className="nx-sutil">{F.visado(firma.visadaPor)}</span>}
      {invalidada && firma?.motivoInvalidacion && <span>{F.invalidadaPorque(firma.motivoInvalidacion)}</span>}
      {!vigente && m.bloqueo && <span className="nx-sutil">{m.bloqueo}</span>}
      <div className="nx-acciones-fila">
        {!vigente && m.puedeFirmar === true && <Boton onClick={() => alFirmar(m)}>{F.firmar}</Boton>}
        {firma?.id !== undefined && <Comprobacion firmaId={firma.id} />}
      </div>
    </li>
  );
}

export function Firmas() {
  const [firmando, setFirmando] = useState<Mes | null>(null);
  const meses = useQuery({ queryKey: CLAVE, queryFn: () => pedir(cliente.GET('/api/v1/firmas/mias', {})) });

  const firmar = useMutacion(
    (m: Mes) => pedir(cliente.POST('/api/v1/firmas', { body: { anio: m.anio ?? 0, mes: m.mes ?? 0 } })),
    { invalida: [['firmas']], exito: F.hecha, alTerminar: () => setFirmando(null) },
  );

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={F.titulo} descripcion={F.explicacion} />
      {/* Una tarjeta por mes, las que quepan por fila: el estado de cada uno se ve de un vistazo. */}
      <EstadoDeConsulta consulta={meses} cargando={<Esqueleto forma="recuadros" lineas={3} />}>
        {(lista) =>
          lista.length === 0 ? (
            <section className="nx-tarjeta">
              <Vacio icono="firma" titulo={F.vacio} />
            </section>
          ) : (
            <ul className="nx-rejilla-tarjetas">
              {lista.map((m) => (
                <FilaDelMes key={`${m.anio}-${m.mes}`} m={m} alFirmar={setFirmando} />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <Dialogo
        abierto={firmando !== null}
        titulo={firmando !== null ? F.confirmarTitulo(nombreDelMes(firmando)) : ''}
        alCerrar={() => {
          firmar.reset();
          setFirmando(null);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setFirmando(null)}>
              {T.app.cancelar}
            </Boton>
            <Boton ocupado={firmar.isPending} onClick={() => firmando !== null && firmar.mutate(firmando)}>
              {F.firmar}
            </Boton>
          </>
        }
      >
        {firmando !== null && (
          <>
            <p>
              {F.confirmarTexto(
                nombreDelMes(firmando).toLowerCase(),
                firmando.jornadas ?? 0,
                duracion(firmando.segundosNetos ?? 0),
              )}
            </p>
            <p className="nx-sutil">{F.confirmarAviso}</p>
            {firmar.error !== null && <Aviso>{firmar.error.message}</Aviso>}
          </>
        )}
      </Dialogo>
    </div>
  );
}
