/**
 * La integridad de la traza de auditoría: la última comprobación nocturna y,
 * si hace falta, comprobarla ahora (`/auditoria/integridad`).
 *
 * Una cadena que nadie comprueba no demuestra nada (RD-ley 8/2019: conservar
 * cuatro años y que sea fiable). La de cada noche se enseña con cuántos
 * movimientos son posteriores y aún no se han mirado: «comprobada anoche»
 * sonaría a que está todo comprobado. Comprobar ahora recorre la cadena entera
 * y puede tardar; si algo falla, se dice en qué movimiento y qué le pasa.
 *
 * A lo ancho (5/10/2026): lo que se mira y se hace, a la izquierda; el «cómo
 * funciona», al lado y en puntos. Antes era un párrafo largo encima de dos
 * tarjetas en una columna de 560 px.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir, pedirOpcional, useMutacion } from '../../api/consultas';
import { Aviso, Boton, Destacado, Puntos, Tarjeta } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
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
    <div className="nx-pagina">
      <CabeceraDePagina titulo={I.titulo} descripcion={I.explicacion} />

      <div className="nx-composicion nx-composicion--principal-lateral">
        <div className="nx-columna">
          <Tarjeta titulo={I.ultima} icono="verificado" descripcion={I.cadaNoche}>
            <EstadoDeConsulta consulta={ultima} cargando={<Esqueleto forma="recuadros" lineas={3} />}>
              {(p) =>
                p === null ? (
                  <Vacio icono="verificado" titulo={I.nunca} detalle={I.nuncaDetalle} />
                ) : (
                  <Cifras>
                    <Cifra icono="hecho" etiqueta={I.cifras.movimientos} valor={p.movimientos ?? 0} />
                    <Cifra
                      icono="horas-extra"
                      etiqueta={I.cifras.pendientes}
                      valor={p.pendientes ?? 0}
                      {...((p.pendientes ?? 0) > 0 ? { tono: 'destacada' as const, detalle: I.cifras.pendientesDetalle } : {})}
                    />
                    <Cifra icono="calendario" etiqueta={I.cifras.cuando} valor={fechaHoraCorta(p.verificadoEn)} />
                  </Cifras>
                )
              }
            </EstadoDeConsulta>
          </Tarjeta>

          <Tarjeta titulo={I.comprobarTitulo} icono="buscar" descripcion={I.comprobarTexto} className="nx-expediente">
            <div>
              <Boton ocupado={comprobar.isPending} onClick={() => comprobar.mutate(undefined)}>
                {I.comprobar}
              </Boton>
            </div>
            {comprobar.error !== null && <Aviso>{comprobar.error.message}</Aviso>}
            {r !== undefined && (
              <Destacado
                role="status"
                tono={r.intacta === true ? 'bien' : 'mal'}
                icono={r.intacta === true ? 'verificado' : 'error'}
                titulo={r.intacta === true ? I.intacta : I.rota}
              >
                <p>{I.detalle(r.movimientos ?? 0, r.comprobados ?? 0, r.soloEnlace ?? 0)}</p>
                {r.intacta !== true && r.primerFallo !== undefined && <p>{I.primerFallo(r.primerFallo, r.motivo ?? '')}</p>}
              </Destacado>
            )}
          </Tarjeta>
        </div>

        <Tarjeta titulo={I.comoFunciona}>
          <Puntos puntos={I.puntos} />
        </Tarjeta>
      </div>
    </div>
  );
}
