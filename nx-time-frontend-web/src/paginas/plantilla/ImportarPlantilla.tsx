/**
 * Importar la plantilla desde un fichero, las invitaciones que esperan y el
 * cambio de rol (ADR 052).
 *
 * Importar va en dos pasos, a propósito: al elegir el fichero se **comprueba**
 * (el servidor lo lee y dice qué pasa con cada fila, sin crear nada) y solo si
 * todo está bien se ofrece crear. Con una fila mala no se crea a nadie, para no
 * dejar media plantilla dentro.
 */

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ChangeEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Insignia, Selector } from '../../componentes/Basicos';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { T } from '../../i18n/es';
import { plantilla } from '../../i18n/es/plantilla';
import { formularioDeSubida } from '../cuenta/adjuntos';
import { CLAVES_PLANTILLA } from './claves';
import type { Persona } from './Plantilla';

const P = plantilla;
const I = plantilla.importar;

type Resultado = components['schemas']['ImportacionPlantillaResponse'];
type Fila = components['schemas']['FilaDeImportacion'];
type Rol = NonNullable<Persona['rol']>;

export const CLAVE_INVITACIONES = ['plantilla', 'invitaciones'] as const;

function importar(fichero: File, simular: boolean) {
  return pedir(
    cliente.POST('/api/v1/gestor/plantilla/importar', {
      params: { query: { simular } },
      // Como el CV: el contrato lo describe como texto, lo que viaja es el fichero.
      body: { fichero: fichero.name },
      bodySerializer: () => formularioDeSubida(fichero),
    }),
  );
}

function descargarEjemplo() {
  // Con BOM: así Excel abre las tildes bien al hacer doble clic.
  const url = URL.createObjectURL(new Blob(['﻿' + I.ficheroEjemplo], { type: 'text/csv;charset=utf-8' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = 'plantilla-ejemplo.csv';
  enlace.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

export function ImportarPlantilla({ alTerminar }: { alTerminar: () => void }) {
  const consultas = useQueryClient();
  const [fichero, setFichero] = useState<File | null>(null);
  const [comprobado, setComprobado] = useState<Resultado | null>(null);
  const [hecho, setHecho] = useState<Resultado | null>(null);

  const comprobar = useMutacion((elegido: File) => importar(elegido, true), {
    alTerminar: (resultado) => setComprobado(resultado),
  });
  const crear = useMutacion((elegido: File) => importar(elegido, false), {
    invalida: [CLAVES_PLANTILLA.todo],
    alTerminar: (resultado) => {
      setHecho(resultado);
      void consultas.invalidateQueries({ queryKey: CLAVE_INVITACIONES });
    },
  });

  function elegir(evento: ChangeEvent<HTMLInputElement>) {
    const elegido = evento.target.files?.[0] ?? null;
    setFichero(elegido);
    setComprobado(null);
    crear.reset();
    if (elegido !== null) comprobar.mutate(elegido);
  }

  if (hecho !== null) {
    const inv = hecho.invitaciones;
    return (
      <div className="nx-formulario-dialogo">
        <p role="status">
          <strong>{I.hecho(hecho.creadas ?? 0)}</strong>
        </p>
        {inv !== undefined && <p>{I.invitaciones(inv.enCamino ?? 0, inv.pendientes ?? 0)}</p>}
        <div className="nx-dialogo__acciones">
          <Boton onClick={alTerminar}>{T.app.cerrar}</Boton>
        </div>
      </div>
    );
  }

  const columnas: Columna<Fila>[] = [
    { clave: 'fila', cabecera: I.fila, celda: (f) => f.fila, numerica: true },
    { clave: 'nombre', cabecera: P.nombre, celda: (f) => `${f.nombre ?? ''} ${f.apellidos ?? ''}`.trim() },
    { clave: 'email', cabecera: P.email, celda: (f) => f.email },
    { clave: 'rol', cabecera: P.rol, celda: (f) => P.roles[f.rol ?? ''] ?? f.rol },
    {
      clave: 'errores',
      cabecera: I.errores,
      celda: (f) =>
        (f.errores ?? []).length === 0 ? (
          <Insignia tono="exito">{I.bien}</Insignia>
        ) : (
          <ul className="nx-lista-simple">
            {(f.errores ?? []).map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        ),
    },
  ];

  const sinErrores = comprobado !== null && (comprobado.conErrores ?? 0) === 0;
  const error = comprobar.error ?? crear.error;

  return (
    <div className="nx-formulario-dialogo">
      <p>{I.explicacion}</p>
      <div>
        <Boton variante="texto" icono="descargar" onClick={descargarEjemplo}>
          {I.ejemplo}
        </Boton>
      </div>
      <Campo
        id="importar-fichero"
        etiqueta={I.fichero}
        type="file"
        accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        onChange={elegir}
      />
      {comprobar.isPending && <p role="status">{I.comprobando}</p>}
      {error !== null && <Aviso>{error.message}</Aviso>}
      {comprobado !== null && (
        <>
          <p role="status">{I.resumen(comprobado.filas ?? 0, comprobado.conErrores ?? 0)}</p>
          {(comprobado.columnasIgnoradas ?? []).length > 0 && (
            <p className="nx-sutil">{I.ignoradas((comprobado.columnasIgnoradas ?? []).join(', '))}</p>
          )}
          <Tabla titulo={I.tabla} columnas={columnas} filas={comprobado.detalle ?? []} claveDeFila={(f) => f.fila ?? 0} />
        </>
      )}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton
          disabled={!sinErrores || fichero === null}
          ocupado={crear.isPending}
          onClick={() => fichero !== null && crear.mutate(fichero)}
        >
          {crear.isPending ? I.creando : I.crear(comprobado?.filas ?? 0)}
        </Boton>
      </div>
    </div>
  );
}

/** El aviso de invitaciones que esperan cupo, encima de la tabla. Nada si no hay. */
export function AvisoDeInvitaciones() {
  const V = P.invitaciones;
  const estado = useQuery({
    queryKey: CLAVE_INVITACIONES,
    queryFn: () => pedir(cliente.GET('/api/v1/gestor/plantilla/invitaciones', {})),
  });
  const [enCamino, setEnCamino] = useState(0);
  const enviar = useMutacion(() => pedir(cliente.POST('/api/v1/gestor/plantilla/invitaciones/enviar', {})), {
    invalida: [CLAVE_INVITACIONES, CLAVES_PLANTILLA.todo],
    alTerminar: (r) => setEnCamino(r.enCamino ?? 0),
  });

  const pendientes = estado.data?.pendientes ?? 0;
  const cupo = estado.data?.cupoHoy ?? 0;
  if (pendientes === 0) return null;
  return (
    <div className="nx-aviso-condiciones" role="region" aria-label={V.enviar}>
      <p>{enCamino > 0 ? V.enviando(enCamino) : V.aviso(pendientes, cupo)}</p>
      {cupo > 0 && enCamino === 0 && (
        <div className="nx-aviso-condiciones__acciones">
          <Boton ocupado={enviar.isPending} onClick={() => enviar.mutate(undefined)}>
            {V.enviar}
          </Boton>
        </div>
      )}
      {enviar.error !== null && <Aviso>{enviar.error.message}</Aviso>}
    </div>
  );
}

const ROLES: readonly Rol[] = ['EMPLEADO', 'GESTOR', 'RRHH', 'ADMIN'];

export function CambiarRol({ persona, alTerminar }: { persona: Persona; alTerminar: () => void }) {
  const R = P.cambioDeRol;
  const [rol, setRol] = useState<Rol>(persona.rol ?? 'EMPLEADO');
  const guardar = useMutacion(
    (nuevo: Rol) =>
      pedir(cliente.PATCH('/api/v1/gestor/empleados/{id}/rol', { params: { path: { id: persona.id ?? 0 } }, body: { rol: nuevo } })),
    {
      invalida: [CLAVES_PLANTILLA.todo],
      exito: (_r, nuevo) => R.hecho(persona.nombre ?? '', P.roles[nuevo] ?? nuevo),
      alTerminar,
    },
  );
  return (
    <div className="nx-formulario-dialogo">
      <p>{R.explicacion}</p>
      <Selector
        id="cambiar-rol"
        etiqueta={R.etiqueta}
        value={rol}
        onChange={(e) => setRol(e.target.value as Rol)}
        opciones={ROLES.map((r) => ({ valor: r, texto: P.roles[r] ?? r }))}
      />
      {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton ocupado={guardar.isPending} disabled={rol === persona.rol} onClick={() => guardar.mutate(rol)}>
          {R.guardar}
        </Boton>
      </div>
    </div>
  );
}
