/**
 * Las tarjetas del kiosco de toda la plantilla, para imprimirlas y recortarlas
 * (ADR 033). Solo quien gestiona los kioscos (`empresa:configurar`, el ADMIN):
 * con una tarjeta se ficha en nombre de su dueño, y el servidor anota quién las
 * saca (ADR 034). Es un POST porque crea las que faltan.
 *
 * Al imprimir solo salen las tarjetas: el menú, la cabecera y los botones se
 * esconden por CSS (`@media print` en `base.css`).
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import { Boton } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { kiosco } from '../../i18n/es/kiosco';
import { srcDeTarjeta } from '../cuenta/FicharEnKiosco';

const TT = kiosco.tarjetas;

export function TarjetasKiosco() {
  const tarjetas = useQuery({
    queryKey: ['plantilla', 'tarjetas-kiosco'],
    queryFn: () => pedir(cliente.POST('/api/v1/empresa/kioscos/tarjetas', {})),
    // Una vez por visita: cada petición queda anotada en el servidor.
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  return (
    <div className="nx-pagina">
      <CabeceraDePagina
        className="nx-no-imprimir"
        titulo={TT.titulo}
        descripcion={TT.explicacion}
        acciones={
          <>
            <Boton onClick={() => window.print()} disabled={tarjetas.data === undefined}>
              {TT.imprimir}
            </Boton>
          </>
        }
      />
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
