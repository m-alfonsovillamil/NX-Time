/**
 * Los ajustes de la empresa: el nombre y la zona horaria (fase Z2, ADR 032).
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

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { abrirSesion, sesionActual } from '../../api/sesion';
import type { components } from '../../api/schema';
import { Aviso, Boton, Campo, Selector } from '../../componentes/Basicos';
import { Dialogo } from '../../componentes/Dialogo';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { T } from '../../i18n/es';
import { empresa } from '../../i18n/es/empresa';

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
  const [falta, setFalta] = useState(false);
  const [confirmando, setConfirmando] = useState(false);

  const guardar = useMutacion(
    (cuerpo: { nombre: string; zonaHoraria: string }) => pedir(cliente.PUT('/api/v1/empresa/ajustes', { body: cuerpo })),
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
    guardar.mutate({ nombre: nombre.trim(), zonaHoraria: zona });
  }

  return (
    <>
      <form className="nx-tarjeta nx-formulario" onSubmit={enviar} noValidate>
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
        <div className="nx-acciones-fila">
          <Boton type="submit" ocupado={guardar.isPending && !confirmando}>
            {A.guardar}
          </Boton>
        </div>
        {guardar.error !== null && !confirmando && <Aviso>{guardar.error.message}</Aviso>}
      </form>

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
              onClick={() => guardar.mutate({ nombre: nombre.trim(), zonaHoraria: zona })}
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

export function AjustesEmpresa() {
  const ajustes = useQuery({
    queryKey: CLAVE_AJUSTES,
    queryFn: () => pedir(cliente.GET('/api/v1/empresa/ajustes', {})),
  });

  return (
    <div className="nx-pagina">
      <header className="nx-cabecera">
        <h1>{A.titulo}</h1>
      </header>
      <EstadoDeConsulta consulta={ajustes} cargando={<Esqueleto lineas={3} />}>
        {(datos) => <Formulario key={`${datos.nombre}|${datos.zonaHoraria}`} ajustes={datos} />}
      </EstadoDeConsulta>
    </div>
  );
}
