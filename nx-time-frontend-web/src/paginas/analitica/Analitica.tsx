/**
 * La analítica completa (fase B4, ADR 022: solo en la web): absentismo y
 * puntualidad por mes, trimestre o año, en total, por departamento o por
 * persona, con el desglose por motivo y exportable a CSV.
 *
 * **El alcance lo pone el servidor**: RRHH y ADMIN ven la empresa; un GESTOR,
 * su departamento, y la cabecera lo dice. Un gestor sin departamento recibe un
 * 409 con su mensaje, que se enseña tal cual: no hay nada que calcular.
 *
 * **Se cuenta hasta ayer**, y la cabecera dice hasta qué día llega el cálculo.
 * Sin ningún día terminado, las cifras son una raya y no un 0 %: un 0 % el día
 * 1 afirmaría que nadie ha faltado.
 *
 * Periodo, día y agrupación van en la URL: se puede volver atrás, recargar o
 * pasar el enlace a quien tenga que verlo.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { cliente } from '../../api/cliente';
import { ErrorDeApi, pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Selector } from '../../componentes/Basicos';
import { Barras } from '../../componentes/Barras';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { analitica } from '../../i18n/es/analitica';
import { descargar } from '../../util/descargar';
import { fechaCorta, hoyEnEmpresa, minutos } from '../../util/fechas';
import { porcentaje } from '../../util/numeros';

const A = analitica;

type Periodo = 'MES' | 'TRIMESTRE' | 'ANIO';
type Agrupacion = 'EMPRESA' | 'DEPARTAMENTO' | 'EMPLEADO';
type Ventana = components['schemas']['AnalyticsWindow'];
type FilaAbsentismo = components['schemas']['AbsenteeismRow'];
type FilaPuntualidad = components['schemas']['PunctualityRow'];

const PERIODOS: Periodo[] = ['MES', 'TRIMESTRE', 'ANIO'];
const AGRUPACIONES: Agrupacion[] = ['EMPRESA', 'DEPARTAMENTO', 'EMPLEADO'];

function valido<T extends string>(valor: string | null, opciones: readonly T[], porDefecto: T): T {
  return opciones.includes(valor as T) ? (valor as T) : porDefecto;
}

/** «Del 1 sept al 30 sept, calculado hasta el 26 sept · toda la empresa». */
export function textoDeVentana(v: Ventana | undefined): string {
  if (v === undefined) return '';
  const quien = v.alcance === 'DEPARTAMENTO' && v.departamento ? A.departamento(v.departamento) : A.todaLaEmpresa;
  const hasta = v.evaluadoHasta ? A.evaluado(fechaCorta(v.evaluadoHasta)) : A.sinDias;
  return `${A.ventana(fechaCorta(v.desde), fechaCorta(v.hasta))}, ${hasta} · ${quien}`;
}

function minutosDeRetraso(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : A.minutosRetraso(valor);
}

function Cifra({ etiqueta, valor, ayuda }: { etiqueta: string; valor: string; ayuda?: string }) {
  return (
    <li>
      <div className="nx-contador">
        <span className="nx-contador__cifra">{valor}</span> <span>{etiqueta}</span>
        {ayuda !== undefined && <span className="nx-sutil">{ayuda}</span>}
      </div>
    </li>
  );
}

function Absentismo({ periodo, fecha, agrupar }: { periodo: Periodo; fecha: string; agrupar: Agrupacion }) {
  const [errorCsv, setErrorCsv] = useState<string | null>(null);
  const [bajando, setBajando] = useState(false);
  const consulta = useQuery({
    queryKey: ['analitica', 'absentismo', periodo, fecha, agrupar],
    queryFn: () => pedir(cliente.GET('/api/v1/analitica/absentismo', { params: { query: { periodo, fecha, agrupar } } })),
    placeholderData: (anterior) => anterior,
  });

  async function csv() {
    setErrorCsv(null);
    setBajando(true);
    try {
      await descargar(
        cliente.GET('/api/v1/analitica/absentismo.csv', { params: { query: { periodo, fecha, agrupar } }, parseAs: 'blob' }),
        `absentismo-${fecha}.csv`,
      );
    } catch (fallo) {
      setErrorCsv(fallo instanceof ErrorDeApi ? fallo.message : String(fallo));
    } finally {
      setBajando(false);
    }
  }

  const columnas: Columna<FilaAbsentismo>[] = [
    { clave: 'nombre', cabecera: A.nombre, celda: (f) => <strong>{f.nombre}</strong> },
    { clave: 'laborables', cabecera: A.diasLaborables, celda: (f) => String(f.diasLaborables ?? 0), numerica: true },
    { clave: 'trabajados', cabecera: A.diasTrabajados, celda: (f) => String(f.diasTrabajados ?? 0), numerica: true },
    { clave: 'justificados', cabecera: A.diasJustificados, celda: (f) => String(f.diasAusenciaJustificada ?? 0), numerica: true },
    { clave: 'sinFichaje', cabecera: A.diasSinFichaje, celda: (f) => String(f.diasSinFichaje ?? 0), numerica: true },
    { clave: 'vacaciones', cabecera: A.diasVacaciones, celda: (f) => String(f.diasVacaciones ?? 0), numerica: true },
    { clave: 'absentismo', cabecera: A.absentismo, celda: (f) => porcentaje(f.absentismo), numerica: true },
    { clave: 'sinJustificar', cabecera: A.sinJustificar, celda: (f) => porcentaje(f.absentismoSinJustificar), numerica: true },
  ];

  return (
    <EstadoDeConsulta consulta={consulta} cargando={<Esqueleto lineas={5} />}>
      {(r) => {
        const filas = r.filas ?? [];
        const total = r.total;
        const motivos = total?.motivos ?? [];
        return (
          <div className="nx-expediente">
            {agrupar !== 'EMPRESA' && filas.length > 0 && (
              <Barras
                filas={filas
                  .filter((f) => f.absentismo !== null && f.absentismo !== undefined)
                  .map((f) => ({ clave: f.id ?? f.nombre ?? '', texto: f.nombre ?? '', valor: Number(f.absentismo) }))}
                {...(total?.absentismo != null ? { media: Number(total.absentismo) } : {})}
                formato={porcentaje}
              />
            )}
            <Tabla
              titulo={A.tablaAbsentismo}
              columnas={columnas}
              filas={[...filas, ...(total ? [{ ...total, nombre: A.total, id: -1 }] : [])]}
              claveDeFila={(f) => f.id ?? f.nombre ?? ''}
            />
            <section aria-labelledby="analitica-motivos">
              <h3 id="analitica-motivos">{A.motivos}</h3>
              {motivos.length === 0 ? (
                <p className="nx-sutil">{A.sinMotivos}</p>
              ) : (
                <Barras
                  filas={motivos.map((m) => ({ clave: m.motivo ?? '', texto: m.etiqueta ?? m.motivo ?? '', valor: m.dias ?? 0 }))}
                  formato={A.dias}
                />
              )}
            </section>
            {errorCsv !== null && <Aviso>{errorCsv}</Aviso>}
            <div className="nx-acciones-fila">
              <Boton variante="secundario" ocupado={bajando} onClick={() => void csv()} title={A.csvAyuda}>
                {A.csv}
              </Boton>
            </div>
          </div>
        );
      }}
    </EstadoDeConsulta>
  );
}

function Puntualidad({ periodo, fecha, agrupar }: { periodo: Periodo; fecha: string; agrupar: Agrupacion }) {
  const consulta = useQuery({
    queryKey: ['analitica', 'puntualidad', periodo, fecha, agrupar],
    queryFn: () => pedir(cliente.GET('/api/v1/analitica/puntualidad', { params: { query: { periodo, fecha, agrupar } } })),
    placeholderData: (anterior) => anterior,
  });

  const columnas: Columna<FilaPuntualidad>[] = [
    { clave: 'nombre', cabecera: A.nombre, celda: (f) => <strong>{f.nombre}</strong> },
    { clave: 'entradas', cabecera: A.entradas, celda: (f) => String(f.entradasConHorario ?? 0), numerica: true },
    { clave: 'puntuales', cabecera: A.puntuales, celda: (f) => String(f.puntuales ?? 0), numerica: true },
    { clave: 'retrasos', cabecera: A.retrasos, celda: (f) => String(f.retrasos ?? 0), numerica: true },
    { clave: 'puntualidad', cabecera: A.puntualidad, celda: (f) => porcentaje(f.puntualidad), numerica: true },
    { clave: 'medio', cabecera: A.retrasoMedio, celda: (f) => minutosDeRetraso(f.retrasoMedioMinutos), numerica: true },
    { clave: 'mediano', cabecera: A.retrasoMediano, celda: (f) => minutosDeRetraso(f.retrasoMedianoMinutos), numerica: true },
    { clave: 'hasta30', cabecera: A.hasta30, celda: (f) => String(f.retrasosHasta30 ?? 0), numerica: true },
    { clave: 'masDe30', cabecera: A.masDe30, celda: (f) => String(f.retrasosDeMasDe30 ?? 0), numerica: true },
  ];

  return (
    <EstadoDeConsulta consulta={consulta} cargando={<Esqueleto lineas={5} />}>
      {(r) => {
        const filas = r.filas ?? [];
        const total = r.total;
        if ((total?.entradasConHorario ?? 0) === 0 && filas.length === 0) return <p className="nx-sutil">{A.sinDatos}</p>;
        return (
          <div className="nx-expediente">
            {agrupar !== 'EMPRESA' && filas.length > 0 && (
              <Barras
                filas={filas
                  .filter((f) => f.puntualidad !== null && f.puntualidad !== undefined)
                  .map((f) => ({ clave: f.id ?? f.nombre ?? '', texto: f.nombre ?? '', valor: Number(f.puntualidad) }))}
                formato={porcentaje}
              />
            )}
            <Tabla
              titulo={A.tablaPuntualidad}
              columnas={columnas}
              filas={[...filas, ...(total ? [{ ...total, nombre: A.total, id: -1 }] : [])]}
              claveDeFila={(f) => f.id ?? f.nombre ?? ''}
            />
          </div>
        );
      }}
    </EstadoDeConsulta>
  );
}

export function Analitica() {
  const [busqueda, setBusqueda] = useSearchParams();
  const periodo = valido(busqueda.get('periodo'), PERIODOS, 'MES');
  const agrupar = valido(busqueda.get('agrupar'), AGRUPACIONES, 'DEPARTAMENTO');
  const fecha = /^\d{4}-\d{2}-\d{2}$/.test(busqueda.get('fecha') ?? '') ? (busqueda.get('fecha') as string) : hoyEnEmpresa();
  const que = busqueda.get('ver') === 'puntualidad' ? 'puntualidad' : 'absentismo';

  function cambiar(clave: string, valor: string) {
    const nueva = new URLSearchParams(busqueda);
    nueva.set(clave, valor);
    setBusqueda(nueva, { replace: true });
  }

  const resumen = useQuery({
    queryKey: ['analitica', 'resumen', periodo, fecha],
    queryFn: () => pedir(cliente.GET('/api/v1/analitica/resumen', { params: { query: { periodo, fecha } } })),
    placeholderData: (anterior) => anterior,
  });

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{A.titulo}</h1>
      </header>
      <p className="nx-sutil">{A.explicacion}</p>

      <section className="nx-tarjeta">
        <div className="nx-filtros">
          <Selector
            id="analitica-periodo"
            etiqueta={A.periodo}
            value={periodo}
            onChange={(e) => cambiar('periodo', e.target.value)}
            opciones={PERIODOS.map((p) => ({ valor: p, texto: A.periodos[p] ?? p }))}
          />
          <Campo
            id="analitica-fecha"
            etiqueta={A.fecha}
            type="date"
            max={hoyEnEmpresa()}
            value={fecha}
            onChange={(e) => e.target.value !== '' && cambiar('fecha', e.target.value)}
          />
          <Selector
            id="analitica-agrupar"
            etiqueta={A.agrupar}
            value={agrupar}
            onChange={(e) => cambiar('agrupar', e.target.value)}
            opciones={AGRUPACIONES.map((g) => ({ valor: g, texto: A.agrupaciones[g] ?? g }))}
          />
        </div>
      </section>

      <EstadoDeConsulta consulta={resumen} cargando={<Esqueleto lineas={3} />}>
        {(r) => (
          <>
            <section className="nx-tarjeta" aria-labelledby="analitica-cifras">
              <h2 id="analitica-cifras">{A.cifras}</h2>
              <p className="nx-sutil">
                {textoDeVentana(r.ventana)} · {A.personas(r.personas ?? 0)}
              </p>
              <ul className="nx-contadores">
                <Cifra etiqueta={A.absentismo} valor={porcentaje(r.absentismo)} />
                <Cifra etiqueta={A.sinJustificar} valor={porcentaje(r.absentismoSinJustificar)} />
                <Cifra etiqueta={A.puntualidad} valor={porcentaje(r.puntualidad)} />
                <Cifra etiqueta={A.retrasoMedio} valor={minutosDeRetraso(r.retrasoMedioMinutos)} />
                <Cifra etiqueta={A.jornadasIncompletas} valor={porcentaje(r.jornadasIncompletas)} ayuda={A.jornadasIncompletasAyuda} />
                <Cifra etiqueta={A.mediaPorDia} valor={r.minutosMediosPorDia != null ? minutos(r.minutosMediosPorDia) : '—'} />
              </ul>
            </section>

            <section className="nx-tarjeta">
              <Pestanas
                etiqueta={A.pestanas}
                pestanas={[
                  { clave: 'absentismo', texto: A.absentismo },
                  { clave: 'puntualidad', texto: A.puntualidad },
                ]}
                activa={que}
                alCambiar={(clave) => cambiar('ver', clave)}
              >
                {que === 'absentismo' ? (
                  <Absentismo periodo={periodo} fecha={fecha} agrupar={agrupar} />
                ) : (
                  <Puntualidad periodo={periodo} fecha={fecha} agrupar={agrupar} />
                )}
              </Pestanas>
            </section>
          </>
        )}
      </EstadoDeConsulta>
    </div>
  );
}
