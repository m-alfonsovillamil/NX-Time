/**
 * Ofertas internas: las vacantes publicadas y mis candidaturas.
 *
 * Es la `OfertasScreen` de la app. Lo que se puede hacer con cada vacante lo
 * dice el servidor (`admiteCandidaturas`, `plazoVencido`, `yaMePresente`) y la
 * página solo lo enseña: la regla vive en un sitio. Las que ya pasaron su
 * fecha de cierre siguen saliendo, marcadas: esconderlas dejaría sin saber qué
 * pasó a quien la vio ayer.
 *
 * **El CV no se elige aquí**: lo adjunta el servidor, el que haya en el perfil
 * en ese momento, y lo congela. Hay que decirlo en el formulario, o la gente
 * busca dónde adjuntarlo. Sin CV el servidor dice que no (400) con su propio
 * mensaje, que se lee en el diálogo junto a un enlace al perfil.
 *
 * `/mis-candidaturas` (el destino de los avisos de una candidatura valorada)
 * es esta misma página con la segunda pestaña abierta.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { useSesion } from '../../api/useSesion';
import { AreaDeTexto, Aviso, Boton, Insignia, type Tono } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Pestanas } from '../../componentes/Pestanas';
import { T } from '../../i18n/es';
import { ofertas } from '../../i18n/es/ofertas';
import { diaEnEmpresa, fechaCorta } from '../../util/fechas';

const O = ofertas;

type Oferta = components['schemas']['JobPostingResponse'];
type Candidatura = components['schemas']['JobApplicationResponse'];

const CLAVES = {
  publicadas: ['ofertas', 'publicadas'] as const,
  candidaturas: ['ofertas', 'candidaturas', 'mias'] as const,
};

const TONO_CANDIDATURA: Record<string, Tono> = {
  RECIBIDA: 'neutro',
  EN_PROCESO: 'info',
  DESCARTADA: 'error',
  SELECCIONADA: 'exito',
};

/** Hasta cuándo se puede optar, o que ya no se puede. */
export function plazoDe(o: Oferta): string {
  if (o.plazoVencido === true) return O.plazoTerminado;
  if (o.fechaCierre) return O.plazoHasta(fechaCorta(o.fechaCierre));
  return O.sinPlazo;
}

function TarjetaDeOferta({ o, alPresentarse }: { o: Oferta; alPresentarse: (o: Oferta) => void }) {
  const detalle = [o.puesto, o.departamento].filter(Boolean).join(' · ');
  return (
    <li className="nx-incidencia">
      <div className="nx-incidencia__cabecera">
        <strong>{o.titulo}</strong>
        {o.yaMePresente === true ? (
          <Insignia tono="exito">{O.yaPresentado}</Insignia>
        ) : (
          o.plazoVencido === true && <Insignia tono="aviso">{O.plazoTerminado}</Insignia>
        )}
      </div>
      {detalle && <span className="nx-sutil">{detalle}</span>}
      {o.descripcion && <p className="nx-texto-largo">{o.descripcion}</p>}
      <span className="nx-sutil">
        {plazoDe(o)}
        {o.publicadaPor ? ` · ${O.publicadaPor(o.publicadaPor)}` : ''}
      </span>
      {o.yaMePresente === true ? (
        <span className="nx-sutil">{O.yaPresentadoDetalle}</span>
      ) : o.admiteCandidaturas === true ? (
        <div className="nx-acciones-fila">
          <Boton variante="secundario" onClick={() => alPresentarse(o)}>
            {O.presentar}
          </Boton>
        </div>
      ) : (
        o.plazoVencido !== true && <span className="nx-sutil">{O.noAdmite}</span>
      )}
    </li>
  );
}

function FormularioDeCandidatura({
  ocupado,
  error,
  alEnviar,
  alCerrar,
}: {
  ocupado: boolean;
  error: string | null;
  alEnviar: (carta: string) => void;
  alCerrar: () => void;
}) {
  const [carta, setCarta] = useState('');

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    alEnviar(carta.trim());
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <AreaDeTexto
        id="candidatura-carta"
        etiqueta={O.carta}
        rows={5}
        maxLength={2000}
        value={carta}
        onChange={(e) => setCarta(e.target.value)}
      />
      <p className="nx-sutil">{O.cvAutomatico}</p>
      <p className="nx-sutil">
        {O.sinCv}{' '}
        <Link className="nx-enlace" to="/perfil">
          {O.irAlPerfil}
        </Link>
      </p>
      {error !== null && <Aviso>{error}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alCerrar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={ocupado}>
          {O.presentar}
        </Boton>
      </div>
    </form>
  );
}

function Vacantes() {
  const [presentandose, setPresentandose] = useState<Oferta | null>(null);
  const lista = useQuery({ queryKey: CLAVES.publicadas, queryFn: () => pedir(cliente.GET('/api/v1/ofertas', {})) });

  const presentar = useMutacion(
    ({ id, carta }: { id: number; carta: string }) =>
      pedir(
        cliente.POST('/api/v1/ofertas/{id}/candidaturas', {
          params: { path: { id } },
          body: carta === '' ? {} : { carta },
        }),
      ),
    { invalida: [['ofertas']], exito: O.presentada, alTerminar: () => setPresentandose(null) },
  );

  function cerrar() {
    presentar.reset();
    setPresentandose(null);
  }

  return (
    <>
      <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={4} />}>
        {(vacantes) =>
          vacantes.length === 0 ? (
            <Vacio titulo={O.vacio} />
          ) : (
            <ul className="nx-lista-incidencias" aria-label={O.abiertas}>
              {vacantes.map((o) => (
                <TarjetaDeOferta key={o.id} o={o} alPresentarse={setPresentandose} />
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <Dialogo
        abierto={presentandose !== null}
        titulo={presentandose !== null ? O.presentarTitulo(presentandose.titulo ?? '') : ''}
        alCerrar={cerrar}
        acciones={null}
      >
        <FormularioDeCandidatura
          ocupado={presentar.isPending}
          error={presentar.error?.message ?? null}
          alEnviar={(carta) => presentandose?.id !== undefined && presentar.mutate({ id: presentandose.id, carta })}
          alCerrar={cerrar}
        />
      </Dialogo>
    </>
  );
}

function TarjetaDeCandidatura({ c }: { c: Candidatura }) {
  return (
    <li className="nx-incidencia">
      <div className="nx-incidencia__cabecera">
        <strong>{c.ofertaTitulo}</strong>
        {c.estado && (
          <Insignia tono={TONO_CANDIDATURA[c.estado] ?? 'neutro'}>{O.estadosCandidatura[c.estado] ?? c.estado}</Insignia>
        )}
      </div>
      {c.creadoEn && <span className="nx-sutil">{O.presentadaEl(fechaCorta(diaEnEmpresa(c.creadoEn)))}</span>}
      {c.cvNombre && <span className="nx-sutil">{O.cvAdjunto(c.cvNombre)}</span>}
      {c.comentario && <span>{O.comentario(c.comentario)}</span>}
      {c.resueltaPor && <span className="nx-sutil">{O.resueltaPor(c.resueltaPor)}</span>}
    </li>
  );
}

function MisCandidaturas() {
  const lista = useQuery({
    queryKey: CLAVES.candidaturas,
    queryFn: () => pedir(cliente.GET('/api/v1/candidaturas/mias', {})),
  });

  return (
    <EstadoDeConsulta consulta={lista} cargando={<Esqueleto lineas={3} />}>
      {(candidaturas) =>
        candidaturas.length === 0 ? (
          <Vacio titulo={O.candidaturasVacio} />
        ) : (
          <ul className="nx-lista-incidencias" aria-label={O.misCandidaturas}>
            {candidaturas.map((c) => (
              <TarjetaDeCandidatura key={c.id} c={c} />
            ))}
          </ul>
        )
      }
    </EstadoDeConsulta>
  );
}

type Pestana = 'vacantes' | 'candidaturas';

export function Ofertas({ pestanaInicial = 'vacantes' }: { pestanaInicial?: Pestana }) {
  const { puede } = useSesion();
  const [pestana, setPestana] = useState<Pestana>(pestanaInicial);
  // Leer ofertas y presentarse son dos authorities: hoy las tiene toda la
  // plantilla, pero si un rol perdiera la segunda, su pestaña no tendría nada.
  const puedeOptar = puede('candidatura:crear');
  const puedeLeer = puede('oferta:leer');

  return (
    <div className="nx-pagina nx-pagina--ancha">
      <header className="nx-cabecera">
        <h1>{O.titulo}</h1>
      </header>
      <section className="nx-tarjeta">
        {puedeOptar && puedeLeer ? (
          <Pestanas
            etiqueta={O.pestanas}
            pestanas={[
              { clave: 'vacantes', texto: O.abiertas },
              { clave: 'candidaturas', texto: O.misCandidaturas },
            ]}
            activa={pestana}
            alCambiar={setPestana}
          >
            {pestana === 'vacantes' ? <Vacantes /> : <MisCandidaturas />}
          </Pestanas>
        ) : puedeLeer ? (
          <Vacantes />
        ) : (
          <MisCandidaturas />
        )}
      </section>
    </div>
  );
}

export function PaginaMisCandidaturas() {
  return <Ofertas pestanaInicial="candidaturas" />;
}
