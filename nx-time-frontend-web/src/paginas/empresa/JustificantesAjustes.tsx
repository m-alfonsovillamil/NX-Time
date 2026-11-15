/**
 * Justificantes en las ausencias (ADR 062), en los ajustes de la empresa.
 *
 * Apagado por defecto. Un justificante médico es un dato de salud (art. 9
 * RGPD): encenderlo pide confirmación y recuerda que hay que revisarlo antes
 * con quien asesore a la empresa. Apagarlo no borra los que haya.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { Aviso, Boton, Tarjeta } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { T } from '../../i18n/es';
import { empresa } from '../../i18n/es/empresa';
import { CLAVE_JUSTIFICANTES } from '../ausencias/Justificantes';

const J = empresa.ajustes.justificantes;

function Formulario({ guardado }: { guardado: boolean }) {
  const [activar, setActivar] = useState(guardado);
  const [confirmando, setConfirmando] = useState(false);
  const guardar = useMutacion(
    (valor: boolean) => pedir(cliente.PUT('/api/v1/empresa/justificantes', { body: { activo: valor } })),
    {
      invalida: [CLAVE_JUSTIFICANTES],
      exito: (r) => (r.activo === true ? J.activados : J.desactivados),
      alTerminar: () => setConfirmando(false),
    },
  );

  return (
    <>
      <div className="nx-columna">
        <label className="nx-casilla">
          <input type="checkbox" checked={activar} onChange={(e) => setActivar(e.target.checked)} />
          <span>{J.activar}</span>
        </label>
        <div>
          <Boton
            variante="secundario"
            ocupado={guardar.isPending && !confirmando}
            disabled={activar === guardado}
            onClick={() => (activar ? setConfirmando(true) : guardar.mutate(false))}
          >
            {J.guardar}
          </Boton>
        </div>
        {guardar.error !== null && !confirmando && <Aviso>{guardar.error.message}</Aviso>}
      </div>
      <Dialogo
        abierto={confirmando}
        titulo={J.confirmarTitulo}
        alCerrar={() => setConfirmando(false)}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setConfirmando(false)}>
              {T.app.cancelar}
            </Boton>
            <Boton ocupado={guardar.isPending} onClick={() => guardar.mutate(true)}>
              {J.confirmar}
            </Boton>
          </>
        }
      >
        <p>{J.confirmarSalud}</p>
        <p>{J.confirmarAsesoria}</p>
        {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
      </Dialogo>
    </>
  );
}

export function JustificantesAjustes() {
  // Sin ausencias que mirar, la respuesta solo dice si están activados.
  const estado = useQuery({
    queryKey: [...CLAVE_JUSTIFICANTES, 'activo'],
    queryFn: () => pedir(cliente.GET('/api/v1/ausencias/justificantes', {})),
  });
  return (
    <Tarjeta titulo={J.titulo} icono="candado" descripcion={J.texto}>
      {estado.data !== undefined && <Formulario key={String(estado.data.activo)} guardado={estado.data.activo === true} />}
      {estado.error !== null && <Aviso>{estado.error.message}</Aviso>}
    </Tarjeta>
  );
}
