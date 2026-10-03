/**
 * La plantilla: quién trabaja en la empresa, su jornada, sus vacaciones y su
 * departamento; y dar de alta, de baja o volver a dar de alta.
 *
 * **Qué lista se ve depende de quién mira.** Con `empleado:gestionar` (RRHH y
 * ADMIN) es la plantilla entera, de cualquier rol (`/gestor/plantilla`, fase
 * W6): hace falta para poner departamento a los gestores, que la app no podía
 * porque solo listaba empleados. Un GESTOR ve a los EMPLEADO, como en la app.
 *
 * **Dar de baja no borra nada** (requisito legal): la persona deja de poder
 * entrar y sus fichajes se conservan. Por eso se confirma, y se puede
 * deshacer. Quien está de baja no sale por defecto, pero se puede incluir.
 *
 * **Al dar de alta no se pone contraseña**: le llega un correo con un código
 * para que la elija ella (ADR 014). Si el correo no sale, el servidor lo dice
 * con su mensaje y no se crea a medias.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { useSesion } from '../../api/useSesion';
import { Aviso, Boton, Campo, Insignia, Selector } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Tabla, type Columna } from '../../componentes/Tabla';
import { T } from '../../i18n/es';
import { kiosco } from '../../i18n/es/kiosco';
import { plantilla } from '../../i18n/es/plantilla';
import { CLAVES_PLANTILLA } from './claves';

const P = plantilla;

export type Persona = components['schemas']['SimpleEmployeeDTO'];

/** De alta primero, luego por nombre: lo que se busca casi siempre es a alguien en activo. */
export function ordenar(personas: readonly Persona[]): Persona[] {
  return [...personas].sort(
    (a, b) => Number(b.activo === true) - Number(a.activo === true) || (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es'),
  );
}

export function filtrar(personas: readonly Persona[], texto: string, conBajas: boolean): Persona[] {
  const buscado = texto.trim().toLocaleLowerCase('es');
  return personas.filter(
    (p) =>
      (conBajas || p.activo !== false) &&
      (buscado === '' || `${p.nombre ?? ''} ${p.email ?? ''}`.toLocaleLowerCase('es').includes(buscado)),
  );
}

/** La lista que toca según quién mira: la plantilla entera o sus empleados. */
export function usePlantilla() {
  const { puede } = useSesion();
  const entera = puede('empleado:gestionar');
  return {
    entera,
    consulta: useQuery({
      queryKey: [...CLAVES_PLANTILLA.personas, entera],
      queryFn: () =>
        entera
          ? pedir(cliente.GET('/api/v1/gestor/plantilla', {}))
          : pedir(cliente.GET('/api/v1/gestor/mis-empleados', {})),
    }),
  };
}

/* ------------------------------------------------------------------ */
/* Alta                                                                */
/* ------------------------------------------------------------------ */

type Alta = 'empleado' | 'gestor';

function FormularioDeAlta({ tipo, alTerminar }: { tipo: Alta; alTerminar: () => void }) {
  const [nombre, setNombre] = useState('');
  const [apellidos, setApellidos] = useState('');
  const [email, setEmail] = useState('');
  const [faltan, setFaltan] = useState(false);

  const crear = useMutacion(
    (cuerpo: { nombre: string; apellidos: string; email: string }) =>
      tipo === 'gestor'
        ? pedir(cliente.POST('/api/v1/gestor/gestores', { body: cuerpo }))
        : pedir(cliente.POST('/api/v1/gestor/empleados', { body: cuerpo })),
    { invalida: [CLAVES_PLANTILLA.todo], exito: (_r, v) => P.creado(v.nombre), alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const cuerpo = { nombre: nombre.trim(), apellidos: apellidos.trim(), email: email.trim() };
    if (cuerpo.nombre === '' || cuerpo.apellidos === '' || cuerpo.email === '') return setFaltan(true);
    setFaltan(false);
    crear.mutate(cuerpo);
  }

  const mensaje = faltan ? P.altaFaltan : (crear.error?.message ?? null);
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p className="nx-sutil">{P.altaExplicacion}</p>
      <div className="nx-fila-campos">
        <Campo id="alta-nombre" etiqueta={P.altaNombre} maxLength={100} autoComplete="off" value={nombre} onChange={(e) => setNombre(e.target.value)} />
        <Campo id="alta-apellidos" etiqueta={P.altaApellidos} maxLength={150} autoComplete="off" value={apellidos} onChange={(e) => setApellidos(e.target.value)} />
      </div>
      <Campo id="alta-email" etiqueta={P.altaEmail} type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} />
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={crear.isPending}>
          {P.crear}
        </Boton>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------------ */
/* Ficha                                                               */
/* ------------------------------------------------------------------ */

/** Horas con coma o punto, más de 0, hasta 60 y con un decimal como mucho: lo que acepta el servidor. */
export function horasValidas(texto: string): number | null {
  const normal = texto.trim().replace(',', '.');
  if (!/^\d{1,2}(\.\d)?$/.test(normal)) return null;
  const horas = Number(normal);
  return horas > 0 && horas <= 60 ? horas : null;
}

function FormularioDeFicha({ persona, alTerminar }: { persona: Persona; alTerminar: () => void }) {
  const { puede } = useSesion();
  const conDepartamento = puede('departamento:gestionar');
  const [horas, setHoras] = useState(String(persona.horasSemanales ?? 40).replace('.', ','));
  const [dias, setDias] = useState(String(persona.diasVacaciones ?? 22));
  const [departamento, setDepartamento] = useState(persona.departamentoId !== undefined ? String(persona.departamentoId) : '');
  const [error, setError] = useState<string | null>(null);

  const departamentos = useQuery({
    queryKey: CLAVES_PLANTILLA.departamentos,
    queryFn: () => pedir(cliente.GET('/api/v1/departamentos', {})),
    enabled: conDepartamento,
  });

  const guardar = useMutacion(
    async ({ horasSemanales, diasVacaciones, departamentoId }: { horasSemanales: number; diasVacaciones: number; departamentoId: number | null }) => {
      const id = persona.id ?? 0;
      await pedir(cliente.PATCH('/api/v1/gestor/empleados/{id}/ficha', { params: { path: { id } }, body: { horasSemanales, diasVacaciones } }));
      const antes = persona.departamentoId ?? null;
      if (conDepartamento && departamentoId !== antes) {
        // Sin departamentoId (el campo ausente llega como null) se le saca del que tuviera.
        await pedir(
          cliente.PATCH('/api/v1/departamentos/empleados/{usuarioId}', {
            params: { path: { usuarioId: id } },
            body: departamentoId !== null ? { departamentoId } : {},
          }),
        );
      }
    },
    { invalida: [CLAVES_PLANTILLA.todo], exito: P.fichaGuardada, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const horasSemanales = horasValidas(horas);
    if (horasSemanales === null) return setError(P.fichaHorasMal);
    const diasVacaciones = Number(dias);
    if (!/^\d{1,3}$/.test(dias.trim()) || diasVacaciones > 365) return setError(P.fichaDiasMal);
    setError(null);
    guardar.mutate({ horasSemanales, diasVacaciones, departamentoId: departamento === '' ? null : Number(departamento) });
  }

  const mensaje = error ?? guardar.error?.message ?? null;
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <div className="nx-fila-campos">
        <Campo id="ficha-horas" etiqueta={P.fichaHoras} inputMode="decimal" value={horas} onChange={(e) => setHoras(e.target.value)} />
        <Campo id="ficha-dias" etiqueta={P.fichaDias} ayuda={P.fichaAyuda} inputMode="numeric" value={dias} onChange={(e) => setDias(e.target.value)} />
      </div>
      {conDepartamento && (
        <Selector
          id="ficha-departamento"
          etiqueta={P.departamento}
          value={departamento}
          onChange={(e) => setDepartamento(e.target.value)}
          opciones={[
            { valor: '', texto: P.sinDepartamento },
            ...(departamentos.data ?? []).map((d) => ({ valor: String(d.id), texto: d.nombre ?? '' })),
          ]}
        />
      )}
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={guardar.isPending}>
          {P.guardar}
        </Boton>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------------ */
/* La página                                                           */
/* ------------------------------------------------------------------ */

export function Plantilla() {
  const { puede } = useSesion();
  const { entera, consulta } = usePlantilla();
  const [texto, setTexto] = useState('');
  const [conBajas, setConBajas] = useState(false);
  const [alta, setAlta] = useState<Alta | null>(null);
  const [ficha, setFicha] = useState<Persona | null>(null);
  const [baja, setBaja] = useState<Persona | null>(null);

  const cambiarEstado = useMutacion(
    ({ persona, activo }: { persona: Persona; activo: boolean }) =>
      pedir(cliente.PATCH('/api/v1/gestor/empleados/{id}/estado', { params: { path: { id: persona.id ?? 0 } }, body: { activo } })),
    {
      invalida: [CLAVES_PLANTILLA.todo],
      exito: (_r, v) => (v.activo ? P.altaHecha(v.persona.nombre ?? '') : P.bajaHecha(v.persona.nombre ?? '')),
      alTerminar: () => setBaja(null),
    },
  );

  const puedeConfigurar = puede('empleado:configurar');
  const puedeGestionar = puede('empleado:gestionar');

  const columnas: Columna<Persona>[] = [
    { clave: 'nombre', cabecera: P.nombre, celda: (p) => <strong>{p.nombre}</strong> },
    { clave: 'email', cabecera: P.email, celda: (p) => p.email },
    ...(entera ? [{ clave: 'rol', cabecera: P.rol, celda: (p: Persona) => P.roles[p.rol ?? ''] ?? p.rol }] : []),
    { clave: 'departamento', cabecera: P.departamento, celda: (p) => p.departamentoNombre ?? <span className="nx-sutil">{P.sinDepartamento}</span> },
    { clave: 'jornada', cabecera: P.jornada, celda: (p) => P.horasSemana(String(p.horasSemanales ?? '').replace('.', ',')), numerica: true },
    { clave: 'vacaciones', cabecera: P.vacaciones, celda: (p) => P.dias(p.diasVacaciones ?? 0), numerica: true },
    {
      clave: 'estado',
      cabecera: P.estado,
      celda: (p) => (p.activo === false ? <Insignia tono="neutro">{P.deBaja}</Insignia> : <Insignia tono="exito">{P.deAlta}</Insignia>),
    },
    ...(puedeConfigurar || puedeGestionar
      ? [
          {
            clave: 'acciones',
            cabecera: P.acciones,
            celda: (p: Persona) => (
              <div className="nx-acciones-fila" role="group" aria-label={P.accionesDe(p.nombre ?? '')}>
                {puedeConfigurar && (
                  <Boton variante="texto" onClick={() => setFicha(p)}>
                    {P.editarFicha}
                  </Boton>
                )}
                {puedeGestionar &&
                  (p.activo === false ? (
                    <Boton
                      variante="texto"
                      ocupado={cambiarEstado.isPending && cambiarEstado.variables?.persona.id === p.id}
                      onClick={() => cambiarEstado.mutate({ persona: p, activo: true })}
                    >
                      {P.reactivar}
                    </Boton>
                  ) : (
                    <Boton variante="texto" onClick={() => setBaja(p)}>
                      {P.darDeBaja}
                    </Boton>
                  ))}
              </div>
            ),
          },
        ]
      : []),
  ];

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera nx-cabecera--con-acciones">
        <h1>{P.titulo}</h1>
        <div className="nx-acciones-fila">
          {puede('empleado:crear') && <Boton onClick={() => setAlta('empleado')}>{P.nuevoEmpleado}</Boton>}
          {puede('gestor:crear') && (
            <Boton variante="secundario" onClick={() => setAlta('gestor')}>
              {P.nuevoGestor}
            </Boton>
          )}
          {puede('empresa:configurar') && (
            <Link className="nx-boton nx-boton--texto nx-boton--enlace" to="/tarjetas-kiosco">
              {kiosco.tarjetas.titulo}
            </Link>
          )}
        </div>
      </header>

      <section className="nx-tarjeta">
        <div className="nx-filtros">
          <Campo id="plantilla-buscar" etiqueta={P.buscar} type="search" value={texto} onChange={(e) => setTexto(e.target.value)} />
          <label className="nx-casilla">
            <input type="checkbox" checked={conBajas} onChange={(e) => setConBajas(e.target.checked)} />
            {P.verDeBaja}
          </label>
        </div>
        {cambiarEstado.error !== null && baja === null && <Aviso>{cambiarEstado.error.message}</Aviso>}
        <EstadoDeConsulta consulta={consulta} cargando={<Esqueleto lineas={6} />}>
          {(personas) => {
            if (personas.length === 0) return <Vacio titulo={P.vacio} />;
            const vistas = ordenar(filtrar(personas, texto, conBajas));
            if (vistas.length === 0) return <Vacio titulo={P.sinResultados} />;
            return <Tabla titulo={P.tablaTitulo} columnas={columnas} filas={vistas} claveDeFila={(p) => p.id ?? 0} />;
          }}
        </EstadoDeConsulta>
      </section>

      <Dialogo abierto={alta !== null} titulo={alta === 'gestor' ? P.nuevoGestor : P.nuevoEmpleado} alCerrar={() => setAlta(null)} acciones={null}>
        {alta !== null && <FormularioDeAlta tipo={alta} alTerminar={() => setAlta(null)} />}
      </Dialogo>

      <Dialogo abierto={ficha !== null} titulo={P.fichaTitulo(ficha?.nombre ?? '')} alCerrar={() => setFicha(null)} acciones={null}>
        {ficha !== null && <FormularioDeFicha persona={ficha} alTerminar={() => setFicha(null)} />}
      </Dialogo>

      <Dialogo
        abierto={baja !== null}
        titulo={P.bajaTitulo(baja?.nombre ?? '')}
        alCerrar={() => {
          cambiarEstado.reset();
          setBaja(null);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setBaja(null)}>
              {T.app.cancelar}
            </Boton>
            <Boton variante="peligro" ocupado={cambiarEstado.isPending} onClick={() => baja !== null && cambiarEstado.mutate({ persona: baja, activo: false })}>
              {P.darDeBaja}
            </Boton>
          </>
        }
      >
        <p>{P.bajaTexto}</p>
        {cambiarEstado.error !== null && <Aviso>{cambiarEstado.error.message}</Aviso>}
      </Dialogo>
    </div>
  );
}
