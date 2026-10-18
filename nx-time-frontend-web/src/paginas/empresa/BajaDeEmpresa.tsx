/**
 * Dar de baja la empresa (condiciones, punto 9; ADR 050). Solo ADMIN.
 *
 * Es lo más irreversible de la aplicación: a los treinta días se borra todo,
 * también la auditoría. Por eso pide escribir el nombre de la empresa (como
 * ejecutar un borrado de datos de una persona), dice qué hay que llevarse
 * antes y se puede cancelar mientras tanto.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { Aviso, Boton, Campo, Tarjeta } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { T } from '../../i18n/es';
import { empresa } from '../../i18n/es/empresa';
import { diaEnEmpresa, fechaCorta } from '../../util/fechas';

const B = empresa.baja;

const CLAVE = ['empresa', 'baja'] as const;

export function BajaDeEmpresa({ nombreEmpresa }: { nombreEmpresa: string }) {
  const [confirmando, setConfirmando] = useState(false);
  const [escrito, setEscrito] = useState('');
  const estado = useQuery({ queryKey: CLAVE, queryFn: () => pedir(cliente.GET('/api/v1/empresa/baja', {})) });

  const pedirLaBaja = useMutacion(
    () => pedir(cliente.POST('/api/v1/empresa/baja', { body: { confirmacion: escrito.trim() } })),
    {
      invalida: [CLAVE],
      exito: B.pedida,
      alTerminar: () => {
        setConfirmando(false);
        setEscrito('');
      },
    },
  );
  const cancelar = useMutacion(() => pedir(cliente.DELETE('/api/v1/empresa/baja', {})), {
    invalida: [CLAVE],
    exito: B.cancelada,
  });

  const borradoDesde = estado.data?.borradoDesde;
  const coincide = escrito.trim().toLowerCase() === nombreEmpresa.trim().toLowerCase();

  return (
    <Tarjeta titulo={B.titulo} icono="papelera" descripcion={B.explicacion}>
      {borradoDesde ? (
        <>
          <Aviso>{B.enCurso(fechaCorta(diaEnEmpresa(borradoDesde)))}</Aviso>
          <p className="nx-sutil">
            {B.llevatelo}{' '}
            <Link className="nx-enlace" to="/informes">
              {B.irAInformes}
            </Link>
          </p>
          <div className="nx-acciones-fila">
            <Boton variante="secundario" ocupado={cancelar.isPending} onClick={() => cancelar.mutate()}>
              {B.cancelar}
            </Boton>
          </div>
          {cancelar.error !== null && <Aviso>{cancelar.error.message}</Aviso>}
        </>
      ) : (
        <div className="nx-acciones-fila">
          <Boton variante="peligro" onClick={() => setConfirmando(true)}>
            {B.pedir}
          </Boton>
        </div>
      )}

      <Dialogo
        abierto={confirmando}
        titulo={B.confirmarTitulo}
        alCerrar={() => {
          pedirLaBaja.reset();
          setConfirmando(false);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setConfirmando(false)}>
              {T.app.cancelar}
            </Boton>
            <Boton variante="peligro" disabled={!coincide} ocupado={pedirLaBaja.isPending} onClick={() => pedirLaBaja.mutate()}>
              {B.confirmar}
            </Boton>
          </>
        }
      >
        <p>{B.confirmarTexto}</p>
        <Campo
          id="baja-confirmacion"
          etiqueta={B.escribeElNombre(nombreEmpresa)}
          autoComplete="off"
          value={escrito}
          onChange={(e) => setEscrito(e.target.value)}
        />
        {pedirLaBaja.error !== null && <Aviso>{pedirLaBaja.error.message}</Aviso>}
      </Dialogo>
    </Tarjeta>
  );
}
