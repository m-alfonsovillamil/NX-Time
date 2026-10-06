/**
 * Mi perfil: mis datos personales (que puedo cambiar), los laborales (que no),
 * mi foto y mi currículum.
 *
 * **La frontera la pone el servidor, y aquí se enseña.** Nombre, apellidos,
 * fecha de nacimiento y puesto son míos (`PATCH /perfil`); rol, jornada,
 * vacaciones y departamento no se deciden sobre uno mismo, y salen en solo
 * lectura con quién los gestiona.
 *
 * Los ficheros se comprueban antes de subir solo en lo que la pantalla ya sabe
 * (vacío o de más de 5 MB). Si es de verdad un PDF o una imagen lo decide el
 * servidor mirando los primeros bytes, no la extensión: un `.exe` renombrado
 * a `.pdf` se rechaza allí, y el mensaje llega tal cual.
 */

import { useQuery } from '@tanstack/react-query';
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { useSesion } from '../../api/useSesion';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Tarjeta } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { cuenta } from '../../i18n/es/cuenta';
import { descargar } from '../../util/descargar';
import { diaEnEmpresa, fechaCompleta, fechaCorta, hoyEnEmpresa } from '../../util/fechas';
import { subirAdjunto } from './adjuntos';
import { FicharEnKiosco } from './FicharEnKiosco';

const P = cuenta.perfil;
const CINCO_MB = 5 * 1024 * 1024;

type Perfil = components['schemas']['ProfileResponse'];
type Adjunto = components['schemas']['AttachmentResponse'];

export const CLAVES_PERFIL = {
  perfil: ['perfil'] as const,
  adjuntos: ['perfil', 'adjuntos'] as const,
  foto: (id: number) => ['perfil', 'foto', id] as const,
};

/** Lo que la pantalla puede decir de un fichero sin mandarlo. */
export function problemaDelFichero(fichero: File): string | null {
  if (fichero.size === 0) return P.adjuntoVacio;
  if (fichero.size > CINCO_MB) return P.adjuntoGrande;
  return null;
}

/** El último adjunto de un tipo: el servidor guarda uno por tipo, pero por si acaso, el más reciente. */
function ultimo(adjuntos: readonly Adjunto[], tipo: 'CV' | 'FOTO'): Adjunto | undefined {
  return [...adjuntos].filter((a) => a.tipo === tipo).sort((a, b) => (b.subidoEn ?? '').localeCompare(a.subidoEn ?? ''))[0];
}

function useSubirAdjunto(tipo: 'CV' | 'FOTO', exito: string) {
  return useMutacion((fichero: File) => subirAdjunto(tipo, fichero), { invalida: [CLAVES_PERFIL.adjuntos], exito });
}

/** Un `<input type="file">` escondido detrás de un botón que se ve como los demás. */
function ElegirFichero({
  id,
  texto,
  acepta,
  ocupado,
  alElegir,
}: {
  id: string;
  texto: string;
  acepta: string;
  ocupado: boolean;
  alElegir: (fichero: File) => void;
}) {
  function cambio(evento: ChangeEvent<HTMLInputElement>) {
    const fichero = evento.target.files?.[0];
    // Vaciarlo permite volver a elegir el mismo fichero tras un error.
    evento.target.value = '';
    if (fichero !== undefined) alElegir(fichero);
  }
  return (
    <span className="nx-elegir-fichero">
      <input id={id} type="file" accept={acepta} className="nx-solo-lector" onChange={cambio} disabled={ocupado} />
      <label htmlFor={id} className="nx-boton nx-boton--secundario" aria-busy={ocupado}>
        {texto}
      </label>
    </span>
  );
}

function Foto({ perfil, foto }: { perfil: Perfil; foto: Adjunto | undefined }) {
  const imagen = useQuery({
    queryKey: CLAVES_PERFIL.foto(foto?.id ?? 0),
    queryFn: async () => {
      const r = await cliente.GET('/api/v1/perfil/adjuntos/{id}', {
        params: { path: { id: foto?.id ?? 0 } },
        parseAs: 'blob',
      });
      if (!r.response.ok || r.data === undefined) throw new Error(T.errores.inesperado);
      return r.data as Blob;
    },
    enabled: foto?.id !== undefined,
    staleTime: Infinity,
  });
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (imagen.data === undefined) return;
    const nueva = URL.createObjectURL(imagen.data);
    setUrl(nueva);
    return () => URL.revokeObjectURL(nueva);
  }, [imagen.data]);

  const subir = useSubirAdjunto('FOTO', P.foto.subida);
  const [error, setError] = useState<string | null>(null);

  function elegir(fichero: File) {
    const problema = problemaDelFichero(fichero);
    setError(problema);
    if (problema === null) subir.mutate(fichero);
  }

  return (
    <div className="nx-perfil__cabecera">
      {url !== null ? (
        <img className="nx-avatar" src={url} alt={P.foto.alternativa(perfil.nombreCompleto ?? '')} />
      ) : (
        <span className="nx-avatar nx-avatar--iniciales" aria-hidden="true">
          {perfil.iniciales}
        </span>
      )}
      <div className="nx-columna">
        <div>
          <h2>{perfil.nombreCompleto}</h2>
          <p className="nx-sutil">{perfil.email}</p>
        </div>
        <ElegirFichero id="foto" texto={P.foto.cambiar} acepta="image/jpeg,image/png" ocupado={subir.isPending} alElegir={elegir} />
        <p className="nx-sutil">{P.foto.ayuda}</p>
        {(error ?? subir.error?.message) && <Aviso>{error ?? subir.error?.message}</Aviso>}
      </div>
    </div>
  );
}

function Personales({ perfil }: { perfil: Perfil }) {
  const [editando, setEditando] = useState(false);
  const [nombre, setNombre] = useState(perfil.nombre ?? '');
  const [apellidos, setApellidos] = useState(perfil.apellidos ?? '');
  const [fecha, setFecha] = useState(perfil.fechaNacimiento ?? '');
  const [puesto, setPuesto] = useState(perfil.puesto ?? '');
  const [error, setError] = useState<string | null>(null);

  const guardar = useMutacion(
    (cuerpo: { nombre: string; apellidos: string; fechaNacimiento?: string; puesto: string }) =>
      pedir(cliente.PATCH('/api/v1/perfil', { body: cuerpo })),
    { invalida: [CLAVES_PERFIL.perfil], exito: P.guardado, alTerminar: () => setEditando(false) },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (nombre.trim() === '') return setError(P.nombreObligatorio);
    if (fecha !== '' && fecha >= hoyEnEmpresa()) return setError(P.fechaFutura);
    setError(null);
    // Es un PATCH: una cadena vacía borra el dato. La fecha vacía no se manda,
    // porque «» no es una fecha y el servidor la rechazaría.
    guardar.mutate({
      nombre: nombre.trim(),
      apellidos: apellidos.trim(),
      puesto: puesto.trim(),
      ...(fecha !== '' ? { fechaNacimiento: fecha } : {}),
    });
  }

  if (!editando) {
    return (
      <Tarjeta
        titulo={P.personales}
        acciones={
          <Boton variante="texto" onClick={() => setEditando(true)}>
            {P.editar}
          </Boton>
        }
      >
        <dl className="nx-datos">
          <dt>{P.nombre}</dt>
          <dd>{perfil.nombre || P.sinDato}</dd>
          <dt>{P.apellidos}</dt>
          <dd>{perfil.apellidos || P.sinDato}</dd>
          <dt>{P.fechaNacimiento}</dt>
          <dd>{perfil.fechaNacimiento ? fechaCompleta(perfil.fechaNacimiento) : P.sinDato}</dd>
          <dt>{P.puesto}</dt>
          <dd>{perfil.puesto || P.sinDato}</dd>
        </dl>
      </Tarjeta>
    );
  }

  return (
    <Tarjeta titulo={P.personales}>
      <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
        <div className="nx-fila-campos">
          <Campo id="perfil-nombre" etiqueta={P.nombre} value={nombre} maxLength={100} autoComplete="given-name" onChange={(e) => setNombre(e.target.value)} />
          <Campo id="perfil-apellidos" etiqueta={P.apellidos} value={apellidos} maxLength={150} autoComplete="family-name" onChange={(e) => setApellidos(e.target.value)} />
        </div>
        <div className="nx-fila-campos">
          <Campo id="perfil-fecha" etiqueta={P.fechaNacimiento} type="date" value={fecha} max={diaEnEmpresa()} onChange={(e) => setFecha(e.target.value)} />
          <Campo id="perfil-puesto" etiqueta={P.puesto} value={puesto} maxLength={120} onChange={(e) => setPuesto(e.target.value)} />
        </div>
        {(error ?? guardar.error?.message) && <Aviso>{error ?? guardar.error?.message}</Aviso>}
        <div className="nx-dialogo__acciones">
          <Boton variante="texto" onClick={() => setEditando(false)}>
            {T.app.cancelar}
          </Boton>
          <Boton type="submit" ocupado={guardar.isPending}>
            {P.guardar}
          </Boton>
        </div>
      </form>
    </Tarjeta>
  );
}

function Laborales({ perfil }: { perfil: Perfil }) {
  return (
    <Tarjeta titulo={P.laborales}>
      <dl className="nx-datos">
        <dt>{P.rol}</dt>
        <dd>{P.roles[perfil.rol ?? ''] ?? perfil.rol ?? P.sinDato}</dd>
        <dt>{P.departamento}</dt>
        <dd>{perfil.departamentoNombre || P.sinDato}</dd>
        <dt>{P.jornada}</dt>
        <dd>{perfil.horasSemanales !== undefined ? P.jornadaValor(perfil.horasSemanales.toLocaleString('es-ES')) : P.sinDato}</dd>
        <dt>{P.vacaciones}</dt>
        <dd>{perfil.diasVacaciones !== undefined ? P.vacacionesValor(perfil.diasVacaciones) : P.sinDato}</dd>
      </dl>
      <p className="nx-sutil">{P.soloRrhh}</p>
    </Tarjeta>
  );
}

function Curriculum({ cv }: { cv: Adjunto | undefined }) {
  const subir = useSubirAdjunto('CV', P.cv.subido);
  const borrar = useMutacion(
    (id: number) => pedir(cliente.DELETE('/api/v1/perfil/adjuntos/{id}', { params: { path: { id } } })),
    { invalida: [CLAVES_PERFIL.adjuntos], exito: P.cv.borrado },
  );
  const [error, setError] = useState<string | null>(null);

  function elegir(fichero: File) {
    const problema = problemaDelFichero(fichero);
    setError(problema);
    if (problema === null) subir.mutate(fichero);
  }

  async function abrir(adjunto: Adjunto) {
    setError(null);
    try {
      await descargar(
        cliente.GET('/api/v1/perfil/adjuntos/{id}', { params: { path: { id: adjunto.id ?? 0 } }, parseAs: 'blob' }),
        adjunto.nombreOriginal ?? 'curriculum.pdf',
      );
    } catch (fallo) {
      setError(fallo instanceof Error ? fallo.message : T.errores.descarga);
    }
  }

  const mensaje = error ?? subir.error?.message ?? borrar.error?.message ?? null;

  return (
    <Tarjeta titulo={P.cv.titulo}>
      {cv === undefined ? (
        <p className="nx-sutil">{P.cv.vacio}</p>
      ) : (
        <div className="nx-fila-accion">
          <span>{P.cv.detalle(cv.nombreOriginal ?? '', cv.subidoEn ? fechaCorta(diaEnEmpresa(cv.subidoEn)) : '')}</span>
          <span className="nx-acciones-fila">
            <Boton variante="texto" onClick={() => void abrir(cv)}>
              {P.cv.abrir}
            </Boton>
            <Boton variante="texto" ocupado={borrar.isPending} onClick={() => cv.id !== undefined && borrar.mutate(cv.id)}>
              {P.cv.borrar}
            </Boton>
          </span>
        </div>
      )}
      <ElegirFichero
        id="cv"
        texto={cv === undefined ? P.cv.subir : P.cv.reemplazar}
        acepta="application/pdf"
        ocupado={subir.isPending}
        alElegir={elegir}
      />
      <p className="nx-sutil">{P.cv.ayuda}</p>
      <p className="nx-sutil">{P.cv.usoEnOfertas}</p>
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
    </Tarjeta>
  );
}

export function PaginaPerfil() {
  const { puede } = useSesion();
  const perfil = useQuery({ queryKey: CLAVES_PERFIL.perfil, queryFn: () => pedir(cliente.GET('/api/v1/perfil', {})) });
  const adjuntos = useQuery({
    queryKey: CLAVES_PERFIL.adjuntos,
    queryFn: () => pedir(cliente.GET('/api/v1/perfil/adjuntos', {})),
  });
  const lista = adjuntos.data ?? [];

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={P.titulo} />
      <EstadoDeConsulta consulta={perfil} cargando={<Esqueleto lineas={6} />}>
        {(p) => (
          <>
            <section className="nx-tarjeta">
              <Foto perfil={p} foto={ultimo(lista, 'FOTO')} />
            </section>
            <div className="nx-rejilla-dos">
              <Personales perfil={p} />
              <Laborales perfil={p} />
            </div>
            <Curriculum cv={ultimo(lista, 'CV')} />
            {puede('fichaje:escribir') && <FicharEnKiosco />}
          </>
        )}
      </EstadoDeConsulta>
    </div>
  );
}
