/**
 * Los borrados de datos (RGPD, ADR 016): las solicitudes pendientes, las más
 * antiguas primero (hay un mes para responder), y ejecutar, rechazar o
 * registrar una que llegó fuera de la app. Es la `BorradosScreen` de la app.
 *
 * **Ejecutar es irreversible**: además del diálogo que explica qué se borra y
 * qué se conserva, hay que escribir el nombre de la persona para que el botón
 * funcione. Un clic de más aquí no tiene arreglo. Si algo lo impide (una
 * jornada abierta, correcciones o ausencias pendientes, ser el único ADMIN),
 * el servidor lo dice en `bloqueos` y el botón ni siquiera se ofrece.
 *
 * Rechazar pide comentario: le llega a la persona para que sepa qué resolver.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Selector } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { borrados } from '../../i18n/es/borrados';
import { diaEnEmpresa, fechaCorta } from '../../util/fechas';

const B = borrados;

type Solicitud = components['schemas']['DeletionResponse'];

const CLAVE = ['borrados'] as const;
// Ejecutar o rechazar cambia la plantilla (la cuenta se desactiva) y los contadores del panel.
const TRAS_RESOLVER = [CLAVE, ['plantilla'], ['dashboard']] as const;

function FormularioDeRegistro({ alTerminar }: { alTerminar: () => void }) {
  const candidatos = useQuery({ queryKey: [...CLAVE, 'candidatos'], queryFn: () => pedir(cliente.GET('/api/v1/borrados/candidatos', {})) });
  const [persona, setPersona] = useState('');
  const [motivo, setMotivo] = useState('');
  const [faltan, setFaltan] = useState(false);

  const registrar = useMutacion(
    (cuerpo: { usuarioId: number; motivo: string }) => pedir(cliente.POST('/api/v1/borrados', { body: cuerpo })),
    { invalida: [CLAVE], exito: B.registrada, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (persona === '' || motivo.trim() === '') return setFaltan(true);
    setFaltan(false);
    registrar.mutate({ usuarioId: Number(persona), motivo: motivo.trim() });
  }

  if (candidatos.isPending) return <Esqueleto lineas={2} />;
  const lista = [...(candidatos.data ?? [])].sort((a, b) => (a.nombre ?? '').localeCompare(b.nombre ?? '', 'es'));
  const mensaje = faltan ? B.faltan : (registrar.error?.message ?? candidatos.error?.message ?? null);
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p className="nx-sutil">{B.registrarAyuda}</p>
      {lista.length === 0 ? (
        <p>{B.nadie}</p>
      ) : (
        <>
          <Selector
            id="borrado-persona"
            etiqueta={B.persona}
            value={persona}
            onChange={(e) => setPersona(e.target.value)}
            opciones={[
              { valor: '', texto: B.elegir },
              ...lista.map((c) => ({ valor: String(c.id), texto: c.activo === false ? B.deBaja(c.nombre ?? '') : (c.nombre ?? '') })),
            ]}
          />
          <Campo id="borrado-motivo" etiqueta={B.comoLlego} maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </>
      )}
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        {lista.length > 0 && (
          <Boton type="submit" ocupado={registrar.isPending}>
            {B.registrarConfirmar}
          </Boton>
        )}
      </div>
    </form>
  );
}

function ConfirmarEjecucion({ solicitud, alTerminar }: { solicitud: Solicitud; alTerminar: () => void }) {
  const nombre = solicitud.nombre ?? '';
  const [escrito, setEscrito] = useState('');
  const ejecutar = useMutacion(
    (id: number) => pedir(cliente.POST('/api/v1/borrados/{id}/ejecutar', { params: { path: { id } } })),
    { invalida: TRAS_RESOLVER, exito: B.ejecutado, alTerminar },
  );
  // Sin distinguir mayúsculas ni espacios de más: se trata de que lo lea, no de que lo teclee perfecto.
  const coincide = escrito.trim().toLocaleLowerCase('es') === nombre.trim().toLocaleLowerCase('es');

  return (
    <div className="nx-formulario-dialogo">
      {B.ejecutarDetalle.map((p) => (
        <p key={p}>{p}</p>
      ))}
      <Campo id="borrado-confirmar" etiqueta={B.confirmarNombre(nombre)} autoComplete="off" value={escrito} onChange={(e) => setEscrito(e.target.value)} />
      {ejecutar.error !== null && <Aviso>{ejecutar.error.message}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton
          variante="peligro"
          disabled={!coincide}
          ocupado={ejecutar.isPending}
          onClick={() => solicitud.id !== undefined && ejecutar.mutate(solicitud.id)}
        >
          {B.ejecutarConfirmar}
        </Boton>
      </div>
    </div>
  );
}

export function Borrados() {
  const [registrando, setRegistrando] = useState(false);
  const [ejecutando, setEjecutando] = useState<Solicitud | null>(null);
  const [rechazando, setRechazando] = useState<Solicitud | null>(null);

  const pendientes = useQuery({ queryKey: [...CLAVE, 'pendientes'], queryFn: () => pedir(cliente.GET('/api/v1/borrados/pendientes', {})) });
  const rechazar = useMutacion(
    ({ id, comentario }: { id: number; comentario: string }) =>
      pedir(cliente.POST('/api/v1/borrados/{id}/rechazar', { params: { path: { id } }, body: { comentario } })),
    { invalida: TRAS_RESOLVER, exito: B.rechazada, alTerminar: () => setRechazando(null) },
  );

  return (
    <div className="nx-pagina nx-pagina--estrecha">
      <header className="nx-cabecera nx-cabecera--con-acciones">
        <h1>{B.titulo}</h1>
        <Boton variante="secundario" onClick={() => setRegistrando(true)}>
          {B.registrar}
        </Boton>
      </header>
      <p className="nx-sutil">{B.explicacion}</p>

      <section className="nx-tarjeta">
        <EstadoDeConsulta consulta={pendientes} cargando={<Esqueleto lineas={3} />}>
          {(lista) =>
            lista.length === 0 ? (
              <Vacio titulo={B.vacioTitulo} detalle={B.vacioTexto} />
            ) : (
              <ul className="nx-lista-incidencias" aria-label={B.titulo}>
                {lista.map((s) => {
                  const bloqueos = s.bloqueos ?? [];
                  return (
                    <li key={s.id} className="nx-incidencia">
                      <div className="nx-incidencia__cabecera">
                        <strong>{s.nombre}</strong>
                        <span className="nx-sutil">{s.email}</span>
                      </div>
                      {s.creadaEn && <span className="nx-sutil">{B.pedidaEl(fechaCorta(diaEnEmpresa(s.creadaEn)))}</span>}
                      {s.registradaPor && <span className="nx-sutil">{B.registradaPor(s.registradaPor)}</span>}
                      {s.motivo && <span>{B.motivo(s.motivo)}</span>}
                      {bloqueos.length > 0 && (
                        <div className="nx-bloqueos">
                          <span>{B.bloqueos}</span>
                          <ul>
                            {bloqueos.map((b) => (
                              <li key={b}>{b}</li>
                            ))}
                          </ul>
                        </div>
                      )}
                      <div className="nx-acciones-fila">
                        {bloqueos.length === 0 && (
                          <Boton variante="texto" onClick={() => setEjecutando(s)}>
                            {B.ejecutar}
                          </Boton>
                        )}
                        <Boton variante="texto" onClick={() => setRechazando(s)}>
                          {B.rechazar}
                        </Boton>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )
          }
        </EstadoDeConsulta>
      </section>

      <Dialogo abierto={registrando} titulo={B.registrarTitulo} alCerrar={() => setRegistrando(false)} acciones={null}>
        {registrando && <FormularioDeRegistro alTerminar={() => setRegistrando(false)} />}
      </Dialogo>

      <Dialogo abierto={ejecutando !== null} titulo={B.ejecutarTitulo(ejecutando?.nombre ?? '')} alCerrar={() => setEjecutando(null)} acciones={null}>
        {ejecutando !== null && <ConfirmarEjecucion solicitud={ejecutando} alTerminar={() => setEjecutando(null)} />}
      </Dialogo>

      <DialogoDeTexto
        abierto={rechazando !== null}
        titulo={B.rechazarTitulo(rechazando?.nombre ?? '')}
        ayuda={B.rechazarAyuda}
        etiqueta={B.comentario}
        vacio={B.comentarioVacio}
        boton={B.rechazar}
        peligro
        maxLength={500}
        ocupado={rechazar.isPending}
        error={rechazar.error?.message ?? null}
        alEnviar={(comentario) => rechazando?.id !== undefined && rechazar.mutate({ id: rechazando.id, comentario })}
        alCerrar={() => {
          rechazar.reset();
          setRechazando(null);
        }}
      />
    </div>
  );
}
