/**
 * Las tarjetas del kiosco de toda la plantilla, para imprimirlas y recortarlas
 * (ADR 033). Solo RRHH (`empleado:gestionar`): con una tarjeta se ficha en
 * nombre de su dueño en un kiosco de la empresa.
 *
 * Al imprimir solo salen las tarjetas: el menú, la cabecera y los botones se
 * esconden por CSS (`@media print` en `base.css`).
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import { Boton } from '../../componentes/Basicos';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { kiosco } from '../../i18n/es/kiosco';
import { srcDeTarjeta } from '../cuenta/FicharEnKiosco';

const TT = kiosco.tarjetas;

export function TarjetasKiosco() {
  const tarjetas = useQuery({
    queryKey: ['plantilla', 'tarjetas-kiosco'],
    queryFn: () => pedir(cliente.GET('/api/v1/gestor/kiosco/tarjetas', {})),
  });

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera nx-cabecera--con-acciones nx-no-imprimir">
        <h1>{TT.titulo}</h1>
        <Boton onClick={() => window.print()} disabled={tarjetas.data === undefined}>
          {TT.imprimir}
        </Boton>
      </header>
      <p className="nx-sutil nx-no-imprimir">{TT.explicacion}</p>
      <EstadoDeConsulta consulta={tarjetas} cargando={<Esqueleto lineas={4} />}>
        {(lista) =>
          lista.length === 0 ? (
            <Vacio titulo={TT.ninguna} />
          ) : (
            <div className="nx-rejilla-tarjetas-kiosco">
              {lista.map((t) => (
                <figure key={t.usuarioId} className="nx-tarjeta-kiosco">
                  <img src={srcDeTarjeta(t.svg ?? '')} alt={`${kiosco.perfil.tarjeta}: ${t.nombre ?? ''}`} />
                  <figcaption>
                    <strong>{t.nombre}</strong>
                  </figcaption>
                </figure>
              ))}
            </div>
          )
        }
      </EstadoDeConsulta>
    </div>
  );
}
