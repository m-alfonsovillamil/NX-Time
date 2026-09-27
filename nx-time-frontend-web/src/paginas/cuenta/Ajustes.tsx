/**
 * Ajustes: el tema, la contraseña, cerrar las sesiones, descargar mis datos y
 * pedir que los borren.
 *
 * Lo que la app tiene aquí y la web no —el recordatorio de fichar, entrar con
 * huella y las notificaciones— se dice al final, con su porqué (ADR 029): un
 * navegador no puede avisar sin estar abierto, y la huella en la web serían
 * *passkeys*, que es otra fase.
 *
 * **Borrar mis datos es un derecho, no un botón más** (RGPD, ADR 016): se pide,
 * no se ejecuta; lo revisa RRHH. Por eso pide confirmación explicando qué se
 * pierde y qué no, y recuerda que conviene descargar una copia antes.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente, salir } from '../../api/cliente';
import { pedir, pedirOpcional, useMutacion } from '../../api/consultas';
import { AreaDeTexto, Aviso, Boton, Campo, Insignia, Selector, Tarjeta } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { Esqueleto } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { cuenta } from '../../i18n/es/cuenta';
import { descargar } from '../../util/descargar';
import { diaEnEspana, fechaCorta } from '../../util/fechas';
import { aplicarTema, temaGuardado, type Tema } from '../../util/tema';

const J = cuenta.ajustes;

const CLAVE_BORRADO = ['perfil', 'borrado'] as const;

function Apariencia() {
  const [tema, setTema] = useState<Tema>(temaGuardado());
  return (
    <Tarjeta titulo={J.apariencia}>
      <Selector
        id="tema"
        etiqueta={J.tema}
        value={tema}
        onChange={(e) => {
          const nuevo = e.target.value as Tema;
          setTema(nuevo);
          aplicarTema(nuevo);
        }}
        opciones={(['sistema', 'claro', 'oscuro'] as const).map((t) => ({ valor: t, texto: J.temas[t] }))}
      />
    </Tarjeta>
  );
}

function FormularioDeContrasena({ alTerminar }: { alTerminar: () => void }) {
  const C = J.contrasena;
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [repetida, setRepetida] = useState('');
  const [error, setError] = useState<string | null>(null);

  const cambiar = useMutacion(
    (cuerpo: { contrasenaAntigua: string; contrasenaNueva: string }) =>
      pedir(cliente.POST('/api/v1/usuario/cambiar-contrasena', { body: cuerpo })),
    { exito: C.cambiada, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (actual === '') return setError(C.faltaActual);
    if (nueva.length < 8) return setError(C.corta);
    if (nueva.length > 72) return setError(C.larga);
    if (nueva !== repetida) return setError(C.noCoinciden);
    setError(null);
    cambiar.mutate({ contrasenaAntigua: actual, contrasenaNueva: nueva });
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      <Campo id="contrasena-actual" etiqueta={C.actual} type="password" autoComplete="current-password" value={actual} onChange={(e) => setActual(e.target.value)} />
      <Campo id="contrasena-nueva" etiqueta={C.nueva} type="password" autoComplete="new-password" value={nueva} onChange={(e) => setNueva(e.target.value)} />
      <Campo id="contrasena-repetida" etiqueta={C.repetir} type="password" autoComplete="new-password" value={repetida} onChange={(e) => setRepetida(e.target.value)} />
      {(error ?? cambiar.error?.message) && <Aviso>{error ?? cambiar.error?.message}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" ocupado={cambiar.isPending}>
          {C.guardar}
        </Boton>
      </div>
    </form>
  );
}

function Cuenta() {
  const [cambiando, setCambiando] = useState(false);
  const [cerrando, setCerrando] = useState(false);

  // Revoca todas las sesiones, también esta: después, aquí también hay que salir.
  const cerrarTodas = useMutacion(() => pedir(cliente.POST('/api/v1/usuario/cerrar-sesiones', {})), {
    alTerminar: () => salir(),
  });

  return (
    <Tarjeta titulo={J.cuenta}>
      <div className="nx-columna">
        <div>
          <Boton variante="secundario" onClick={() => setCambiando(true)}>
            {J.contrasena.boton}
          </Boton>
        </div>
        <div>
          <Boton variante="secundario" onClick={() => setCerrando(true)}>
            {J.cerrarTodas.boton}
          </Boton>
          <p className="nx-sutil">{J.cerrarTodas.detalle}</p>
        </div>
      </div>

      <Dialogo abierto={cambiando} titulo={J.contrasena.titulo} alCerrar={() => setCambiando(false)} acciones={null}>
        {cambiando && <FormularioDeContrasena alTerminar={() => setCambiando(false)} />}
      </Dialogo>

      <Dialogo
        abierto={cerrando}
        titulo={J.cerrarTodas.titulo}
        alCerrar={() => setCerrando(false)}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setCerrando(false)}>
              {T.app.cancelar}
            </Boton>
            <Boton variante="peligro" ocupado={cerrarTodas.isPending} onClick={() => cerrarTodas.mutate(undefined)}>
              {J.cerrarTodas.confirmar}
            </Boton>
          </>
        }
      >
        <p>{J.cerrarTodas.detalle}</p>
        {cerrarTodas.error !== null && <Aviso>{cerrarTodas.error.message}</Aviso>}
      </Dialogo>
    </Tarjeta>
  );
}

function MisDatos() {
  const [error, setError] = useState<string | null>(null);
  const [bajando, setBajando] = useState<'pdf' | 'json' | null>(null);

  async function bajar(formato: 'pdf' | 'json') {
    setError(null);
    setBajando(formato);
    try {
      if (formato === 'pdf') {
        await descargar(cliente.GET('/api/v1/perfil/mis-datos/pdf', { parseAs: 'blob' }), 'mis-datos-nx-time.pdf');
      } else {
        await descargar(cliente.GET('/api/v1/perfil/mis-datos', { parseAs: 'blob' }), 'mis-datos-nx-time.json');
      }
    } catch (fallo) {
      setError(fallo instanceof Error ? fallo.message : T.errores.descarga);
    } finally {
      setBajando(null);
    }
  }

  return (
    <div>
      <h3 className="nx-subtitulo">{J.misDatos.titulo}</h3>
      <p className="nx-sutil">{J.misDatos.detalle}</p>
      <div className="nx-acciones-fila">
        <Boton variante="secundario" ocupado={bajando === 'pdf'} onClick={() => void bajar('pdf')}>
          {J.misDatos.pdf}
        </Boton>
        <Boton variante="secundario" ocupado={bajando === 'json'} onClick={() => void bajar('json')}>
          {J.misDatos.json}
        </Boton>
      </div>
      {error !== null && <Aviso>{error}</Aviso>}
    </div>
  );
}

function FormularioDeBorrado({ alTerminar }: { alTerminar: () => void }) {
  const B = J.borrado;
  const [motivo, setMotivo] = useState('');
  const pedirBorrado = useMutacion(
    (cuerpo: { motivo?: string }) => pedir(cliente.POST('/api/v1/perfil/borrado', { body: cuerpo })),
    { invalida: [CLAVE_BORRADO], exito: B.enviada, alTerminar },
  );

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    pedirBorrado.mutate(motivo.trim() !== '' ? { motivo: motivo.trim() } : {});
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      {B.confirmarDetalle.map((parrafo) => (
        <p key={parrafo}>{parrafo}</p>
      ))}
      <AreaDeTexto id="borrado-motivo" etiqueta={B.motivo} maxLength={500} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
      {pedirBorrado.error !== null && <Aviso>{pedirBorrado.error.message}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alTerminar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" variante="peligro" ocupado={pedirBorrado.isPending}>
          {B.pedir}
        </Boton>
      </div>
    </form>
  );
}

function Borrado() {
  const B = J.borrado;
  const [pidiendo, setPidiendo] = useState(false);
  const solicitud = useQuery({
    queryKey: CLAVE_BORRADO,
    // 204: nunca pidió ninguno. No es un error.
    queryFn: () => pedirOpcional(cliente.GET('/api/v1/perfil/borrado', {})),
  });
  const retirar = useMutacion(() => pedir(cliente.POST('/api/v1/perfil/borrado/cancelar', {})), {
    invalida: [CLAVE_BORRADO],
    exito: B.retirada,
  });

  const estado = solicitud.data?.estado;
  const pendiente = estado === 'PENDIENTE';

  return (
    <div>
      <h3 className="nx-subtitulo">{B.titulo}</h3>
      <p className="nx-sutil">{B.detalle}</p>
      {solicitud.isPending ? (
        <Esqueleto lineas={1} />
      ) : pendiente ? (
        <>
          <p>
            <Insignia tono="aviso">{B.pendiente(fechaCorta(diaEnEspana(solicitud.data?.creadaEn ?? '')))}</Insignia>
          </p>
          <Boton variante="secundario" ocupado={retirar.isPending} onClick={() => retirar.mutate(undefined)}>
            {B.retirar}
          </Boton>
        </>
      ) : (
        <>
          {estado === 'RECHAZADA' && solicitud.data?.comentarioResolucion && (
            <Aviso>{B.rechazado(solicitud.data.comentarioResolucion)}</Aviso>
          )}
          <Boton variante="peligro" onClick={() => setPidiendo(true)}>
            {B.pedir}
          </Boton>
        </>
      )}
      {retirar.error !== null && <Aviso>{retirar.error.message}</Aviso>}

      <Dialogo abierto={pidiendo} titulo={B.confirmarTitulo} alCerrar={() => setPidiendo(false)} acciones={null}>
        {pidiendo && <FormularioDeBorrado alTerminar={() => setPidiendo(false)} />}
      </Dialogo>
    </div>
  );
}

export function Ajustes() {
  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{J.titulo}</h1>
      </header>
      <Apariencia />
      <Cuenta />
      <Tarjeta titulo={J.privacidad}>
        <div className="nx-columna">
          <MisDatos />
          <Borrado />
        </div>
      </Tarjeta>
      <Tarjeta titulo={J.enLaApp}>
        <p className="nx-sutil">{J.enLaAppDetalle}</p>
      </Tarjeta>
    </div>
  );
}
