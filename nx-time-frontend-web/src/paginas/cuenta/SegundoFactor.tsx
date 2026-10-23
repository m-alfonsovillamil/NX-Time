/**
 * El segundo factor (ADR 051): activarlo, desactivarlo y los códigos de
 * recuperación. Lo usan la tarjeta de Ajustes y la pantalla de quien está
 * obligado a tenerlo y aún no lo tiene, que no puede hacer otra cosa hasta
 * activarlo.
 *
 * El QR llega ya dibujado del servidor (SVG, como la tarjeta del kiosco): la
 * web no necesita una librería de QR. Con él viene la clave en texto, para
 * quien no puede escanearlo.
 *
 * Al confirmar, la sesión desde la que se activa pasa a contar como sesión con
 * segundo factor; el token que lo dice llega al renovarla, y eso es lo que
 * saca de la pantalla obligatoria a quien estaba en ella.
 */

import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';

import { cliente, renovarSesion, salir } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { Aviso, Boton, Campo, Insignia, Tarjeta } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { Esqueleto } from '../../componentes/Estados';
import { notificar } from '../../componentes/Notificaciones';
import { cuenta } from '../../i18n/es/cuenta';
import { T } from '../../i18n/es';
import { diaEnEmpresa, fechaCorta } from '../../util/fechas';
import { MarcoDeAcceso } from '../acceso/MarcoDeAcceso';
import { srcDeTarjeta } from './FicharEnKiosco';

const S = cuenta.ajustes.segundoFactor;

export const CLAVE_SEGUNDO_FACTOR = ['perfil', 'segundo-factor'] as const;

/** Por debajo de esto se sugiere generar otros. */
const POCOS_CODIGOS = 3;

function useSegundoFactor() {
  return useQuery({
    queryKey: CLAVE_SEGUNDO_FACTOR,
    queryFn: () => pedir(cliente.GET('/api/v1/perfil/segundo-factor', {})),
  });
}

/** La clave en grupos de cuatro, que es como se copia a mano sin perderse. */
export function enGrupos(clave: string): string {
  return clave.match(/.{1,4}/g)?.join(' ') ?? clave;
}

async function copiar(texto: string, hecho: string) {
  try {
    await navigator.clipboard.writeText(texto);
    notificar(hecho);
  } catch {
    // Sin permiso para el portapapeles: el texto sigue a la vista para copiarlo a mano.
  }
}

function descargarCodigos(codigos: readonly string[]) {
  const url = URL.createObjectURL(new Blob([S.recuperacion.fichero(codigos)], { type: 'text/plain' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = 'codigos-recuperacion-nxtime.txt';
  enlace.click();
  // En el siguiente ciclo, como en util/descargar.ts.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** Los códigos de recuperación, la única vez que se ven. */
export function CodigosDeRecuperacion({ codigos, alTerminar }: { codigos: readonly string[]; alTerminar: () => void }) {
  const R = S.recuperacion;
  const [guardados, setGuardados] = useState(false);
  return (
    <div className="nx-columna">
      <p>{R.explicacion}</p>
      <ol className="nx-codigos-recuperacion" aria-label={R.titulo}>
        {codigos.map((codigo) => (
          <li key={codigo}>{codigo}</li>
        ))}
      </ol>
      <div className="nx-acciones-fila">
        <Boton variante="secundario" onClick={() => void copiar(codigos.join('\n'), R.copiados)}>
          {R.copiar}
        </Boton>
        <Boton variante="secundario" onClick={() => descargarCodigos(codigos)}>
          {R.descargar}
        </Boton>
      </div>
      <label className="nx-casilla">
        <input type="checkbox" checked={guardados} onChange={(e) => setGuardados(e.target.checked)} />
        <span>{R.guardados}</span>
      </label>
      <div>
        <Boton disabled={!guardados} onClick={alTerminar}>
          {R.listo}
        </Boton>
      </div>
    </div>
  );
}

/**
 * Activarlo, de principio a fin: el QR y la clave, el código y la contraseña,
 * y los códigos de recuperación.
 */
export function ActivarSegundoFactor({ alTerminar, alCancelar }: { alTerminar: () => void; alCancelar?: () => void }) {
  const A = S.activacion;
  const [codigo, setCodigo] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [codigos, setCodigos] = useState<readonly string[] | null>(null);

  // Un secreto nuevo al abrir. Si se cierra y se vuelve a abrir, otro: el
  // primero deja de valer (lo dice el servidor). Una sola vez por apertura:
  // en desarrollo React ejecuta los efectos dos veces, y dos secretos pedidos
  // a la vez dejaban en pantalla uno que ya no era el guardado (lo vio el e2e).
  const iniciar = useMutacion(() => pedir(cliente.POST('/api/v1/perfil/segundo-factor/activacion', {})));
  const { mutate: empezar } = iniciar;
  const pedido = useRef(false);
  useEffect(() => {
    if (pedido.current) return;
    pedido.current = true;
    empezar(undefined);
  }, [empezar]);

  const confirmar = useMutacion(
    (cuerpo: { contrasena: string; codigo: string }) =>
      pedir(cliente.POST('/api/v1/perfil/segundo-factor/activacion/confirmar', { body: cuerpo })),
    {
      invalida: [CLAVE_SEGUNDO_FACTOR],
      alTerminar: (respuesta) => setCodigos(respuesta.codigos ?? []),
    },
  );

  /*
   * El token nuevo es el que dice que esta sesión ya pasó por el segundo
   * factor. Se pide después de enseñar los códigos y no antes: a quien estaba
   * obligado, renovar le saca de su pantalla, y se iría sin haberlos visto.
   */
  async function terminar() {
    await renovarSesion();
    alTerminar();
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const limpio = codigo.replace(/\s/g, '');
    if (!/^\d{6}$/.test(limpio)) return setError(A.faltaCodigo);
    if (contrasena === '') return setError(A.faltaContrasena);
    setError(null);
    confirmar.mutate({ codigo: limpio, contrasena });
  }

  if (codigos !== null) return <CodigosDeRecuperacion codigos={codigos} alTerminar={() => void terminar()} />;

  const datos = iniciar.data;
  const mensaje = error ?? confirmar.error?.message ?? iniciar.error?.message ?? null;
  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p>{A.paso1}</p>
      {datos === undefined ? (
        iniciar.error === null && <Esqueleto lineas={4} />
      ) : (
        <>
          <figure className="nx-tarjeta-kiosco">
            <img src={srcDeTarjeta(datos.qr ?? '')} alt={A.qr} />
          </figure>
          <p className="nx-sutil">{A.sinCamara}</p>
          <p className="nx-clave-totp" data-testid="clave-totp">
            {enGrupos(datos.secreto ?? '')}
          </p>
          <div>
            <Boton variante="texto" onClick={() => void copiar(datos.secreto ?? '', A.claveCopiada)}>
              {A.copiarClave}
            </Boton>
          </div>
        </>
      )}
      <p>{A.paso2}</p>
      <Campo
        id="activar-codigo"
        etiqueta={A.codigo}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={7}
        value={codigo}
        onChange={(e) => setCodigo(e.target.value)}
      />
      <Campo
        id="activar-contrasena"
        etiqueta={A.contrasena}
        type="password"
        autoComplete="current-password"
        value={contrasena}
        onChange={(e) => setContrasena(e.target.value)}
      />
      {mensaje !== null && <Aviso>{mensaje}</Aviso>}
      <div className="nx-dialogo__acciones">
        {alCancelar !== undefined && (
          <Boton variante="texto" onClick={alCancelar}>
            {T.app.cancelar}
          </Boton>
        )}
        <Boton type="submit" ocupado={confirmar.isPending} disabled={datos === undefined}>
          {datos === undefined ? A.preparando : A.confirmar}
        </Boton>
      </div>
    </form>
  );
}

function Desactivar({ alTerminar }: { alTerminar: () => void }) {
  const D = S.desactivacion;
  const [codigo, setCodigo] = useState('');
  const [contrasena, setContrasena] = useState('');
  const quitar = useMutacion(
    (cuerpo: { contrasena: string; codigo: string }) =>
      pedir(cliente.POST('/api/v1/perfil/segundo-factor/desactivar', { body: cuerpo })),
    { invalida: [CLAVE_SEGUNDO_FACTOR], exito: D.hecho, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    quitar.mutate({ codigo: codigo.trim(), contrasena });
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p>{D.explicacion}</p>
      <Campo id="desactivar-contrasena" etiqueta={D.contrasena} type="password" autoComplete="current-password" value={contrasena} onChange={(e) => setContrasena(e.target.value)} />
      <Campo id="desactivar-codigo" etiqueta={D.codigo} autoComplete="one-time-code" maxLength={14} value={codigo} onChange={(e) => setCodigo(e.target.value)} />
      {quitar.error !== null && <Aviso>{quitar.error.message}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" variante="peligro" ocupado={quitar.isPending}>
          {D.confirmar}
        </Boton>
      </div>
    </form>
  );
}

function Regenerar({ alTerminar }: { alTerminar: () => void }) {
  const G = S.regenerar;
  const [codigo, setCodigo] = useState('');
  const [codigos, setCodigos] = useState<readonly string[] | null>(null);
  const generar = useMutacion(
    (escrito: string) => pedir(cliente.POST('/api/v1/perfil/segundo-factor/codigos', { body: { codigo: escrito } })),
    { invalida: [CLAVE_SEGUNDO_FACTOR], alTerminar: (respuesta) => setCodigos(respuesta.codigos ?? []) },
  );

  if (codigos !== null) return <CodigosDeRecuperacion codigos={codigos} alTerminar={alTerminar} />;

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    generar.mutate(codigo.replace(/\s/g, ''));
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <p>{G.explicacion}</p>
      <Campo id="regenerar-codigo" etiqueta={G.codigo} inputMode="numeric" autoComplete="one-time-code" maxLength={7} value={codigo} onChange={(e) => setCodigo(e.target.value)} />
      {generar.error !== null && <Aviso>{generar.error.message}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={generar.isPending}>
          {G.confirmar}
        </Boton>
      </div>
    </form>
  );
}

type Abierto = 'activar' | 'desactivar' | 'regenerar' | null;

/** La tarjeta de Ajustes. */
export function TarjetaSegundoFactor() {
  const estado = useSegundoFactor();
  const [abierto, setAbierto] = useState<Abierto>(null);
  const cerrar = () => setAbierto(null);
  const datos = estado.data;

  return (
    <Tarjeta id="ajustes-segundo-factor" icono="candado" titulo={S.titulo} descripcion={S.descripcion}>
      {estado.isPending ? (
        <Esqueleto lineas={2} />
      ) : estado.error !== null ? (
        <Aviso>{estado.error.message}</Aviso>
      ) : (
        <div className="nx-columna">
          {datos?.activo === true ? (
            <>
              <div>
                <Insignia tono="exito">{S.activo}</Insignia>{' '}
                {datos.desde !== undefined && <span className="nx-sutil">{S.desde(fechaCorta(diaEnEmpresa(datos.desde)))}</span>}
              </div>
              <p className="nx-sutil">{S.quedan(datos.codigosDeRecuperacion ?? 0)}</p>
              {(datos.codigosDeRecuperacion ?? 0) < POCOS_CODIGOS && <Aviso>{S.pocos}</Aviso>}
            </>
          ) : (
            <p className="nx-sutil">{S.inactivo}</p>
          )}
          {datos?.obligatorio === true && datos.motivo !== undefined && <p>{S.obligatorio(datos.motivo)}</p>}
          <div className="nx-acciones-fila">
            {datos?.activo === true ? (
              <>
                <Boton variante="secundario" onClick={() => setAbierto('regenerar')}>
                  {S.codigosNuevos}
                </Boton>
                {datos.obligatorio !== true && (
                  <Boton variante="texto" onClick={() => setAbierto('desactivar')}>
                    {S.desactivar}
                  </Boton>
                )}
              </>
            ) : (
              <Boton onClick={() => setAbierto('activar')}>{S.activar}</Boton>
            )}
          </div>
        </div>
      )}

      <Dialogo abierto={abierto === 'activar'} titulo={S.activacion.titulo} alCerrar={cerrar} acciones={null}>
        {abierto === 'activar' && <ActivarSegundoFactor alTerminar={cerrar} alCancelar={cerrar} />}
      </Dialogo>
      <Dialogo abierto={abierto === 'desactivar'} titulo={S.desactivacion.titulo} alCerrar={cerrar} acciones={null}>
        {abierto === 'desactivar' && <Desactivar alTerminar={cerrar} />}
      </Dialogo>
      <Dialogo abierto={abierto === 'regenerar'} titulo={S.regenerar.titulo} alCerrar={cerrar} acciones={null}>
        {abierto === 'regenerar' && <Regenerar alTerminar={cerrar} />}
      </Dialogo>
    </Tarjeta>
  );
}

/**
 * Lo único que ve quien tiene que entrar con segundo factor y aún no lo tiene
 * (el operador, el Responsable del canal, o ADMIN y RRHH si su empresa lo
 * exige): el servidor no le deja hacer otra cosa. Al terminar, la sesión ya
 * no está limitada y la web sigue como siempre.
 */
export function SegundoFactorObligatorio() {
  const O = S.obligado;
  const estado = useSegundoFactor();
  const motivo = estado.data?.motivo;
  return (
    <MarcoDeAcceso>
      <div className="nx-tarjeta nx-formulario">
        <header>
          <h1>{O.titulo}</h1>
          <p className="nx-sutil">{motivo !== undefined ? O.explicacion(motivo) : O.sinMotivo}</p>
        </header>
        {/* Al terminar no hay nada que hacer aquí: renovar la sesión ya la ha liberado. */}
        <ActivarSegundoFactor alTerminar={() => undefined} />
        <Boton variante="texto" onClick={salir}>
          {O.salir}
        </Boton>
      </div>
    </MarcoDeAcceso>
  );
}
