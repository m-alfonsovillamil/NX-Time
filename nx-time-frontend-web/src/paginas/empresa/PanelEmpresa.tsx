/**
 * El panel de empresa: cómo va el mes en curso. Es el `PanelEmpresaScreen` de
 * la app (sin la plantilla ni los departamentos, que en la web tienen su
 * página, ni los informes, que están en «Informes»).
 *
 * Las incidencias abiertas van en rojo cuando las hay: son jornadas que cerró
 * el sistema por falta de fichaje de salida y que nadie ha corregido, y entre
 * cifras informativas pasarían desapercibidas. La tarjeta de analítica dice
 * hasta qué día llega el cálculo y de quién es: un «0,0 %» el día 1 afirmaría
 * que nadie ha faltado, así que sin días terminados se dice eso.
 */

import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import { useSesion } from '../../api/useSesion';
import { Barras } from '../../componentes/Barras';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { empresa } from '../../i18n/es/empresa';
import { fechaCorta, hoyEnEmpresa, mesYAnio, minutos } from '../../util/fechas';
import { porcentaje } from '../../util/numeros';

const E = empresa.panel;
const A = empresa.analitica;

export { porcentaje } from '../../util/numeros';

function Indicador({ etiqueta, valor, alerta = false, ayuda, a }: { etiqueta: string; valor: string; alerta?: boolean; ayuda?: string; a?: string }) {
  const contenido: ReactNode = (
    <>
      <span className="nx-contador__cifra">{valor}</span> <span>{etiqueta}</span>
      {ayuda !== undefined && <span className="nx-sutil">{ayuda}</span>}
    </>
  );
  const clase = `nx-contador${alerta ? ' nx-contador--alerta' : ''}`;
  return <li>{a !== undefined ? <Link className={clase} to={a}>{contenido}</Link> : <div className={clase}>{contenido}</div>}</li>;
}

function TarjetaDeAnalitica() {
  const resumen = useQuery({
    queryKey: ['analitica', 'resumen', 'MES'],
    queryFn: () => pedir(cliente.GET('/api/v1/analitica/resumen', { params: { query: { periodo: 'MES' } } })),
  });
  return (
    <section className="nx-tarjeta" aria-labelledby="empresa-analitica">
      <h2 id="empresa-analitica">{A.titulo}</h2>
      <EstadoDeConsulta consulta={resumen} cargando={<Esqueleto lineas={2} />}>
        {(r) => {
          const hasta = r.ventana?.evaluadoHasta;
          return (
            <>
              <ul className="nx-contadores">
                <Indicador etiqueta={A.absentismo} valor={porcentaje(r.absentismo)} />
                <Indicador etiqueta={A.puntualidad} valor={porcentaje(r.puntualidad)} />
              </ul>
              <p className="nx-sutil">
                {hasta ? A.alcance(fechaCorta(hasta), r.ventana?.departamento ?? A.todaLaEmpresa) : A.sinDias}
              </p>
              <p className="nx-sutil">{A.ayuda}</p>
            </>
          );
        }}
      </EstadoDeConsulta>
    </section>
  );
}

export function PanelEmpresa() {
  const { puede } = useSesion();
  const hoy = hoyEnEmpresa();
  const anio = Number(hoy.slice(0, 4));
  const mes = Number(hoy.slice(5, 7));

  const panel = useQuery({ queryKey: ['dashboard', 'empresa'], queryFn: () => pedir(cliente.GET('/api/v1/dashboard/empresa', {})) });
  const proyectos = useQuery({
    queryKey: ['proyectos', 'horas', anio, mes],
    queryFn: () => pedir(cliente.GET('/api/v1/proyectos/horas', { params: { query: { anio, mes } } })),
    enabled: puede('proyecto:gestionar'),
  });

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{E.titulo}</h1>
      </header>

      <EstadoDeConsulta consulta={panel} cargando={<Esqueleto lineas={6} />}>
        {(p) => {
          const horas = p.horasPorEmpleado ?? [];
          const media = horas.length > 0 ? Math.round(horas.reduce((s, h) => s + (h.minutos ?? 0), 0) / horas.length) : 0;
          return (
            <>
              <section className="nx-tarjeta" aria-labelledby="empresa-mes">
                <h2 id="empresa-mes">{E.delMes(mesYAnio(anio, mes).toLowerCase())}</h2>
                <ul className="nx-contadores">
                  <Indicador etiqueta={E.empleadosActivos} valor={String(p.empleadosActivos ?? 0)} />
                  <Indicador etiqueta={E.horasMes} valor={minutos(p.minutosMesEmpresa ?? 0)} />
                  <Indicador
                    etiqueta={E.ausencias}
                    valor={String(p.ausenciasPendientes ?? 0)}
                    {...(puede('ausencia:aprobar') ? { a: '/ausencias-equipo/pendientes' } : {})}
                  />
                  <Indicador
                    etiqueta={E.incidencias}
                    valor={String(p.incidenciasAbiertas ?? 0)}
                    alerta={(p.incidenciasAbiertas ?? 0) > 0}
                    ayuda={E.incidenciasAyuda}
                  />
                  {puede('horasextra:revisar') && <Indicador etiqueta={E.horasExtra} valor={String(p.horasExtraAbiertas ?? 0)} a="/horas-extra" />}
                  {puede('denuncia:instruir') && <Indicador etiqueta={E.denuncias} valor={String(p.denunciasAbiertas ?? 0)} />}
                </ul>
              </section>

              <section className="nx-tarjeta" aria-labelledby="empresa-horas">
                <h2 id="empresa-horas">{E.horasPorEmpleado}</h2>
                {horas.length === 0 ? (
                  <p className="nx-sutil">{E.sinHoras}</p>
                ) : (
                  <>
                    <Barras
                      filas={[...horas]
                        .sort((a, b) => (b.minutos ?? 0) - (a.minutos ?? 0))
                        .map((h) => ({ clave: h.usuarioId ?? 0, texto: h.nombre ?? '', valor: h.minutos ?? 0 }))}
                      media={media}
                    />
                    <p className="nx-sutil">{E.mediaEquipo(minutos(media))}</p>
                  </>
                )}
              </section>
            </>
          );
        }}
      </EstadoDeConsulta>

      {puede('proyecto:gestionar') && (proyectos.data?.proyectos ?? []).length > 0 && (
        <section className="nx-tarjeta" aria-labelledby="empresa-proyectos">
          <h2 id="empresa-proyectos">{E.horasPorProyecto}</h2>
          <Barras
            filas={(proyectos.data?.proyectos ?? []).map((x) => ({ clave: x.proyectoId ?? 0, texto: `${x.codigo ?? ''} · ${x.nombre ?? ''}`, valor: x.minutos ?? 0 }))}
          />
        </section>
      )}

      {puede('analitica:leer') && <TarjetaDeAnalitica />}
    </div>
  );
}
