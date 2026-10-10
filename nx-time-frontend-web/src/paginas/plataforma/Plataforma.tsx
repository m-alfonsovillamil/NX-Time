/**
 * El panel de plataforma (ADR 040): qué empresas hay dadas de alta en este
 * servicio y cuánto lo usan. Para quien lo mantiene, no para ninguna empresa.
 *
 * **Solo lo ve quien tiene `plataforma:ver`**, que no sale de ningún rol: lo
 * decide el servidor por una lista de correos. Un ADMIN no ve ni el apartado.
 *
 * **Solo cifras, y solo lectura.** Lo único que hay de personas es el nombre y
 * el correo de los administradores de cada empresa, en su detalle.
 *
 * La lista y el detalle son la misma sección: `/plataforma` y
 * `/plataforma?empresa=7`, como las demás páginas que abren una ficha.
 *
 * **La traza de auditoría, entera.** La cadena es una sola para todas las
 * empresas, y a cada una se le dan solo sus cifras: si se rompe en una fila
 * ajena, se le dice que avise a quien administra el servicio. Ese alguien lo
 * lee aquí, con el número de fila y de qué empresa es.
 */

import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useListaPaginada, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Destacado, Insignia, Selector, Tarjeta } from '../../componentes/Basicos';
import { Barras } from '../../componentes/Barras';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { ErrorConReintento, EstadoDeConsulta, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { plataforma } from '../../i18n/es/plataforma';
import { diaEnEmpresa, fechaCompleta, fechaCorta, fechaHoraCorta } from '../../util/fechas';
import { DetalleDeEmpresa } from './DetalleDeEmpresa';

const P = plataforma;

export const CLAVE_PLATAFORMA = ['plataforma'] as const;

type Empresa = components['schemas']['PlatformCompanyResponse'];
type Resumen = components['schemas']['PlatformSummaryResponse'];
type Tarea = components['schemas']['TaskStatus'];
/** Los del contrato: uno que no exista no compila al pasarlo a la consulta. */
type Orden = 'NOMBRE' | 'ALTA' | 'EMPLEADOS' | 'ACTIVIDAD';

const ORDENES: readonly Orden[] = ['NOMBRE', 'ALTA', 'EMPLEADOS', 'ACTIVIDAD'];

/** Cuánto se espera tras la última tecla antes de buscar: una petición por palabra, no por letra. */
const ESPERA_AL_TECLEAR = 300;

/** El día de un instante, con su año (las altas son de años distintos), o el texto de «no hay fecha» que toque. */
export function diaDe(instante: string | null | undefined, sinFecha: string): string {
  return instante === null || instante === undefined ? sinFecha : fechaCompleta(diaEnEmpresa(instante));
}

/** El id de la empresa de `?empresa=7`, o `null` si no hay o no es un número. */
export function empresaElegida(busqueda: URLSearchParams): number | null {
  const texto = busqueda.get('empresa') ?? '';
  return /^[1-9]\d{0,15}$/.test(texto) ? Number(texto) : null;
}

/** Lo tecleado, cuando deja de cambiar un momento. */
function useRetardado(valor: string, espera: number): string {
  const [retardado, setRetardado] = useState(valor);
  useEffect(() => {
    const temporizador = setTimeout(() => setRetardado(valor), espera);
    return () => clearTimeout(temporizador);
  }, [valor, espera]);
  return retardado;
}

export function Plataforma() {
  const [busqueda] = useSearchParams();
  const id = empresaElegida(busqueda);
  return id !== null ? <DetalleDeEmpresa id={id} /> : <Instalacion />;
}

function Instalacion() {
  const resumen = useQuery({
    queryKey: [...CLAVE_PLATAFORMA, 'resumen'],
    queryFn: () => pedir(cliente.GET('/api/v1/plataforma/resumen', {})),
  });

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={P.titulo} descripcion={P.explicacion} />

      <Tarjeta titulo={P.resumen.titulo} icono="servidores">
        <EstadoDeConsulta consulta={resumen} cargando={<Esqueleto forma="recuadros" lineas={4} />}>
          {(r) => <Totales resumen={r} />}
        </EstadoDeConsulta>
      </Tarjeta>

      <Tarjeta titulo={P.lista.titulo} icono="mundo">
        <Empresas />
      </Tarjeta>

      <div className="nx-composicion nx-composicion--mitades nx-composicion--arriba">
        <Tarjeta titulo={P.tareas.titulo} icono="lapso" className="nx-expediente">
          <EstadoDeConsulta consulta={resumen} cargando={<Esqueleto forma="tabla" lineas={4} />}>
            {(r) => <Tareas tareas={r.tareas?.tareas ?? []} />}
          </EstadoDeConsulta>
        </Tarjeta>

        <div className="nx-columna">
          <Tarjeta titulo={P.cadena.titulo} icono="verificado" descripcion={P.cadena.descripcion} className="nx-expediente">
            <EstadoDeConsulta consulta={resumen} cargando={<Esqueleto forma="recuadros" lineas={2} />}>
              {(r) => <Cadena cadena={r.cadena} />}
            </EstadoDeConsulta>
          </Tarjeta>

          <Tarjeta titulo={P.altas.titulo} icono="tendencia" descripcion={P.altas.descripcion}>
            <EstadoDeConsulta consulta={resumen} cargando={<Esqueleto lineas={4} />}>
              {(r) => <Altas altas={r.altasPorSemana ?? []} />}
            </EstadoDeConsulta>
          </Tarjeta>
        </div>
      </div>
    </div>
  );
}

function Totales({ resumen: r }: { resumen: Resumen }) {
  const sinConfirmar = r.registrosSinConfirmar ?? 0;
  return (
    <Cifras etiqueta={P.resumen.titulo}>
      <Cifra
        icono="mundo"
        etiqueta={P.resumen.empresas}
        valor={r.empresas ?? 0}
        detalle={P.resumen.conActividad(r.empresasConActividad ?? 0)}
      />
      <Cifra icono="grupo" etiqueta={P.resumen.cuentas} valor={r.empleadosActivos ?? 0} />
      <Cifra icono="reloj" etiqueta={P.resumen.fichajesHoy} valor={r.fichajesHoy ?? 0} />
      <Cifra
        icono="info"
        etiqueta={P.resumen.sinConfirmar}
        valor={sinConfirmar}
        {...(sinConfirmar > 0 ? { tono: 'destacada' as const, detalle: P.resumen.sinConfirmarDetalle } : {})}
      />
    </Cifras>
  );
}

/* ------------------------------------------------------------------ */
/* La lista                                                            */
/* ------------------------------------------------------------------ */

const COLUMNAS: readonly Columna<Empresa>[] = [
  {
    clave: 'empresa',
    cabecera: P.lista.empresa,
    celda: (e) => (
      <>
        <Link className="nx-enlace" to={`/plataforma?empresa=${e.id ?? 0}`}>
          {e.nombre}
        </Link>
        {e.registroSinConfirmar === true && (
          <>
            {' '}
            <Insignia tono="aviso">{P.lista.sinConfirmar}</Insignia>
          </>
        )}
      </>
    ),
  },
  { clave: 'alta', cabecera: P.lista.alta, celda: (e) => diaDe(e.creadaEn, P.lista.sinFecha) },
  { clave: 'empleados', cabecera: P.lista.empleados, celda: (e) => e.empleadosActivos ?? 0, numerica: true },
  { clave: 'fichajes', cabecera: P.lista.fichajes, celda: (e) => e.fichajesEn7Dias ?? 0, numerica: true },
  { clave: 'personas', cabecera: P.lista.personas, celda: (e) => e.personasQueFichan ?? 0, numerica: true },
  { clave: 'ultimoFichaje', cabecera: P.lista.ultimoFichaje, celda: (e) => fechaHoraCorta(e.ultimoFichaje) || P.lista.nunca },
  { clave: 'ultimaSesion', cabecera: P.lista.ultimaSesion, celda: (e) => fechaHoraCorta(e.ultimaSesion) || P.lista.nunca },
];

function Empresas() {
  const [texto, setTexto] = useState('');
  const [orden, setOrden] = useState<Orden>('NOMBRE');
  const buscado = useRetardado(texto.trim(), ESPERA_AL_TECLEAR);

  const lista = useListaPaginada([...CLAVE_PLATAFORMA, 'empresas', buscado, orden], (pagina) =>
    pedir(
      cliente.GET('/api/v1/plataforma/empresas', {
        params: { query: { pagina, orden, ...(buscado !== '' ? { busqueda: buscado } : {}) } },
      }),
    ),
  );
  const total = lista.data?.pages[0]?.totalElementos;

  return (
    <>
      <div className="nx-filtros">
        <Campo id="plataforma-buscar" etiqueta={P.lista.buscar} type="search" value={texto} onChange={(e) => setTexto(e.target.value)} />
        <Selector
          id="plataforma-orden"
          etiqueta={P.lista.orden}
          value={orden}
          onChange={(e) => setOrden(e.target.value as Orden)}
          opciones={ORDENES.map((o) => ({ valor: o, texto: P.lista.ordenes[o] }))}
        />
      </div>
      {lista.isPending ? (
        <Esqueleto forma="tabla" lineas={6} />
      ) : lista.isError && lista.elementos.length === 0 ? (
        <ErrorConReintento mensaje={lista.error.message} alReintentar={() => void lista.refetch()} />
      ) : lista.elementos.length === 0 ? (
        <Vacio icono="mundo" titulo={buscado !== '' ? P.lista.sinResultados(buscado) : P.lista.vacio} />
      ) : (
        <>
          {total !== undefined && (
            <p className="nx-sutil" role="status">
              {P.lista.cuantas(total)}
            </p>
          )}
          <Tabla titulo={P.lista.tabla} columnas={COLUMNAS} filas={lista.elementos} claveDeFila={(e) => e.id ?? 0} />
          <FinDeLista
            hayMas={lista.hasNextPage}
            cargando={lista.isFetchingNextPage}
            fallo={lista.isFetchNextPageError}
            alPedirMas={() => void lista.fetchNextPage()}
          />
        </>
      )}
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Las tareas, la traza y las altas                                    */
/* ------------------------------------------------------------------ */

const COLUMNAS_DE_TAREAS: readonly Columna<Tarea>[] = [
  { clave: 'tarea', cabecera: P.tareas.tarea, celda: (t) => P.tareas.nombres[t.tarea ?? ''] ?? t.tarea ?? '' },
  {
    clave: 'estado',
    cabecera: P.tareas.estado,
    celda: (t) => <Insignia tono={t.ok === true ? 'exito' : 'error'}>{t.ok === true ? P.tareas.ok : P.tareas.fallo}</Insignia>,
  },
  { clave: 'ultima', cabecera: P.tareas.ultima, celda: (t) => fechaHoraCorta(t.ultimaEjecucion) || P.tareas.nunca },
];

function Tareas({ tareas }: { tareas: readonly Tarea[] }) {
  const mal = tareas.filter((t) => t.ok !== true).length;
  return (
    <>
      <Destacado
        tono={mal === 0 ? 'bien' : 'mal'}
        icono={mal === 0 ? 'hecho' : 'error'}
        titulo={mal === 0 ? P.tareas.bien : P.tareas.mal(mal)}
      />
      <Tabla titulo={P.tareas.tabla} columnas={COLUMNAS_DE_TAREAS} filas={tareas} claveDeFila={(t) => t.tarea ?? ''} />
    </>
  );
}

function Cadena({ cadena }: { cadena: Resumen['cadena'] }) {
  const comprobar = useMutacion(() => pedir(cliente.GET('/api/v1/plataforma/integridad', {})));
  const r = comprobar.data;
  const sinRevisar = cadena?.movimientosSinRevisar ?? 0;

  return (
    <>
      <p className="nx-sutil">
        {cadena?.ultimaComprobacion !== undefined && cadena.ultimaComprobacion !== null
          ? P.cadena.comprobada(fechaHoraCorta(cadena.ultimaComprobacion))
          : P.cadena.nunca}
      </p>
      <Cifras>
        <Cifra icono="hecho" etiqueta={P.cadena.movimientos} valor={cadena?.movimientosComprobados ?? 0} />
        <Cifra
          icono="horas-extra"
          etiqueta={P.cadena.sinRevisar}
          valor={sinRevisar}
          {...(sinRevisar > 0 ? { tono: 'destacada' as const } : {})}
        />
      </Cifras>
      <div>
        <Boton variante="secundario" ocupado={comprobar.isPending} onClick={() => comprobar.mutate(undefined)}>
          {P.cadena.comprobar}
        </Boton>
      </div>
      {comprobar.error !== null && <Aviso>{comprobar.error.message}</Aviso>}
      {r !== undefined && (
        <Destacado
          role="status"
          tono={r.intacta === true ? 'bien' : 'mal'}
          icono={r.intacta === true ? 'verificado' : 'error'}
          titulo={r.intacta === true ? P.cadena.intacta : P.cadena.rota}
        >
          <p>{P.cadena.detalle(r.movimientos ?? 0, r.comprobados ?? 0, r.soloEnlace ?? 0)}</p>
          {r.intacta !== true && r.primerFallo !== undefined && r.primerFallo !== null && (
            <p>{P.cadena.fallo(r.primerFallo, r.motivo ?? '')}</p>
          )}
          {r.intacta !== true &&
            (r.empresaDelFallo !== undefined && r.empresaDelFallo !== null ? (
              <p>
                {P.cadena.deQuien}{' '}
                <Link className="nx-enlace" to={`/plataforma?empresa=${r.empresaDelFallo}`}>
                  {P.cadena.verEmpresa(r.empresaDelFallo)}
                </Link>
              </p>
            ) : (
              <p>{P.cadena.deNadie}</p>
            ))}
        </Destacado>
      )}
    </>
  );
}

function Altas({ altas }: { altas: NonNullable<Resumen['altasPorSemana']> }) {
  const total = altas.reduce((suma, a) => suma + (a.altas ?? 0), 0);
  if (total === 0) return <p className="nx-sutil">{P.altas.ninguna}</p>;
  return (
    <>
      <p className="nx-sutil">{P.altas.resumen(total)}</p>
      <Barras
        filas={altas.map((a) => ({ clave: a.semana ?? '', texto: fechaCorta(a.semana), valor: a.altas ?? 0 }))}
        formato={String}
      />
    </>
  );
}
