/**
 * El aviso de privacidad de la primera entrada (plan J, fase J5; ADR 047):
 * quién es la responsable de los datos de cada persona (su empresa), qué
 * papel tiene NX Time y a quién dirigirse. Sale una vez; con «Entendido» no
 * vuelve, tampoco en la app.
 *
 * No bloquea nada, como el de las condiciones: informar es cosa de la empresa,
 * y esto solo pone el contacto delante.
 */

import { useQuery, useQueryClient } from '@tanstack/react-query';

import { cliente } from '../api/cliente';
import { pedir, useMutacion } from '../api/consultas';
import { Aviso, Boton } from '../componentes/Basicos';
import { T } from '../i18n/es';

const P = T.navegacion.privacidad;

const CLAVE = ['perfil', 'aviso-de-privacidad'] as const;

function esCorreo(contacto: string): boolean {
  return !contacto.startsWith('https://');
}

export function AvisoDePrivacidad() {
  const clienteDeConsultas = useQueryClient();
  const aviso = useQuery({
    queryKey: CLAVE,
    queryFn: () => pedir(cliente.GET('/api/v1/perfil/aviso-de-privacidad', {})),
    // Una vez por sesión basta: no cambia mientras se está dentro.
    staleTime: Infinity,
  });
  const entendido = useMutacion(() => pedir(cliente.POST('/api/v1/perfil/aviso-de-privacidad/visto')), {
    alTerminar: () => clienteDeConsultas.setQueryData(CLAVE, (antes) => (antes ? { ...antes, visto: true } : antes)),
  });

  const datos = aviso.data;
  if (datos === undefined || datos.visto) return null;
  const contacto = datos.contacto ?? null;

  return (
    <div className="nx-aviso-condiciones" role="region" aria-label={P.titulo}>
      <p>
        <strong>{P.titulo}.</strong> {P.texto(datos.empresa ?? '')}
      </p>
      <p>
        {contacto === null ? (
          P.sinContacto
        ) : esCorreo(contacto) ? (
          <>
            {P.contactoCorreo}{' '}
            <a className="nx-enlace" href={`mailto:${contacto}`}>
              {contacto}
            </a>
            .
          </>
        ) : (
          <>
            {P.contactoEnlace}{' '}
            <a className="nx-enlace" href={contacto} target="_blank" rel="noopener noreferrer">
              {contacto}
            </a>
          </>
        )}
      </p>
      <div className="nx-aviso-condiciones__acciones">
        <a className="nx-boton nx-boton--secundario nx-boton--enlace" href="/legal/privacidad" target="_blank" rel="noopener">
          {P.masInformacion}
        </a>
        <Boton ocupado={entendido.isPending} onClick={() => entendido.mutate()}>
          {P.entendido}
        </Boton>
      </div>
      {entendido.error !== null && <Aviso>{entendido.error.message}</Aviso>}
    </div>
  );
}
