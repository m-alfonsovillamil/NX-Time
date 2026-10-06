/**
 * Mis ausencias: el saldo de vacaciones, lo que he pedido y cómo va, y pedir una nueva.
 *
 * Es `AusenciasScreen` + `SolicitudScreen` de la app. Los días hábiles de
 * cada petición los calcula el servidor (sin fines de semana ni festivos de la
 * empresa): aquí no se cuentan, porque dos cálculos del mismo número acaban
 * diciendo cosas distintas el año que cambia un festivo.
 */

import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { AreaDeTexto, Aviso, Boton, Campo, Insignia, Selector, Tarjeta, type Tono } from '../../componentes/Basicos';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Tabla } from '../../componentes/Tabla';
import { T } from '../../i18n/es';
import { ausencias as A, type EstadoAusencia, type TipoAusencia } from '../../i18n/es/ausencias';
import { fechaCorta, hoyEnEmpresa } from '../../util/fechas';
import { TRAS_SOLICITAR, useMisAusencias, useSaldoDeVacaciones, type Ausencia } from './consultas';

const TONO: Record<EstadoAusencia, Tono> = { PENDIENTE: 'aviso', APROBADA: 'exito', RECHAZADA: 'error' };

export interface FiltroDeAusencias {
  anio: string;
  tipo: string;
  estado: string;
}

const SIN_FILTRO: FiltroDeAusencias = { anio: '', tipo: '', estado: '' };

/** Una ausencia es de un año si lo toca: la que va del 28 de diciembre al 3 de enero es de los dos. */
export function filtrar(lista: readonly Ausencia[], filtro: FiltroDeAusencias): Ausencia[] {
  return lista.filter((a) => {
    if (filtro.tipo !== '' && a.tipo !== filtro.tipo) return false;
    if (filtro.estado !== '' && a.estado !== filtro.estado) return false;
    if (filtro.anio !== '') {
      const desde = (a.fechaInicio ?? '').slice(0, 4);
      const hasta = (a.fechaFin ?? a.fechaInicio ?? '').slice(0, 4);
      if (filtro.anio < desde || filtro.anio > hasta) return false;
    }
    return true;
  });
}

/** Los años en que hay alguna ausencia, el más reciente primero. */
function aniosDe(lista: readonly Ausencia[]): string[] {
  const anios = new Set<string>();
  for (const a of lista) {
    const desde = Number((a.fechaInicio ?? '').slice(0, 4));
    const hasta = Number((a.fechaFin ?? a.fechaInicio ?? '').slice(0, 4));
    for (let anio = desde; anio <= hasta && anio > 0; anio++) anios.add(String(anio));
  }
  return [...anios].sort().reverse();
}

function Saldo() {
  const anio = Number(hoyEnEmpresa().slice(0, 4));
  const saldo = useSaldoDeVacaciones(anio);
  // Como el resumen de «Mi jornada»: un extra. Si falla, no se enseña.
  if (saldo.data === undefined) return saldo.isPending ? <Esqueleto lineas={2} /> : null;
  const s = saldo.data;
  return (
    <Tarjeta titulo={A.saldo.titulo(anio)}>
      <Cifras>
        <Cifra
          icono="vacaciones"
          etiqueta={A.saldo.disponibles}
          valor={A.saldo.dias(s.diasDisponibles ?? 0)}
          {...((s.diasTotales ?? 0) > 0 ? { progreso: (s.diasDisponibles ?? 0) / (s.diasTotales ?? 1) } : {})}
        />
        <Cifra icono="hecho" etiqueta={A.saldo.consumidos} valor={A.saldo.dias(s.diasConsumidos ?? 0)} />
        <Cifra icono="horas-extra" etiqueta={A.saldo.pendientes} valor={A.saldo.dias(s.diasPendientes ?? 0)} />
        <Cifra icono="calendario" etiqueta={A.saldo.totales} valor={A.saldo.dias(s.diasTotales ?? 0)} />
      </Cifras>
    </Tarjeta>
  );
}

function FormularioDeSolicitud({ alTerminar }: { alTerminar: () => void }) {
  const [tipo, setTipo] = useState<TipoAusencia>('VACACIONES');
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState<string | null>(null);

  const solicitar = useMutacion(
    (cuerpo: { tipo: TipoAusencia; fechaInicio: string; fechaFin: string; motivo?: string }) =>
      pedir(cliente.POST('/api/v1/ausencias', { body: cuerpo })),
    { invalida: TRAS_SOLICITAR, exito: A.solicitud.enviada, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (desde === '' || hasta === '') return setError(A.solicitud.fechasIncompletas);
    if (hasta < desde) return setError(A.solicitud.fechaInvertida);
    setError(null);
    solicitar.mutate({
      tipo,
      fechaInicio: desde,
      fechaFin: hasta,
      ...(motivo.trim() !== '' ? { motivo: motivo.trim() } : {}),
    });
  }

  const mensaje = error ?? solicitar.error?.message ?? null;

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <Selector
        id="solicitud-tipo"
        etiqueta={A.solicitud.tipo}
        value={tipo}
        onChange={(e) => setTipo(e.target.value as TipoAusencia)}
        opciones={A.ordenDeTipos.map((t) => ({ valor: t, texto: A.tipos[t] }))}
      />
      <div className="nx-fila-campos">
        <Campo
          id="solicitud-desde"
          etiqueta={A.solicitud.desde}
          type="date"
          value={desde}
          onChange={(e) => {
            setDesde(e.target.value);
            // Lo más habitual es un solo día: se propone el mismo como fin.
            if (hasta === '' || hasta < e.target.value) setHasta(e.target.value);
          }}
        />
        <Campo
          id="solicitud-hasta"
          etiqueta={A.solicitud.hasta}
          type="date"
          value={hasta}
          min={desde || undefined}
          onChange={(e) => setHasta(e.target.value)}
        />
      </div>
      <p className="nx-sutil">{A.solicitud.explicacion}</p>
      <AreaDeTexto id="solicitud-motivo" etiqueta={A.solicitud.motivo} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={solicitar.isPending}>
          {A.solicitud.enviar}
        </Boton>
      </div>
    </form>
  );
}

function Detalle({ a }: { a: Ausencia }) {
  const lineas = [
    a.motivo ? A.motivo(a.motivo) : null,
    a.aprobadoPor?.nombre ? A.resueltaPor(a.aprobadoPor.nombre) : null,
    a.comentarioResolucion ? A.respuesta(a.comentarioResolucion) : null,
  ].filter((l): l is string => l !== null);
  if (lineas.length === 0) return null;
  return (
    <span className="nx-detalle">
      {lineas.map((l) => (
        <span key={l}>{l}</span>
      ))}
    </span>
  );
}

function Lista({ lista }: { lista: readonly Ausencia[] }) {
  const [filtro, setFiltro] = useState<FiltroDeAusencias>(SIN_FILTRO);
  const visibles = filtrar(lista, filtro);
  const hayFiltro = filtro.anio !== '' || filtro.tipo !== '' || filtro.estado !== '';

  if (lista.length === 0) return <Vacio titulo={A.vacioTitulo} detalle={A.vacioTexto} />;

  return (
    <>
      <div className="nx-filtros" role="group" aria-label={A.filtros.titulo}>
        <Selector
          id="filtro-anio"
          etiqueta={A.filtros.anio}
          value={filtro.anio}
          onChange={(e) => setFiltro({ ...filtro, anio: e.target.value })}
          opciones={[{ valor: '', texto: A.filtros.todosLosAnios }, ...aniosDe(lista).map((a) => ({ valor: a, texto: a }))]}
        />
        <Selector
          id="filtro-tipo"
          etiqueta={A.filtros.tipo}
          value={filtro.tipo}
          onChange={(e) => setFiltro({ ...filtro, tipo: e.target.value })}
          opciones={[{ valor: '', texto: A.filtros.todosLosTipos }, ...A.ordenDeTipos.map((t) => ({ valor: t, texto: A.tipos[t] }))]}
        />
        <Selector
          id="filtro-estado"
          etiqueta={A.filtros.estado}
          value={filtro.estado}
          onChange={(e) => setFiltro({ ...filtro, estado: e.target.value })}
          opciones={[
            { valor: '', texto: A.filtros.todosLosEstados },
            ...(Object.keys(A.estados) as EstadoAusencia[]).map((e) => ({ valor: e, texto: A.estados[e] })),
          ]}
        />
        {hayFiltro && (
          <Boton variante="texto" onClick={() => setFiltro(SIN_FILTRO)}>
            {A.filtros.quitar}
          </Boton>
        )}
      </div>

      {visibles.length === 0 ? (
        <Vacio titulo={A.filtros.sinResultados} />
      ) : (
        <Tabla
          titulo={A.tabla.titulo}
          filas={visibles}
          claveDeFila={(a) => a.id ?? 0}
          columnas={[
            { clave: 'fechas', cabecera: A.tabla.fechas, celda: (a) => A.rango(fechaCorta(a.fechaInicio), fechaCorta(a.fechaFin)) },
            { clave: 'tipo', cabecera: A.tabla.tipo, celda: (a) => (a.tipo ? A.tipos[a.tipo] : '') },
            {
              clave: 'dias',
              cabecera: A.tabla.dias,
              celda: (a) => (a.diasHabiles !== undefined ? A.diasHabiles(a.diasHabiles) : ''),
              numerica: true,
            },
            {
              clave: 'estado',
              cabecera: A.tabla.estado,
              celda: (a) => (a.estado ? <Insignia tono={TONO[a.estado]}>{A.estados[a.estado]}</Insignia> : null),
            },
            { clave: 'detalle', cabecera: A.tabla.detalle, celda: (a) => <Detalle a={a} /> },
          ]}
        />
      )}
    </>
  );
}

export function Ausencias() {
  const mias = useMisAusencias();
  const [solicitando, setSolicitando] = useState(false);

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{A.titulo}</h1>
        <Boton onClick={() => setSolicitando(true)}>{A.solicitar}</Boton>
      </header>

      <Saldo />

      <section className="nx-tarjeta">
        <EstadoDeConsulta consulta={mias} cargando={<Esqueleto lineas={5} />}>
          {(lista) => <Lista lista={lista} />}
        </EstadoDeConsulta>
      </section>

      <Dialogo abierto={solicitando} titulo={A.solicitud.titulo} alCerrar={() => setSolicitando(false)} acciones={null}>
        {solicitando && <FormularioDeSolicitud alTerminar={() => setSolicitando(false)} />}
      </Dialogo>
    </div>
  );
}
