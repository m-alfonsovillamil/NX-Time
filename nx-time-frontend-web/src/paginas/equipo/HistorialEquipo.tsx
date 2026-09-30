/**
 * El historial del equipo: las jornadas de los EMPLEADO de la empresa, las más
 * recientes primero, con filtro por persona. Es la `HistorialEquipoScreen` de
 * la app, en tabla, porque en escritorio es donde se revisa.
 *
 * **El filtro lo hace el servidor** (`usuarioId`, fase W5). La app filtraba
 * sobre lo ya cargado, y con páginas eso obligaba a pedir y pedir hasta dar
 * con alguien que ficha poco. La persona elegida va en la URL (`?persona=`):
 * se puede recargar, volver atrás o pasar el enlace.
 *
 * Con `fichaje:corregir` se puede proponer una corrección sobre la jornada de
 * otra persona, que la aprueba ella (ADR 015); con `fichaje:auditoria`, ver su
 * traza. Hoy las dos son de RRHH y ADMIN: un GESTOR ve el historial y nada más,
 * y los botones no salen para no ofrecer algo que el servidor va a rechazar.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useListaPaginada } from '../../api/consultas';
import type { components } from '../../api/schema';
import { useSesion } from '../../api/useSesion';
import { Boton, Selector } from '../../componentes/Basicos';
import { ConKiosco } from '../../componentes/ConKiosco';
import { ErrorConReintento, Esqueleto, FinDeLista, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { equipo } from '../../i18n/es/equipo';
import { duracion, fechaCorta, hora, horaDeSalida, minutos, segundosTrabajados } from '../../util/fechas';
import { DialogoCorreccion, type JornadaCerrada } from '../historial/DialogoCorreccion';
import { DialogoAuditoria } from './DialogoAuditoria';

const E = equipo;

type Fila = components['schemas']['TeamTimeEntryDTO'];

export const CLAVE_EQUIPO = ['equipo'] as const;
const TAMANO = 50;

interface Acciones {
  alCorregir: ((j: JornadaCerrada, persona: string) => void) | null;
  alAuditar: ((fichajeId: number) => void) | null;
}

function columnas(conPersona: boolean, { alCorregir, alAuditar }: Acciones): Columna<Fila>[] {
  const todas: (Columna<Fila> | null)[] = [
    { clave: 'dia', cabecera: E.dia, celda: (f) => fechaCorta(f.fecha) },
    conPersona ? { clave: 'persona', cabecera: E.persona, celda: (f) => f.usuario?.nombre ?? '' } : null,
    {
      clave: 'entrada',
      cabecera: E.entrada,
      celda: (f) => <ConKiosco hora={hora(f.horaEntrada)} kiosco={f.kiosco} />,
    },
    {
      clave: 'salida',
      cabecera: E.salida,
      celda: (f) => (f.horaSalida === undefined ? E.enCurso : horaDeSalida(f.horaEntrada, f.horaSalida)),
    },
    {
      clave: 'pausa',
      cabecera: E.pausa,
      celda: (f) => ((f.minutosPausaAcumulados ?? 0) > 0 ? minutos(f.minutosPausaAcumulados ?? 0) : ''),
      numerica: true,
    },
    {
      clave: 'neto',
      cabecera: E.neto,
      // La abierta no tiene total todavía: una cifra que cambia cada segundo
      // no se puede comparar con las demás.
      celda: (f) =>
        f.horaSalida === undefined ? '—' : duracion(segundosTrabajados(f.horaEntrada, f.horaSalida, f.segundosPausaAcumulados ?? 0)),
      numerica: true,
    },
    alCorregir !== null || alAuditar !== null
      ? {
          clave: 'acciones',
          cabecera: E.acciones,
          celda: (f) => {
            if (f.id === undefined) return null;
            const id = f.id;
            const quien = f.usuario?.nombre ?? '';
            // Una jornada abierta se cierra fichando: el servidor rechaza corregirla.
            const cerrada =
              f.horaEntrada !== undefined && f.horaSalida !== undefined
                ? { id, horaEntrada: f.horaEntrada, horaSalida: f.horaSalida }
                : null;
            return (
              <div className="nx-acciones-fila" role="group" aria-label={E.accionesDe(quien, fechaCorta(f.fecha))}>
                {alCorregir !== null && cerrada !== null && (
                  <Boton variante="texto" onClick={() => alCorregir(cerrada, quien)}>
                    {E.corregir}
                  </Boton>
                )}
                {alAuditar !== null && (
                  <Boton variante="texto" onClick={() => alAuditar(id)}>
                    {E.verAuditoria}
                  </Boton>
                )}
              </div>
            );
          },
        }
      : null,
  ];
  return todas.filter((c): c is Columna<Fila> => c !== null);
}

function Jornadas({ personaId, nombre, acciones }: { personaId: number | null; nombre: string | null; acciones: Acciones }) {
  const lista = useListaPaginada([...CLAVE_EQUIPO, 'historial', personaId], (pagina) =>
    pedir(
      cliente.GET('/api/v1/fichaje/gestor/historial', {
        params: { query: { pagina, tamano: TAMANO, ...(personaId !== null ? { usuarioId: personaId } : {}) } },
      }),
    ),
  );

  if (lista.isPending) return <Esqueleto lineas={6} />;
  if (lista.isError && lista.elementos.length === 0) {
    return <ErrorConReintento mensaje={lista.error.message} alReintentar={() => void lista.refetch()} />;
  }
  if (lista.elementos.length === 0) {
    return nombre !== null ? (
      <Vacio titulo={E.vacioFiltroTitulo(nombre)} detalle={E.vacioFiltroTexto} />
    ) : (
      <Vacio titulo={E.vacioTitulo} detalle={E.vacioTexto} />
    );
  }

  return (
    <>
      <Tabla titulo={E.tablaTitulo} columnas={columnas(personaId === null, acciones)} filas={lista.elementos} claveDeFila={(f) => f.id ?? 0} />
      <FinDeLista
        hayMas={lista.hasNextPage}
        cargando={lista.isFetchingNextPage}
        fallo={lista.isFetchNextPageError}
        alPedirMas={() => void lista.fetchNextPage()}
      />
    </>
  );
}

export function HistorialEquipo() {
  const { puede } = useSesion();
  const [busqueda, setBusqueda] = useSearchParams();
  const [corrigiendo, setCorrigiendo] = useState<{ jornada: JornadaCerrada; persona: string } | null>(null);
  const [auditando, setAuditando] = useState<number | null>(null);

  const empleados = useQuery({
    queryKey: [...CLAVE_EQUIPO, 'empleados'],
    queryFn: () => pedir(cliente.GET('/api/v1/gestor/mis-empleados', {})),
    enabled: puede('empleado:leer'),
  });
  const personas = [...(empleados.data ?? [])].sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es'));

  const elegida = Number(busqueda.get('persona'));
  const personaId = Number.isInteger(elegida) && elegida > 0 ? elegida : null;
  const nombre = personaId !== null ? (personas.find((p) => p.id === personaId)?.nombre ?? null) : null;

  const acciones: Acciones = {
    alCorregir: puede('fichaje:corregir') ? (jornada, persona) => setCorrigiendo({ jornada, persona }) : null,
    alAuditar: puede('fichaje:auditoria') ? setAuditando : null,
  };

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{E.titulo}</h1>
      </header>
      <section className="nx-tarjeta">
        {personas.length > 0 && (
          <div className="nx-filtros">
            <Selector
              id="equipo-persona"
              etiqueta={E.filtro}
              value={personaId !== null ? String(personaId) : ''}
              onChange={(e) => setBusqueda(e.target.value === '' ? {} : { persona: e.target.value }, { replace: true })}
              opciones={[
                { valor: '', texto: E.filtroTodos },
                ...personas.map((p) => ({
                  valor: String(p.id),
                  texto: p.activo === false ? E.deBaja(p.nombre ?? '') : (p.nombre ?? ''),
                })),
              ]}
            />
          </div>
        )}
        <Jornadas personaId={personaId} nombre={nombre} acciones={acciones} />
      </section>

      <DialogoCorreccion jornada={corrigiendo?.jornada ?? null} persona={corrigiendo?.persona} alCerrar={() => setCorrigiendo(null)} />
      <DialogoAuditoria fichajeId={auditando} alCerrar={() => setAuditando(null)} />
    </div>
  );
}
