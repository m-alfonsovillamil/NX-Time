/**
 * El aviso al ADMIN de una empresa que no tiene aceptada la versión en vigor
 * de las condiciones y del contrato de encargo (ADR 046): las de antes de la
 * V43, y todas cada vez que cambien. No bloquea nada; se queda hasta que se
 * aceptan.
 */

import { cliente } from '../api/cliente';
import { pedir, useMutacion } from '../api/consultas';
import { abrirSesion, sesionActual } from '../api/sesion';
import { useSesion } from '../api/useSesion';
import { Aviso, Boton } from '../componentes/Basicos';
import { T } from '../i18n/es';

const C = T.navegacion.condiciones;

export function AvisoDeCondiciones() {
  const { sesion } = useSesion();
  const aceptar = useMutacion(() => pedir(cliente.POST('/api/v1/empresa/condiciones/aceptar')), {
    alTerminar: () => {
      const actual = sesionActual();
      if (actual !== null) abrirSesion({ ...actual, condicionesPorAceptar: false });
    },
  });

  if (sesion?.condicionesPorAceptar !== true) return null;
  return (
    <div className="nx-aviso-condiciones" role="region" aria-label={C.texto}>
      <p>{C.texto}</p>
      <div className="nx-aviso-condiciones__acciones">
        <a className="nx-boton nx-boton--secundario nx-boton--enlace" href="/legal/condiciones" target="_blank" rel="noopener">
          {C.leer}
        </a>
        <Boton ocupado={aceptar.isPending} onClick={() => aceptar.mutate()}>
          {aceptar.isPending ? C.aceptando : C.aceptar}
        </Boton>
      </div>
      {aceptar.error !== null && <Aviso>{aceptar.error.message}</Aviso>}
    </div>
  );
}
