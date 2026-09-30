/**
 * Lo de cada persona para fichar en un kiosco (ADR 033), en su perfil: su PIN
 * y su tarjeta.
 *
 * El PIN lo elige ella y no se enseña nunca, ni a quien administra (ADR 014).
 * La tarjeta se enseña bajo demanda, no al abrir el perfil: pedirla la crea si
 * no existía, y con ella se puede fichar en su nombre.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Tarjeta } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { T } from '../../i18n/es';
import { kiosco } from '../../i18n/es/kiosco';
import { hora } from '../../util/fechas';

const P = kiosco.perfil;

type TarjetaDeKiosco = components['schemas']['KioskCard'];

const CLAVE_ESTADO = ['perfil', 'kiosco'] as const;

/** El SVG del QR como imagen: la CSP de la web admite `data:` en `img-src`. */
export function srcDeTarjeta(svg: string): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function descargarTarjeta(tarjeta: TarjetaDeKiosco) {
  const url = URL.createObjectURL(new Blob([tarjeta.svg ?? ''], { type: 'image/svg+xml' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = 'tarjeta-kiosco-nxtime.svg';
  enlace.click();
  URL.revokeObjectURL(url);
}

function Pin({ tienePin }: { tienePin: boolean }) {
  const [pin, setPin] = useState('');
  const [invalido, setInvalido] = useState(false);
  const guardar = useMutacion(
    (nuevo: string) => pedir(cliente.PUT('/api/v1/perfil/kiosco/pin', { body: { pin: nuevo } })),
    { invalida: [CLAVE_ESTADO], exito: P.pinGuardado, alTerminar: () => setPin('') },
  );
  const quitar = useMutacion(() => pedir(cliente.DELETE('/api/v1/perfil/kiosco/pin', {})), {
    invalida: [CLAVE_ESTADO],
    exito: P.pinQuitado,
  });

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!/^\d{4,6}$/.test(pin)) return setInvalido(true);
    setInvalido(false);
    guardar.mutate(pin);
  }

  return (
    <form className="nx-formulario" onSubmit={enviar} noValidate>
      <Campo
        id="kiosco-pin"
        etiqueta={P.pin}
        ayuda={P.pinAyuda}
        type="password"
        inputMode="numeric"
        autoComplete="off"
        maxLength={6}
        error={invalido ? P.pinInvalido : undefined}
        value={pin}
        onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
      />
      <div className="nx-acciones-fila">
        <Boton type="submit" ocupado={guardar.isPending}>
          {tienePin ? P.cambiarPin : P.guardarPin}
        </Boton>
        {tienePin && (
          <Boton variante="texto" ocupado={quitar.isPending} onClick={() => quitar.mutate()}>
            {P.quitarPin}
          </Boton>
        )}
      </div>
      {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
      {quitar.error !== null && <Aviso>{quitar.error.message}</Aviso>}
    </form>
  );
}

function SuTarjeta() {
  const [visible, setVisible] = useState(false);
  const [regenerando, setRegenerando] = useState(false);
  const tarjeta = useQuery({
    queryKey: [...CLAVE_ESTADO, 'tarjeta'],
    queryFn: () => pedir(cliente.GET('/api/v1/perfil/kiosco/tarjeta', {})),
    enabled: visible,
  });
  const regenerar = useMutacion(() => pedir(cliente.POST('/api/v1/perfil/kiosco/tarjeta', {})), {
    invalida: [CLAVE_ESTADO],
    exito: P.regenerada,
    alTerminar: () => setRegenerando(false),
  });

  if (!visible) {
    return (
      <Boton variante="secundario" onClick={() => setVisible(true)}>
        {P.verTarjeta}
      </Boton>
    );
  }

  return (
    <div className="nx-formulario">
      {tarjeta.data !== undefined && (
        <figure className="nx-tarjeta-kiosco">
          <img src={srcDeTarjeta(tarjeta.data.svg ?? '')} alt={P.tarjeta} />
          <figcaption>{tarjeta.data.nombre}</figcaption>
        </figure>
      )}
      {tarjeta.error !== null && <Aviso>{tarjeta.error.message}</Aviso>}
      <p className="nx-sutil">{P.tarjetaAyuda}</p>
      <div className="nx-acciones-fila">
        <Boton variante="secundario" disabled={tarjeta.data === undefined} onClick={() => tarjeta.data && descargarTarjeta(tarjeta.data)}>
          {P.descargar}
        </Boton>
        <Boton variante="texto" onClick={() => setRegenerando(true)}>
          {P.regenerar}
        </Boton>
        <Boton variante="texto" onClick={() => setVisible(false)}>
          {P.ocultarTarjeta}
        </Boton>
      </div>
      <Dialogo
        abierto={regenerando}
        titulo={P.regenerarTitulo}
        alCerrar={() => {
          regenerar.reset();
          setRegenerando(false);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setRegenerando(false)}>
              {T.app.cancelar}
            </Boton>
            <Boton variante="peligro" ocupado={regenerar.isPending} onClick={() => regenerar.mutate()}>
              {P.regenerar}
            </Boton>
          </>
        }
      >
        <p>{P.regenerarTexto}</p>
        {regenerar.error !== null && <Aviso>{regenerar.error.message}</Aviso>}
      </Dialogo>
    </div>
  );
}

export function FicharEnKiosco() {
  const estado = useQuery({
    queryKey: CLAVE_ESTADO,
    queryFn: () => pedir(cliente.GET('/api/v1/perfil/kiosco', {})),
  });
  const e = estado.data;
  const bloqueado = e?.pinBloqueadoHasta !== undefined && new Date(e.pinBloqueadoHasta) > new Date();

  return (
    <Tarjeta titulo={P.titulo}>
      <p className="nx-sutil">{P.explicacion}</p>
      {e !== undefined && <p>{e.tienePin ? P.tienePin : P.sinPin}</p>}
      {bloqueado && e?.pinBloqueadoHasta !== undefined && <Aviso>{P.bloqueado(hora(e.pinBloqueadoHasta))}</Aviso>}
      <Pin tienePin={e?.tienePin ?? false} />
      <h3>{P.tarjeta}</h3>
      <SuTarjeta />
    </Tarjeta>
  );
}
