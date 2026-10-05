/**
 * La integridad de la traza de auditoría: la última comprobación nocturna y,
 * si hace falta, comprobarla ahora (`/auditoria/integridad`).
 *
 * Una cadena que nadie comprueba no demuestra nada (RD-ley 8/2019: conservar
 * cuatro años y que sea fiable). La de cada noche se enseña con cuántos
 * movimientos son posteriores y aún no se han mirado: «comprobada anoche»
 * sonaría a que está todo comprobado. Comprobar ahora recorre la cadena entera
 * y puede tardar; si algo falla, se dice en qué movimiento y qué le pasa.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir, pedirOpcional, useMutacion } from '../../api/consultas';
import { Aviso, Boton } from '../../componentes/Basicos';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { empresa } from '../../i18n/es/empresa';
import { fechaHoraCorta } from '../../util/fechas';

const I = empresa.integridad;

export function Integridad() {
  const ultima = useQuery({
    queryKey: ['auditoria', 'integridad', 'ultima'],
    queryFn: () => pedirOpcional(cliente.GET('/api/v1/auditoria/integridad/ultima', {})),
  });
  const comprobar = useMutacion(() => pedir(cliente.GET('/api/v1/auditoria/integridad', {})));

  const r = comprobar.data;
  return (
    <div className="nx-pagina nx-pagina--estrecha">
      <header className="nx-cabecera">
        <h1>{I.titulo}</h1>
      </header>
      <p className="nx-sutil">{I.explicacion}</p>

      <section className="nx-tarjeta nx-expediente" aria-labelledby="integridad-ultima">
        <h2 id="integridad-ultima">{I.ultima}</h2>
        <EstadoDeConsulta consulta={ultima} cargando={<Esqueleto lineas={2} />}>
          {(p) =>
            p === null ? (
              <p>{I.nunca}</p>
            ) : (
              <>
                <p>{I.ultimaTexto(fechaHoraCorta(p.verificadoEn), p.movimientos ?? 0)}</p>
                {(p.pendientes ?? 0) > 0 && <p className="nx-sutil">{I.pendientes(p.pendientes ?? 0)}</p>}
              </>
            )
          }
        </EstadoDeConsulta>
      </section>

      <section className="nx-tarjeta nx-expediente" aria-labelledby="integridad-ahora">
        <h2 id="integridad-ahora">{I.comprobarTitulo}</h2>
        <p className="nx-sutil">{I.comprobarTexto}</p>
        <div>
          <Boton ocupado={comprobar.isPending} onClick={() => comprobar.mutate(undefined)}>
            {I.comprobar}
          </Boton>
        </div>
        {comprobar.error !== null && <Aviso>{comprobar.error.message}</Aviso>}
        {r !== undefined && (
          <div role="status" className={r.intacta === true ? 'nx-cadena' : 'nx-cadena nx-cadena--rota'}>
            <strong>{r.intacta === true ? I.intacta : I.rota}</strong>
            <p>{I.detalle(r.movimientos ?? 0, r.comprobados ?? 0, r.soloEnlace ?? 0)}</p>
            {r.intacta !== true && r.primerFallo !== undefined && <p>{I.primerFallo(r.primerFallo, r.motivo ?? '')}</p>}
          </div>
        )}
      </section>
    </div>
  );
}
