/**
 * Quién está trabajando ahora y quién no ha fichado hoy (plan N, paso N7).
 *
 * Una sección del panel de gestión, para quien puede ver el historial del
 * equipo (`fichaje:leer:equipo`). Lo que pide atención sale primero: quien ya
 * debería haber entrado según su cuadrante y no lo ha hecho, y quien no ha
 * fichado. El orden lo pone el servidor. Se refresca solo cada minuto: es una
 * foto del momento, y la que se dejó abierta a las nueve no sirve a las diez.
 */

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { cliente } from '../../api/cliente';
import { pedir } from '../../api/consultas';
import type { components } from '../../api/schema';
import { Boton, Insignia, type Tono } from '../../componentes/Basicos';
import { Cifra, Cifras } from '../../componentes/Cifra';
import { EstadoDeConsulta, Esqueleto, Vacio } from '../../componentes/Estados';
import { Tabla } from '../../componentes/Tabla';
import { gestion } from '../../i18n/es/gestion';
import { hora, minutos } from '../../util/fechas';

const A = gestion.ahora;

type Persona = components['schemas']['PersonaAhora'];
type Estado = NonNullable<Persona['estado']>;

const TONO: Record<Estado, Tono> = {
  TRABAJANDO: 'exito',
  EN_PAUSA: 'aviso',
  TERMINADA: 'neutro',
  SIN_FICHAR: 'info',
  NO_LABORABLE: 'neutro',
};

export const CLAVE_EQUIPO_AHORA = ['equipo-ahora'] as const;

function nota(p: Persona): string {
  if (p.conRetraso && p.entradaPrevista) return A.retraso(p.entradaPrevista);
  if (p.motivo) return p.motivo;
  if (p.estado === 'SIN_FICHAR' && p.entradaPrevista) return A.entraA(p.entradaPrevista);
  if (p.modalidad === 'A_DISTANCIA') return A.aDistancia;
  return '';
}

/**
 * Quien está o falta por fichar: lo que se viene a mirar. Quien ya terminó o
 * hoy no tenía que venir solo cuenta en las cifras; con 120 personas, listarlas
 * a todas llenaba el panel de nombres que no piden nada (revisión del 28/11/2026).
 */
function aLaVista(p: Persona): boolean {
  return p.estado === 'TRABAJANDO' || p.estado === 'EN_PAUSA' || p.estado === 'SIN_FICHAR';
}

export function EquipoAhora() {
  const [todos, setTodos] = useState(false);
  const consulta = useQuery({
    queryKey: CLAVE_EQUIPO_AHORA,
    queryFn: () => pedir(cliente.GET('/api/v1/gestor/equipo-ahora', {})),
    refetchInterval: 60_000,
  });

  return (
    <section className="nx-tarjeta" aria-labelledby="panel-ahora">
      <h2 id="panel-ahora">{A.titulo}</h2>
      <EstadoDeConsulta consulta={consulta} cargando={<Esqueleto forma="recuadros" lineas={4} />}>
        {(foto) => {
          const personas = foto.personas ?? [];
          if (personas.length === 0) return <Vacio titulo={A.vacio} />;
          const vistas = todos ? personas : personas.filter(aLaVista);
          return (
            <>
              <Cifras etiqueta={A.titulo}>
                <Cifra icono="reloj" etiqueta={A.trabajando} valor={foto.trabajando ?? 0} />
                <Cifra icono="lapso" etiqueta={A.enPausa} valor={foto.enPausa ?? 0} />
                <Cifra
                  icono="incidencia"
                  etiqueta={A.sinFichar}
                  valor={foto.sinFichar ?? 0}
                  {...((foto.conRetraso ?? 0) > 0 ? { detalle: A.deEllosConRetraso(foto.conRetraso ?? 0), tono: 'destacada' as const } : {})}
                />
                <Cifra icono="hecho" etiqueta={A.terminada} valor={foto.terminada ?? 0} />
                <Cifra icono="calendario" etiqueta={A.noLaborable} valor={foto.noLaborable ?? 0} />
              </Cifras>
              {vistas.length === 0 ? (
                <p className="nx-sutil">{A.nadieALaVista}</p>
              ) : (
                <Tabla
                  titulo={A.tabla}
                  compacta
                  filas={vistas}
                  claveDeFila={(p) => p.usuarioId ?? 0}
                  columnas={[
                    { clave: 'nombre', cabecera: A.persona, celda: (p) => p.nombre ?? '', enMovil: 'cabecera' },
                    {
                      clave: 'estado',
                      cabecera: A.estado,
                      celda: (p) => (p.estado ? <Insignia tono={p.conRetraso ? 'error' : TONO[p.estado]}>{A.estados[p.estado]}</Insignia> : null),
                    },
                    { clave: 'desde', cabecera: A.desde, celda: (p) => hora(p.desde) },
                    { clave: 'hoy', cabecera: A.hoy, celda: (p) => ((p.minutosHoy ?? 0) > 0 ? minutos(p.minutosHoy ?? 0) : ''), numerica: true },
                    { clave: 'nota', cabecera: A.nota, celda: nota },
                  ]}
                />
              )}
              {personas.some((p) => !aLaVista(p)) && (
                <Boton variante="texto" onClick={() => setTodos((t) => !t)}>
                  {todos ? A.verAtencion : A.verTodos(personas.length)}
                </Boton>
              )}
            </>
          );
        }}
      </EstadoDeConsulta>
    </section>
  );
}
