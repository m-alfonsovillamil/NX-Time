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

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import { useSesion } from '../../api/useSesion';
import { Barras } from '../../componentes/Barras';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { empresa } from '../../i18n/es/empresa';
import { fechaCorta, hoyEnEmpresa, mesYAnio, minutos } from '../../util/fechas';
import { porcentaje } from '../../util/numeros';

const E = empresa.panel;
const A = empresa.analitica;

export { porcentaje } from '../../util/numeros';

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
              <Cifras>
                <Cifra icono="ausencia" etiqueta={A.absentismo} valor={porcentaje(r.absentismo)} />
                <Cifra icono="reloj" etiqueta={A.puntualidad} valor={porcentaje(r.puntualidad)} />
              </Cifras>
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
    <div className="nx-pagina">
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
                <Cifras>
                  <Cifra icono="grupo" etiqueta={E.empleadosActivos} valor={p.empleadosActivos ?? 0} />
                  <Cifra icono="reloj" etiqueta={E.horasMes} valor={minutos(p.minutosMesEmpresa ?? 0)} />
                  <Cifra
                    icono="ausencia-aprobar"
                    etiqueta={E.ausencias}
                    valor={p.ausenciasPendientes ?? 0}
                    tono={(p.ausenciasPendientes ?? 0) > 0 ? 'destacada' : 'normal'}
                    {...(puede('ausencia:aprobar') ? { a: '/ausencias-equipo/pendientes' } : {})}
                  />
                  <Cifra
                    icono="incidencia"
                    etiqueta={E.incidencias}
                    valor={p.incidenciasAbiertas ?? 0}
                    tono={(p.incidenciasAbiertas ?? 0) > 0 ? 'alerta' : 'normal'}
                    detalle={E.incidenciasAyuda}
                  />
                  {puede('horasextra:revisar') && (
                    <Cifra icono="horas-extra" etiqueta={E.horasExtra} valor={p.horasExtraAbiertas ?? 0} a="/horas-extra" />
                  )}
                  {puede('denuncia:instruir') && <Cifra icono="mazo" etiqueta={E.denuncias} valor={p.denunciasAbiertas ?? 0} />}
                </Cifras>
              </section>

              <section className="nx-tarjeta" aria-labelledby="empresa-horas">
                <h2 id="empresa-horas">{E.horasPorEmpleado}</h2>
                {horas.length === 0 ? (
                  <Vacio icono="reloj" titulo={E.sinHoras} />
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
