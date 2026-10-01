/**
 * El editor de cuadrantes (fase B1, ADR 022: solo en la web). Tres vistas:
 * la semana del equipo, las plantillas y el cuadrante de una persona.
 *
 * **La semana del equipo** junta, día a día, a quien tiene cuadrante vigente
 * (`/cuadrantes/equipo?fecha=`, una petición por día): quien no tiene ninguno
 * no sale, y eso es lo que se ve al mirarla. Cada día dice de dónde sale lo
 * que toca, como en «Mi cuadrante»: un festivo o una ausencia mandan sobre la
 * plantilla, y una excepción cambia solo ese día.
 *
 * La vista y la persona elegida van en la URL (`?ver=`, `?persona=`).
 */

import { useQueries } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Boton, Insignia, Selector } from '../../componentes/Basicos';
import { ErrorConReintento, Esqueleto, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { cuadrantes } from '../../i18n/es/cuadrantes';
import { fechaCorta, hoyEnEmpresa, inicialDelDia, lunesDe, sumarDias } from '../../util/fechas';
import { textoDelTramo } from '../cuadrante/MiCuadrante';
import { NavegadorDeMes } from '../proyectos/mes';
import { ordenar, usePlantilla } from '../plantilla/Plantilla';
import { CLAVE_CUADRANTES, Plantillas } from './Plantillas';
import { PorPersona } from './PorPersona';

const C = cuadrantes;
const E = cuadrantes.equipo;

type DiaTeorico = components['schemas']['TheoreticalDayResponse'];

/** Lo que toca un día, en corto, para la celda de la tabla. */
export function textoDelDia(dia: DiaTeorico | undefined): { texto: string; marca: 'libre' | 'no-laborable' | 'excepcion' | null } {
  if (dia === undefined) return { texto: '', marca: null };
  if (dia.origen === 'NO_LABORABLE') return { texto: dia.motivo ?? E.noLaborable, marca: 'no-laborable' };
  const tramos = dia.tramos ?? [];
  const texto = tramos.length === 0 ? E.libre : tramos.map(textoDelTramo).join(' · ');
  return { texto, marca: dia.origen === 'EXCEPCION' ? 'excepcion' : tramos.length === 0 ? 'libre' : null };
}

function SemanaDelEquipo({ alVerPersona }: { alVerPersona: (id: number) => void }) {
  const [busqueda, setBusqueda] = useSearchParams();
  const hoy = hoyEnEmpresa();
  const elegido = busqueda.get('semana');
  const lunes = elegido && /^\d{4}-\d{2}-\d{2}$/.test(elegido) ? lunesDe(elegido) : lunesDe(hoy);
  const dias = Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i));

  const consultas = useQueries({
    queries: dias.map((fecha) => ({
      queryKey: [...CLAVE_CUADRANTES, 'equipo', fecha],
      queryFn: () => pedir(cliente.GET('/api/v1/cuadrantes/equipo', { params: { query: { fecha } } })),
    })),
  });

  function irA(semana: string) {
    const nueva = new URLSearchParams(busqueda);
    nueva.set('semana', semana);
    setBusqueda(nueva, { replace: true });
  }

  // El navegador de meses sirve para semanas: mover ±1 es ±7 días.
  const navegador = {
    anio: 0,
    mes: 0,
    mover: (delta: number) => irA(sumarDias(lunes, delta * 7)),
    volver: () => irA(lunesDe(hoy)),
    esElActual: lunes === lunesDe(hoy),
  };

  const fallo = consultas.find((c) => c.isError);
  const cargando = consultas.some((c) => c.isPending);

  // Persona → día → lo que le toca.
  const personas = new Map<number, { nombre: string; dias: Map<string, DiaTeorico> }>();
  consultas.forEach((c, i) => {
    for (const entrada of c.data ?? []) {
      const id = entrada.usuarioId ?? 0;
      const fila = personas.get(id) ?? { nombre: entrada.nombre ?? '', dias: new Map() };
      if (entrada.dia) fila.dias.set(dias[i] as string, entrada.dia);
      personas.set(id, fila);
    }
  });
  const filas = [...personas.entries()].sort((a, b) => a[1].nombre.localeCompare(b[1].nombre, 'es'));

  return (
    <>
      <NavegadorDeMes mes={navegador} id="cuadrantes-semana" titulo={E.titulo(fechaCorta(dias[0]), fechaCorta(dias[6]))} />
      {fallo?.error ? (
        <ErrorConReintento mensaje={fallo.error.message} alReintentar={() => consultas.forEach((c) => void c.refetch())} />
      ) : cargando ? (
        <Esqueleto lineas={5} />
      ) : filas.length === 0 ? (
        <Vacio titulo={E.vacio} />
      ) : (
        <div className="nx-tabla-contenedor">
          <table className="nx-tabla nx-tabla--semana">
            <caption className="nx-solo-lector">{E.tablaTitulo}</caption>
            <thead>
              <tr>
                <th scope="col">{E.persona}</th>
                {dias.map((d) => (
                  <th key={d} scope="col" className={d === hoy ? 'nx-tabla__hoy' : undefined}>
                    <abbr title={fechaCorta(d)}>{inicialDelDia(d)}</abbr> {d.slice(8, 10)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filas.map(([id, fila]) => (
                <tr key={id}>
                  <th scope="row">
                    <Boton variante="texto" aria-label={E.ver(fila.nombre)} onClick={() => alVerPersona(id)}>
                      {fila.nombre}
                    </Boton>
                  </th>
                  {dias.map((d) => {
                    const { texto, marca } = textoDelDia(fila.dias.get(d));
                    return (
                      <td key={d} data-etiqueta={fechaCorta(d)} className={marca !== null ? `nx-celda-dia nx-celda-dia--${marca}` : 'nx-celda-dia'}>
                        {marca === 'excepcion' ? <Insignia tono="aviso">{texto}</Insignia> : texto}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

type Vista = 'semana' | 'plantillas' | 'persona';

export function Cuadrantes() {
  const [busqueda, setBusqueda] = useSearchParams();
  const { consulta: plantilla } = usePlantilla();
  const ver = (['semana', 'plantillas', 'persona'] as const).includes(busqueda.get('ver') as Vista) ? (busqueda.get('ver') as Vista) : 'semana';
  const personaId = Number(busqueda.get('persona')) || null;
  const personas = ordenar((plantilla.data ?? []).filter((p) => p.activo !== false));
  const persona = personas.find((p) => p.id === personaId);

  function cambiar(cambios: Record<string, string | null>) {
    const nueva = new URLSearchParams(busqueda);
    for (const [clave, valor] of Object.entries(cambios)) {
      if (valor === null) nueva.delete(clave);
      else nueva.set(clave, valor);
    }
    setBusqueda(nueva);
  }

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{C.titulo}</h1>
      </header>
      <section className="nx-tarjeta">
        <Pestanas
          etiqueta={C.pestanas}
          pestanas={[
            { clave: 'semana', texto: C.semana },
            { clave: 'plantillas', texto: C.plantillas },
            { clave: 'persona', texto: C.porPersona },
          ]}
          activa={ver}
          alCambiar={(v) => cambiar({ ver: v })}
        >
          {ver === 'semana' && <SemanaDelEquipo alVerPersona={(id) => cambiar({ ver: 'persona', persona: String(id) })} />}
          {ver === 'plantillas' && <Plantillas />}
          {ver === 'persona' && (
            <>
              <div className="nx-filtros">
                <Selector
                  id="cuadrantes-persona"
                  etiqueta={C.persona.elegir}
                  value={personaId !== null ? String(personaId) : ''}
                  onChange={(e) => cambiar({ persona: e.target.value === '' ? null : e.target.value })}
                  opciones={[{ valor: '', texto: C.persona.elegirTexto }, ...personas.map((p) => ({ valor: String(p.id), texto: p.nombre ?? '' }))]}
                />
              </div>
              {persona?.id !== undefined ? (
                <PorPersona key={persona.id} usuarioId={persona.id} nombre={persona.nombre ?? ''} />
              ) : (
                <p className="nx-sutil">{C.persona.sinElegir}</p>
              )}
            </>
          )}
        </Pestanas>
      </section>
    </div>
  );
}
