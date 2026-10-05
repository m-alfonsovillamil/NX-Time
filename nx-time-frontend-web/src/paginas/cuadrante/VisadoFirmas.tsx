/**
 * El visado de firmas (ADR 025): cómo está un mes en la empresa, persona por
 * persona, y dar el visto bueno a las firmas vigentes. Solo con `firma:visar`
 * (RRHH y ADMIN); lo decidió el ADR 022 como cosa de la web.
 *
 * Empieza en el **mes anterior**, que es el que se firma: el en curso todavía
 * no se puede firmar. Visar solo se ofrece sobre una firma VIGENTE y sin
 * visar; nadie visa la suya (el servidor lo rechaza y su mensaje se lee). Una
 * firma que quedó sin efecto dice por qué: se corrigió un fichaje del mes
 * después de firmarlo, y hay que volver a pedirla.
 *
 * La huella se puede comprobar desde aquí: el servidor recalcula hoy el
 * resumen del mes y lo compara con el firmado.
 */

import { useQuery } from '@tanstack/react-query';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Insignia, type Tono } from '../../componentes/Basicos';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { cuadrante } from '../../i18n/es/cuadrante';
import { diaEnEmpresa, duracion, fechaCorta, mesYAnio } from '../../util/fechas';
import { NavegadorDeMes, useMes } from '../proyectos/mes';
import { Comprobacion } from './Firmas';

const V = cuadrante.visado;
const F = cuadrante.firmas;

type Fila = components['schemas']['TeamSignatureResponse'];

const TONO: Record<string, Tono> = { SIN_FIRMAR: 'aviso', VIGENTE: 'exito', INVALIDADA: 'error' };

export function resumenDe(filas: readonly Fila[]) {
  return {
    firmadas: filas.filter((f) => f.estado === 'VIGENTE').length,
    visadas: filas.filter((f) => f.estado === 'VIGENTE' && f.firma?.visadaPor).length,
    total: filas.length,
  };
}

export function VisadoFirmas() {
  const mes = useMes(-1);
  const equipo = useQuery({
    queryKey: ['firmas', 'equipo', mes.anio, mes.mes],
    queryFn: () => pedir(cliente.GET('/api/v1/firmas/equipo', { params: { query: { anio: mes.anio, mes: mes.mes } } })),
    placeholderData: (anterior) => anterior,
  });
  const visar = useMutacion((id: number) => pedir(cliente.POST('/api/v1/firmas/{id}/visado', { params: { path: { id } } })), {
    invalida: [['firmas']],
    exito: V.visada,
  });

  const columnas: Columna<Fila>[] = [
    { clave: 'persona', cabecera: V.persona, celda: (f) => <strong>{f.usuario}</strong> },
    {
      clave: 'estado',
      cabecera: V.estado,
      celda: (f) => (
        <div className="nx-celda-apilada">
          <Insignia tono={TONO[f.estado ?? ''] ?? 'neutro'}>{V.estados[f.estado ?? ''] ?? f.estado}</Insignia>
          {f.estado === 'INVALIDADA' && f.firma?.motivoInvalidacion && <span className="nx-sutil">{V.motivo(f.firma.motivoInvalidacion)}</span>}
        </div>
      ),
    },
    { clave: 'firmada', cabecera: V.firmadaEl, celda: (f) => (f.firma?.firmadaEn ? fechaCorta(diaEnEmpresa(f.firma.firmadaEn)) : '') },
    {
      clave: 'registro',
      cabecera: V.registro,
      celda: (f) => (f.firma ? F.jornadas(f.firma.jornadas ?? 0, duracion(f.firma.segundosNetos ?? 0)) : ''),
    },
    {
      clave: 'visado',
      cabecera: V.visadoCol,
      celda: (f) => (f.firma?.visadaPor ? V.visadaPor(f.firma.visadaPor, f.firma.visadaEn ? fechaCorta(diaEnEmpresa(f.firma.visadaEn)) : '') : ''),
    },
    {
      clave: 'acciones',
      cabecera: V.acciones,
      celda: (f) => {
        const firma = f.firma;
        if (firma?.id === undefined) return null;
        const id = firma.id;
        return (
          <div className="nx-acciones-fila" role="group" aria-label={V.accionesDe(f.usuario ?? '')}>
            {f.estado === 'VIGENTE' && !firma.visadaPor && (
              <Boton variante="texto" ocupado={visar.isPending && visar.variables === id} onClick={() => visar.mutate(id)}>
                {V.visar}
              </Boton>
            )}
            <Comprobacion firmaId={id} />
          </div>
        );
      },
    },
  ];

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{V.titulo}</h1>
      </header>
      <p className="nx-sutil">{V.explicacion}</p>
      <section className="nx-tarjeta">
        <NavegadorDeMes mes={mes} id="visado-mes" titulo={V.delMes(mesYAnio(mes.anio, mes.mes).toLowerCase())} />
        {visar.error !== null && <Aviso>{visar.error.message}</Aviso>}
        <EstadoDeConsulta consulta={equipo} cargando={<Esqueleto lineas={6} />}>
          {(filas) => {
            if (filas.length === 0) return <Vacio titulo={V.vacio} />;
            const r = resumenDe(filas);
            return (
              <>
                <p>{V.resumen(r.firmadas, r.total, r.visadas)}</p>
                <Tabla
                  titulo={V.tablaTitulo}
                  columnas={columnas}
                  filas={[...filas].sort((a, b) => (a.usuario ?? '').localeCompare(b.usuario ?? '', 'es'))}
                  claveDeFila={(f) => f.usuarioId ?? 0}
                />
              </>
            );
          }}
        </EstadoDeConsulta>
      </section>
    </div>
  );
}
