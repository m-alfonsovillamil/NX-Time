/**
 * El canal de denuncias visto por quien instruye (Ley 2/2023, solo ADMIN, que
 * es el Responsable del Sistema Interno de Información). Es la
 * `CanalDenunciasScreen` de la app.
 *
 * La bandeja trae primero las abiertas y, dentro, las más antiguas: son las
 * que están más cerca de que venza el plazo (7 días para el acuse, 3 meses
 * para responder), y el plazo que corre se ve en cada una, en rojo si ya
 * venció. **El primer mensaje cuenta como acuse de recibo.**
 *
 * **Cerrar exige conclusión**, tanto si se resuelve como si se archiva: la ley
 * obliga a responder, no a dar la razón. Un expediente cerrado no se reabre.
 * Nadie instruye una denuncia que presentó él mismo: el servidor lo rechaza
 * (403) y su mensaje se enseña.
 *
 * El expediente se pinta con el mismo cuerpo que ve quien denuncia
 * (`CuerpoDeExpediente`), para que los dos lean lo mismo.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { AreaDeTexto, Aviso, Boton, Insignia } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { DialogoDeTexto } from '../../componentes/DialogoDeTexto';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { denuncias } from '../../i18n/es/denuncias';
import { fechaHoraCorta } from '../../util/fechas';
import { CuerpoDeExpediente, Plazo, TONO_DENUNCIA, cerrado } from './Denuncias';

const D = denuncias;
const C = denuncias.canal;

type Expediente = components['schemas']['ComplaintResponse'];
type Estado = components['schemas']['UpdateComplaintStatusRequest']['estado'];

const CLAVE = ['canal-denuncias'] as const;

function Responder({ id }: { id: number }) {
  const [texto, setTexto] = useState('');
  const [falta, setFalta] = useState(false);
  const responder = useMutacion(
    (t: string) => pedir(cliente.POST('/api/v1/denuncias/{id}/mensajes', { params: { path: { id } }, body: { texto: t } })),
    { invalida: [CLAVE], exito: D.mensajeEnviado, alTerminar: () => setTexto('') },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (texto.trim() === '') return setFalta(true);
    setFalta(false);
    responder.mutate(texto.trim());
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <AreaDeTexto
        id="canal-mensaje"
        etiqueta={C.responder}
        ayuda={C.acusa}
        error={falta ? D.mensajeVacio : undefined}
        maxLength={5000}
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
      />
      {responder.error !== null && <Aviso>{responder.error.message}</Aviso>}
      <div>
        <Boton type="submit" ocupado={responder.isPending}>
          {D.enviarMensaje}
        </Boton>
      </div>
    </form>
  );
}

function Expediente({ id }: { id: number }) {
  const [cerrando, setCerrando] = useState<'RESUELTA' | 'ARCHIVADA' | null>(null);
  const expediente = useQuery({
    queryKey: [...CLAVE, id],
    queryFn: () => pedir(cliente.GET('/api/v1/denuncias/{id}', { params: { path: { id } } })),
  });
  const cambiar = useMutacion(
    ({ estado, conclusion }: { estado: Estado; conclusion?: string }) =>
      pedir(
        cliente.PATCH('/api/v1/denuncias/{id}/estado', {
          params: { path: { id } },
          body: { estado, ...(conclusion !== undefined ? { conclusion } : {}) },
        }),
      ),
    {
      invalida: [CLAVE, ['dashboard']],
      exito: (_r, v) => (v.conclusion !== undefined ? C.cerrada : C.actualizada),
      alTerminar: () => setCerrando(null),
    },
  );

  return (
    <EstadoDeConsulta consulta={expediente} cargando={<Esqueleto lineas={5} />}>
      {(e: Expediente) => (
        <>
          <CuerpoDeExpediente
            e={e}
            subtitulo={[e.anonima === true ? C.esAnonima : C.laPuso(e.denunciante ?? ''), e.creadoEn ? fechaHoraCorta(e.creadoEn) : ''].filter(Boolean).join(' · ')}
          >
            {cerrado(e) ? (
              <p className="nx-sutil">{D.cerrado}</p>
            ) : (
              <>
                <Responder id={id} />
                <div className="nx-acciones-fila">
                  {e.estado === 'RECIBIDA' && (
                    <Boton variante="secundario" ocupado={cambiar.isPending && cerrando === null} onClick={() => cambiar.mutate({ estado: 'EN_INVESTIGACION' })}>
                      {C.aInvestigacion}
                    </Boton>
                  )}
                  <Boton variante="texto" onClick={() => setCerrando('RESUELTA')}>
                    {C.resolver}
                  </Boton>
                  <Boton variante="texto" onClick={() => setCerrando('ARCHIVADA')}>
                    {C.archivar}
                  </Boton>
                </div>
                {cambiar.error !== null && cerrando === null && <Aviso>{cambiar.error.message}</Aviso>}
              </>
            )}
          </CuerpoDeExpediente>

          <DialogoDeTexto
            abierto={cerrando !== null}
            titulo={C.cerrarTitulo(cerrando === 'ARCHIVADA' ? C.archivar : C.resolver)}
            ayuda={C.conclusionAyuda}
            etiqueta={D.conclusion}
            vacio={C.conclusionVacia}
            boton={cerrando === 'ARCHIVADA' ? C.archivar : C.resolver}
            maxLength={5000}
            ocupado={cambiar.isPending}
            error={cambiar.error?.message ?? null}
            alEnviar={(conclusion) => cerrando !== null && cambiar.mutate({ estado: cerrando, conclusion })}
            alCerrar={() => {
              cambiar.reset();
              setCerrando(null);
            }}
          />
        </>
      )}
    </EstadoDeConsulta>
  );
}

export function CanalDenuncias() {
  const [abierto, setAbierto] = useState<{ id: number; titulo: string } | null>(null);
  const bandeja = useQuery({ queryKey: [...CLAVE, 'bandeja'], queryFn: () => pedir(cliente.GET('/api/v1/denuncias', {})) });

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{C.titulo}</h1>
      </header>
      <p className="nx-sutil">{C.explicacion}</p>

      <section className="nx-tarjeta">
        <EstadoDeConsulta consulta={bandeja} cargando={<Esqueleto lineas={4} />}>
          {(lista) =>
            lista.length === 0 ? (
              <Vacio titulo={C.vacioTitulo} detalle={C.vacioTexto} />
            ) : (
              <ul className="nx-lista-incidencias" aria-label={C.titulo}>
                {lista.map((r) => (
                  <li key={r.id} className="nx-incidencia">
                    <div className="nx-incidencia__cabecera">
                      <strong>{r.categoriaEtiqueta}</strong>
                      {r.estado && <Insignia tono={TONO_DENUNCIA[r.estado] ?? 'neutro'}>{D.estados[r.estado] ?? r.estado}</Insignia>}
                    </div>
                    <span className="nx-sutil">
                      {r.anonima === true ? C.esAnonima : ''}
                      {r.anonima === true && r.creadoEn ? ' · ' : ''}
                      {r.creadoEn ? fechaHoraCorta(r.creadoEn) : ''}
                    </span>
                    <Plazo e={r} />
                    <span className="nx-sutil">{D.mensajesCuenta(r.mensajes ?? 0)}</span>
                    <div className="nx-acciones-fila">
                      <Boton
                        variante="texto"
                        aria-label={`${C.abrir}: ${r.categoriaEtiqueta ?? ''}, ${r.creadoEn ? fechaHoraCorta(r.creadoEn) : ''}`}
                        onClick={() => r.id !== undefined && setAbierto({ id: r.id, titulo: r.categoriaEtiqueta ?? '' })}
                      >
                        {C.abrir}
                      </Boton>
                    </div>
                  </li>
                ))}
              </ul>
            )
          }
        </EstadoDeConsulta>
      </section>

      <Dialogo abierto={abierto !== null} titulo={abierto?.titulo ?? ''} alCerrar={() => setAbierto(null)}>
        {abierto !== null && <Expediente id={abierto.id} />}
      </Dialogo>
    </div>
  );
}
