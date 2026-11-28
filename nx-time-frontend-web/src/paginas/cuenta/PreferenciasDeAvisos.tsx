/**
 * Cómo me llegan los avisos (ADR 059; plan N, paso N9): por categoría, si por
 * correo y si al móvil o a este navegador (push). La campana de la aplicación
 * los recibe siempre, y las obligatorias no se pueden apagar: se enseñan, pero
 * sin casillas, para que nadie crea que tiene apagado lo que no se apaga.
 *
 * Cada casilla se guarda al cambiarla, sin botón: es lo que se espera de un
 * interruptor, y con la respuesta llega todo ya guardado.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Tarjeta } from '../../componentes/Basicos';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { cuenta } from '../../i18n/es/cuenta';

const A = cuenta.preferenciasDeAvisos;

type Preferencia = components['schemas']['PreferenciaDeAviso'];
type Categoria = NonNullable<Preferencia['categoria']>;

const CLAVE = ['preferencias-de-avisos'] as const;

export function PreferenciasDeAvisos() {
  const consulta = useQuery({
    queryKey: CLAVE,
    queryFn: () => pedir(cliente.GET('/api/v1/perfil/avisos', {})),
  });
  const guardar = useMutacion(
    (cambio: { categoria: Categoria; correo: boolean; push: boolean }) =>
      pedir(cliente.PUT('/api/v1/perfil/avisos', { body: { preferencias: [cambio] } })),
    { invalida: [CLAVE], exito: A.guardado },
  );

  return (
    <Tarjeta id="ajustes-avisos" icono="bandeja" titulo={A.titulo} descripcion={A.descripcion}>
      <EstadoDeConsulta consulta={consulta} cargando={<Esqueleto forma="tabla" lineas={6} />}>
        {(preferencias) => (
          <>
            <table className="nx-tabla">
              <caption className="nx-solo-lector">{A.titulo}</caption>
              <thead>
                <tr>
                  <th scope="col">{A.categoria}</th>
                  <th scope="col">{A.correo}</th>
                  <th scope="col">{A.push}</th>
                </tr>
              </thead>
              <tbody>
                {preferencias.map((p) => (
                  <tr key={p.categoria}>
                    <th scope="row">
                      <div>{p.etiqueta}</div>
                      <div className="nx-sutil">{p.descripcion}</div>
                    </th>
                    {p.configurable ? (
                      <>
                        <td data-etiqueta={A.correo}>
                          <input
                            type="checkbox"
                            aria-label={A.correoDe(p.etiqueta ?? '')}
                            checked={p.correo ?? true}
                            disabled={guardar.isPending}
                            onChange={(e) =>
                              p.categoria && guardar.mutate({ categoria: p.categoria, correo: e.target.checked, push: p.push ?? true })
                            }
                          />
                        </td>
                        <td data-etiqueta={A.push}>
                          <input
                            type="checkbox"
                            aria-label={A.pushDe(p.etiqueta ?? '')}
                            checked={p.push ?? true}
                            disabled={guardar.isPending}
                            onChange={(e) =>
                              p.categoria && guardar.mutate({ categoria: p.categoria, correo: p.correo ?? true, push: e.target.checked })
                            }
                          />
                        </td>
                      </>
                    ) : (
                      <td colSpan={2} className="nx-sutil">
                        {A.siempre}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
            {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
          </>
        )}
      </EstadoDeConsulta>
    </Tarjeta>
  );
}
