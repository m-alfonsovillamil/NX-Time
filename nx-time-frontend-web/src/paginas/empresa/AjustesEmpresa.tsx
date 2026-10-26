/**
 * Los ajustes de la empresa: el nombre, la zona horaria (fase Z2, ADR 032) y
 * el contacto de privacidad que ve la plantilla (ADR 047), y si se exige el
 * segundo factor a ADMIN y RRHH (ADR 051).
 *
 * Solo la ve ADMIN (`empresa:configurar`). La zona decide a qué día pertenece
 * cada fichaje de todo el histórico, así que cambiarla pide confirmación: una
 * jornada nocturna puede cambiar de día, y si eso mueve un mes ya firmado la
 * firma se anula. La respuesta dice cuántas cayeron, y se cuenta en el aviso.
 *
 * Al guardar una zona nueva se aplica en el acto a esta sesión y se vuelve a
 * pedir todo lo que hay en caché: todo lo que enseña días se ha movido.
 */

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { abrirSesion, sesionActual } from '../../api/sesion';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Selector, Tarjeta } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { empresa } from '../../i18n/es/empresa';
import { BajaDeEmpresa } from './BajaDeEmpresa';
import { CanalDeDenunciasAjustes } from './CanalDeDenunciasAjustes';
import { Kioscos } from './Kioscos';

const A = empresa.ajustes;

type Ajustes = components['schemas']['CompanySettingsResponse'];

const CLAVE_AJUSTES = ['empresa', 'ajustes'] as const;

const PENINSULA = 'Europe/Madrid';
const CANARIAS = 'Atlantic/Canary';

/**
 * Las dos de España primero, con nombre de persona, y después todas las que
 * conoce el navegador. Si el navegador no sabe listarlas, al menos esas dos y
 * la que tenga ya la empresa.
 */
function opcionesDeZona(actual: string) {
  const todas = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];
  const otras = [...new Set([...todas, actual])]
    .filter((z) => z !== PENINSULA && z !== CANARIAS)
    .sort();
  // Textos cortos a propósito: Safari da al selector el ancho de su opción más
  // larga, y en un iPhone la página se salía por la derecha.
  return [
    { valor: PENINSULA, texto: A.peninsula },
    { valor: CANARIAS, texto: A.canarias },
    ...otras.map((z) => ({ valor: z, texto: z.replaceAll('_', ' '), grupo: A.otras })),
  ];
}

function Formulario({ ajustes }: { ajustes: Ajustes }) {
  const clienteDeConsultas = useQueryClient();
  const [nombre, setNombre] = useState(ajustes.nombre ?? '');
  const [zona, setZona] = useState(ajustes.zonaHoraria ?? PENINSULA);
  const [contacto, setContacto] = useState(ajustes.contactoPrivacidad ?? '');
  const [falta, setFalta] = useState(false);
  const [confirmando, setConfirmando] = useState(false);

  const guardar = useMutacion(
    (cuerpo: { nombre: string; zonaHoraria: string; contactoPrivacidad: string }) =>
      pedir(cliente.PUT('/api/v1/empresa/ajustes', { body: cuerpo })),
    {
      invalida: [CLAVE_AJUSTES],
      exito: (r) => ((r.firmasInvalidadas ?? 0) > 0 ? A.firmasAnuladas(r.firmasInvalidadas ?? 0) : A.guardado),
      alTerminar: (r) => {
        setConfirmando(false);
        const sesion = sesionActual();
        if (sesion !== null && r.zonaHoraria !== sesion.zonaHoraria) {
          abrirSesion({ ...sesion, zonaHoraria: r.zonaHoraria });
          // Todo lo que enseña días se ha movido: se vuelve a pedir.
          void clienteDeConsultas.invalidateQueries();
        }
      },
    },
  );

  const cambiaLaZona = zona !== ajustes.zonaHoraria;

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (nombre.trim() === '') return setFalta(true);
    setFalta(false);
    if (cambiaLaZona) return setConfirmando(true);
    guardar.mutate(cuerpo());
  }

  function cuerpo() {
    // Vacío lo quita: el servidor lo guarda como «sin contacto».
    return { nombre: nombre.trim(), zonaHoraria: zona, contactoPrivacidad: contacto.trim() };
  }

  return (
    <>
      <Tarjeta titulo={A.datos} icono="empresa" descripcion={A.datosTexto}>
        <form className="nx-formulario nx-formulario--libre" onSubmit={enviar} noValidate>
        <Campo
          id="empresa-nombre"
          etiqueta={A.nombre}
          maxLength={255}
          error={falta ? A.nombreVacio : undefined}
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
        />
        <Selector
          id="empresa-zona"
          etiqueta={A.zona}
          ayuda={A.zonaAyuda}
          value={zona}
          onChange={(e) => setZona(e.target.value)}
          opciones={opcionesDeZona(ajustes.zonaHoraria ?? PENINSULA)}
        />
        <Campo
          id="empresa-contacto-privacidad"
          etiqueta={A.contacto}
          ayuda={A.contactoAyuda}
          maxLength={255}
          inputMode="email"
          autoComplete="off"
          placeholder="privacidad@tuempresa.es"
          value={contacto}
          onChange={(e) => setContacto(e.target.value)}
        />
        <div className="nx-acciones-fila">
          <Boton type="submit" ocupado={guardar.isPending && !confirmando}>
            {A.guardar}
          </Boton>
        </div>
        {guardar.error !== null && !confirmando && <Aviso>{guardar.error.message}</Aviso>}
        </form>
      </Tarjeta>

      <Dialogo
        abierto={confirmando}
        titulo={A.confirmarTitulo}
        alCerrar={() => {
          guardar.reset();
          setConfirmando(false);
        }}
        acciones={
          <>
            <Boton variante="texto" onClick={() => setConfirmando(false)}>
              {T.app.cancelar}
            </Boton>
            <Boton
              variante="peligro"
              ocupado={guardar.isPending}
              onClick={() => guardar.mutate(cuerpo())}
            >
              {A.confirmar}
            </Boton>
          </>
        }
      >
        <p>{A.confirmarTexto(zona)}</p>
        {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
      </Dialogo>
    </>
  );
}

/**
 * Exigir el segundo factor a ADMIN y RRHH (ADR 051). Aparte del formulario de
 * arriba, con su propio botón: no tiene nada que ver con el nombre ni con la
 * zona, y cambiarlo no debe pasar por la confirmación de la zona. El servidor
 * guarda los ajustes enteros, así que se mandan los guardados.
 */
function ExigirSegundoFactor({ ajustes }: { ajustes: Ajustes }) {
  const S = A.segundoFactor;
  const [exigir, setExigir] = useState(ajustes.exigirSegundoFactor === true);
  const guardar = useMutacion(
    (valor: boolean) =>
      pedir(
        cliente.PUT('/api/v1/empresa/ajustes', {
          body: {
            nombre: ajustes.nombre ?? '',
            zonaHoraria: ajustes.zonaHoraria ?? PENINSULA,
            contactoPrivacidad: ajustes.contactoPrivacidad ?? '',
            exigirSegundoFactor: valor,
          },
        }),
      ),
    { invalida: [CLAVE_AJUSTES], exito: (r) => (r.exigirSegundoFactor === true ? S.exigido : S.noExigido) },
  );

  return (
    <Tarjeta titulo={S.titulo} icono="candado" descripcion={S.texto}>
      <div className="nx-columna">
        <label className="nx-casilla">
          <input type="checkbox" checked={exigir} onChange={(e) => setExigir(e.target.checked)} />
          <span>{S.exigir}</span>
        </label>
        <div>
          <Boton
            variante="secundario"
            ocupado={guardar.isPending}
            disabled={exigir === (ajustes.exigirSegundoFactor === true)}
            onClick={() => guardar.mutate(exigir)}
          >
            {S.guardar}
          </Boton>
        </div>
        {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
      </div>
    </Tarjeta>
  );
}

/**
 * Preguntar presencial o a distancia al entrar (ADR 055). Como el segundo
 * factor: su propio botón, y se mandan los ajustes guardados.
 */
function PreguntarModalidad({ ajustes }: { ajustes: Ajustes }) {
  const M = A.modalidad;
  const [preguntar, setPreguntar] = useState(ajustes.preguntarModalidad === true);
  const guardar = useMutacion(
    (valor: boolean) =>
      pedir(
        cliente.PUT('/api/v1/empresa/ajustes', {
          body: {
            nombre: ajustes.nombre ?? '',
            zonaHoraria: ajustes.zonaHoraria ?? PENINSULA,
            contactoPrivacidad: ajustes.contactoPrivacidad ?? '',
            preguntarModalidad: valor,
          },
        }),
      ),
    { invalida: [CLAVE_AJUSTES], exito: (r) => (r.preguntarModalidad === true ? M.preguntada : M.noPreguntada) },
  );

  return (
    <Tarjeta titulo={M.titulo} icono="maletin" descripcion={M.texto}>
      <div className="nx-columna">
        <label className="nx-casilla">
          <input type="checkbox" checked={preguntar} onChange={(e) => setPreguntar(e.target.checked)} />
          <span>{M.preguntar}</span>
        </label>
        <div>
          <Boton
            variante="secundario"
            ocupado={guardar.isPending}
            disabled={preguntar === (ajustes.preguntarModalidad === true)}
            onClick={() => guardar.mutate(preguntar)}
          >
            {M.guardar}
          </Boton>
        </div>
        {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
      </div>
    </Tarjeta>
  );
}

export function AjustesEmpresa() {
  const ajustes = useQuery({
    queryKey: CLAVE_AJUSTES,
    queryFn: () => pedir(cliente.GET('/api/v1/empresa/ajustes', {})),
  });

  return (
    <div className="nx-pagina">
      <CabeceraDePagina titulo={A.titulo} descripcion={A.explicacion} />
      {/* Dos mitades: los datos de la empresa y, al lado, sus kioscos. Cada una a su altura. */}
      <div className="nx-composicion nx-composicion--mitades nx-composicion--arriba">
        <div className="nx-columna">
          <EstadoDeConsulta consulta={ajustes} cargando={<Esqueleto lineas={3} />}>
            {(datos) => (
              <Formulario key={`${datos.nombre}|${datos.zonaHoraria}|${datos.contactoPrivacidad ?? ''}`} ajustes={datos} />
            )}
          </EstadoDeConsulta>
          {ajustes.data !== undefined && (
            <ExigirSegundoFactor key={String(ajustes.data.exigirSegundoFactor)} ajustes={ajustes.data} />
          )}
          {ajustes.data !== undefined && (
            <PreguntarModalidad key={String(ajustes.data.preguntarModalidad)} ajustes={ajustes.data} />
          )}
          <CanalDeDenunciasAjustes />
          {ajustes.data?.nombre && <BajaDeEmpresa nombreEmpresa={ajustes.data.nombre} />}
          <Tarjeta titulo={A.accesosTitulo} icono="candado" descripcion={A.accesosTexto}>
            <Link className="nx-boton nx-boton--secundario nx-boton--enlace" to="/accesos-a-datos">
              {A.verAccesos}
            </Link>
          </Tarjeta>
        </div>
        <Kioscos />
      </div>
    </div>
  );
}
