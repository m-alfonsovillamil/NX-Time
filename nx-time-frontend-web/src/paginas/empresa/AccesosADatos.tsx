/**
 * Quién ha consultado datos personales de otra persona (plan J, fase J5; ADR
 * 047). Solo ADMIN (`empresa:configurar`): la empresa es la responsable del
 * tratamiento y es quien tiene que poder enseñarlo.
 *
 * Solo lectura: la tabla es de solo inserción en la base y aquí no hay nada
 * que cambiar.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { empresa } from '../../i18n/es/empresa';
import { fechaHoraCorta } from '../../util/fechas';

const A = empresa.accesos;

type Acceso = components['schemas']['DataAccessResponse'];

const MAXIMO = 500;

/** Por si el servidor manda uno nuevo antes que la web: se enseña tal cual. */
const QUE: Readonly<Record<string, string | undefined>> = A.que;

const columnas: readonly Columna<Acceso>[] = [
  { clave: 'cuando', cabecera: A.columnas.cuando, celda: (a) => fechaHoraCorta(a.en) },
  { clave: 'quien', cabecera: A.columnas.quien, celda: (a) => a.quien },
  { clave: 'que', cabecera: A.columnas.que, celda: (a) => QUE[a.que ?? ''] ?? a.que },
  {
    clave: 'sobre',
    cabecera: A.columnas.sobre,
    celda: (a) => a.sobre ?? <span className="nx-sutil">{a.que === 'DENUNCIA' ? A.denuncia : A.varias}</span>,
  },
  { clave: 'detalle', cabecera: A.columnas.detalle, celda: (a) => a.detalle },
];

export function AccesosADatos() {
  const accesos = useQuery({
    queryKey: ['empresa', 'accesos'],
    queryFn: () => pedir(cliente.GET('/api/v1/empresa/accesos', {})),
  });

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={A.titulo} descripcion={A.explicacion} />
      <section className="nx-tarjeta">
        <EstadoDeConsulta consulta={accesos} cargando={<Esqueleto forma="tabla" lineas={4} />}>
          {(lista) =>
            lista.length === 0 ? (
              <Vacio icono="escudo" titulo={A.vacioTitulo} detalle={A.vacioTexto} />
            ) : (
              <>
                <Tabla
                  titulo={A.tabla}
                  columnas={columnas}
                  filas={lista}
                  claveDeFila={(a) => `${a.en}|${a.quienId}|${a.que}|${a.sobreId ?? ''}|${a.detalle ?? ''}`}
                />
                {lista.length >= MAXIMO && <p className="nx-sutil">{A.maximo}</p>}
              </>
            )
          }
        </EstadoDeConsulta>
      </section>
    </div>
  );
}
