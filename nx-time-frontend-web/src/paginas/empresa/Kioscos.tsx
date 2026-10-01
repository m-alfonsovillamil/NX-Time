/**
 * Los kioscos de la empresa, dentro de sus ajustes (ADR 033): dar de alta una
 * tablet con el código que enseña, ver cuándo se fichó en cada una y revocarlas.
 *
 * Solo ADMIN, como el resto de los ajustes: un kiosco ficha en nombre de
 * cualquiera de la plantilla que tenga tarjeta o PIN.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { kiosco } from '../../i18n/es/kiosco';
import { fechaHoraCorta } from '../../util/fechas';

const G = kiosco.gestion;

type Kiosco = components['schemas']['KioskResponse'];

export const CLAVE_KIOSCOS = ['empresa', 'kioscos'] as const;

function Alta() {
  const [codigo, setCodigo] = useState('');
  const [nombre, setNombre] = useState('');
  const [falta, setFalta] = useState(false);
  const alta = useMutacion(
    (cuerpo: { codigo: string; nombre: string }) => pedir(cliente.POST('/api/v1/empresa/kioscos', { body: cuerpo })),
    {
      invalida: [CLAVE_KIOSCOS],
      exito: (k) => G.anadido(k.nombre ?? ''),
      alTerminar: () => {
        setCodigo('');
        setNombre('');
      },
    },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (codigo.trim() === '' || nombre.trim() === '') return setFalta(true);
    setFalta(false);
    alta.mutate({ codigo: codigo.trim(), nombre: nombre.trim() });
  }

  return (
    <form className="nx-formulario" onSubmit={enviar} noValidate>
      <Campo
        id="kiosco-codigo"
        etiqueta={G.codigo}
        maxLength={20}
        autoCapitalize="characters"
        autoComplete="off"
        value={codigo}
        onChange={(e) => setCodigo(e.target.value)}
      />
      <Campo
        id="kiosco-nombre"
        etiqueta={G.nombre}
        ayuda={G.nombreAyuda}
        maxLength={80}
        value={nombre}
        onChange={(e) => setNombre(e.target.value)}
      />
      {falta && <Aviso>{G.faltan}</Aviso>}
      <div className="nx-acciones-fila">
        <Boton type="submit" ocupado={alta.isPending}>
          {G.anadir}
        </Boton>
      </div>
      {alta.error !== null && <Aviso>{alta.error.message}</Aviso>}
    </form>
  );
}

export function Kioscos() {
  const kioscos = useQuery({
    queryKey: CLAVE_KIOSCOS,
    queryFn: () => pedir(cliente.GET('/api/v1/empresa/kioscos', {})),
  });
  const [revocando, setRevocando] = useState<Kiosco | null>(null);
  const revocar = useMutacion(
    (id: number) => pedir(cliente.DELETE('/api/v1/empresa/kioscos/{id}', { params: { path: { id } } })),
    { invalida: [CLAVE_KIOSCOS], exito: G.revocadoAviso, alTerminar: () => setRevocando(null) },
  );

  return (
    <section className="nx-tarjeta" aria-labelledby="kioscos-titulo">
      <h2 id="kioscos-titulo">{G.titulo}</h2>
      <p className="nx-sutil">{G.explicacion}</p>
      <Alta />
      <EstadoDeConsulta consulta={kioscos} cargando={<Esqueleto lineas={2} />}>
        {(lista) =>
          lista.length === 0 ? (
            <Vacio titulo={G.ninguno} />
          ) : (
            <ul className="nx-lista-incidencias">
              {lista.map((k) => (
                <li key={k.id} className="nx-incidencia">
                  <div className="nx-incidencia__cabecera">
                    <strong>{k.nombre}</strong>
                    {k.activo ? (
                      <Boton variante="texto" onClick={() => setRevocando(k)}>
                        {G.revocar}
                      </Boton>
                    ) : (
                      <span className="nx-sutil">{G.revocado}</span>
                    )}
                  </div>
                  <p className="nx-sutil">
                    {k.ultimoUso !== undefined ? G.ultimoUso(fechaHoraCorta(k.ultimoUso)) : G.sinUso}
                  </p>
                </li>
              ))}
            </ul>
          )
        }
      </EstadoDeConsulta>

      <Dialogo
        abierto={revocando !== null}
        titulo={G.revocarTitulo(revocando?.nombre ?? '')}
        alCerrar={() => {
          revocar.reset();
          setRevocando(null);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setRevocando(null)}>
              {T.app.cancelar}
            </Boton>
            <Boton
              variante="peligro"
              ocupado={revocar.isPending}
              onClick={() => revocando?.id !== undefined && revocar.mutate(revocando.id)}
            >
              {G.revocar}
            </Boton>
          </>
        }
      >
        <p>{G.revocarTexto}</p>
        {revocar.error !== null && <Aviso>{revocar.error.message}</Aviso>}
      </Dialogo>
    </section>
  );
}
