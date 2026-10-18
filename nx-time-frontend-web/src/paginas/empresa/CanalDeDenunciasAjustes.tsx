/**
 * El canal de denuncias de la empresa, en sus ajustes (plan J, fase J6; ADR
 * 048): quién es el Responsable del Sistema Interno de Información y cómo se
 * comunica algo de palabra.
 *
 * Designar al Responsable cambia quién instruye: desde ese momento solo él ve
 * el canal, y los ADMIN dejan de verlo. Por eso, al guardar, se vuelven a pedir
 * los permisos de la sesión: el menú tiene que cambiar en el acto, no a los 15
 * minutos.
 *
 * La empresa tiene que comunicar el nombramiento y el cese a la Autoridad
 * Independiente de Protección del Informante en 10 días hábiles. La aplicación
 * no puede hacerlo por ella; aquí se le recuerda, con el enlace al formulario.
 */

import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { cliente } from '../../api/cliente';
import { pedir, useMutacion } from '../../api/consultas';
import { abrirSesion, sesionActual } from '../../api/sesion';
import type { components } from '../../api/schema';
import { AreaDeTexto, Aviso, Boton, Selector, Tarjeta } from '../../componentes/Basicos';
import { EstadoDeConsulta, Esqueleto } from '../../componentes/Estados';
import { empresa } from '../../i18n/es/empresa';
import { fechaCorta, diaEnEmpresa } from '../../util/fechas';

const A = empresa.canal;

type Ajustes = components['schemas']['ComplaintChannelSettingsResponse'];
type Persona = components['schemas']['SimpleEmployeeDTO'];

const CLAVE = ['empresa', 'canal-denuncias'] as const;

function Formulario({ ajustes, personas }: { ajustes: Ajustes; personas: readonly Persona[] }) {
  const [responsable, setResponsable] = useState(ajustes.responsableId !== undefined ? String(ajustes.responsableId) : '');
  const [via, setVia] = useState(ajustes.viaVerbal ?? '');

  const guardar = useMutacion(
    () =>
      pedir(
        cliente.PUT('/api/v1/empresa/canal-denuncias', {
          body: { ...(responsable !== '' ? { responsableId: Number(responsable) } : {}), viaVerbal: via.trim() },
        }),
      ),
    {
      invalida: [CLAVE, ['canal-denuncias']],
      exito: A.guardado,
      alTerminar: async () => {
        // Quién instruye ha podido cambiar, también para quien guarda.
        const perfil = await pedir(cliente.GET('/api/v1/perfil', {}));
        const sesion = sesionActual();
        if (sesion !== null && perfil.authorities !== undefined) abrirSesion({ ...sesion, authorities: perfil.authorities });
      },
    },
  );

  const opciones = [
    { valor: '', texto: A.nadie },
    ...personas
      .filter((p) => p.activo === true && p.id !== undefined)
      .map((p) => ({ valor: String(p.id), texto: p.nombre ?? p.email ?? String(p.id) })),
  ];

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    guardar.mutate();
  }

  return (
    <form className="nx-formulario nx-formulario--libre" onSubmit={enviar} noValidate>
      <Selector
        id="canal-responsable"
        etiqueta={A.responsable}
        ayuda={A.responsableAyuda}
        value={responsable}
        onChange={(e) => setResponsable(e.target.value)}
        opciones={opciones}
      />
      {ajustes.desde && <p className="nx-sutil">{A.desde(fechaCorta(diaEnEmpresa(ajustes.desde)))}</p>}
      <p className="nx-sutil">
        {A.autoridad}{' '}
        <a className="nx-enlace" href={A.autoridadUrl} target="_blank" rel="noopener noreferrer">
          {A.autoridadEnlace}
        </a>
      </p>
      <AreaDeTexto
        id="canal-via-verbal"
        etiqueta={A.viaVerbal}
        ayuda={A.viaVerbalAyuda}
        maxLength={500}
        rows={3}
        value={via}
        onChange={(e) => setVia(e.target.value)}
      />
      <div className="nx-acciones-fila">
        <Boton type="submit" ocupado={guardar.isPending}>
          {A.guardar}
        </Boton>
      </div>
      {guardar.error !== null && <Aviso>{guardar.error.message}</Aviso>}
    </form>
  );
}

export function CanalDeDenunciasAjustes() {
  const ajustes = useQuery({ queryKey: CLAVE, queryFn: () => pedir(cliente.GET('/api/v1/empresa/canal-denuncias', {})) });
  const personas = useQuery({ queryKey: ['plantilla', 'canal'], queryFn: () => pedir(cliente.GET('/api/v1/gestor/plantilla', {})) });

  return (
    <Tarjeta titulo={A.titulo} icono="mazo" descripcion={A.explicacion}>
      <EstadoDeConsulta consulta={ajustes} cargando={<Esqueleto lineas={3} />}>
        {(datos) => (
          <EstadoDeConsulta consulta={personas} cargando={<Esqueleto lineas={3} />}>
            {(lista) => (
              <Formulario key={`${datos.responsableId ?? ''}|${datos.viaVerbal ?? ''}`} ajustes={datos} personas={lista} />
            )}
          </EstadoDeConsulta>
        )}
      </EstadoDeConsulta>
    </Tarjeta>
  );
}
