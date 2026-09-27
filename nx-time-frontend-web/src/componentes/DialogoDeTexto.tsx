/**
 * Un diálogo que pide un texto obligatorio: explicar una incidencia, rechazar
 * algo con su motivo, no estar de acuerdo con una corrección…
 *
 * Todos se comportan igual: el texto vacío no se manda (se avisa aquí mismo) y
 * el error del servidor se lee dentro del diálogo, que sigue abierto para
 * poder corregir. El formulario se monta al abrir, así que cada vez empieza
 * desde `inicial` y no desde lo que se escribió la vez anterior.
 */

import { useState, type FormEvent } from 'react';

import { T } from '../i18n/es';
import { AreaDeTexto, Aviso, Boton } from './Basicos';
import { Dialogo } from './Dialogo';

interface Props {
  abierto: boolean;
  titulo: string;
  ayuda?: string;
  etiqueta: string;
  /** Lo que se dice si se intenta mandar vacío. */
  vacio: string;
  boton: string;
  ocupado: boolean;
  error: string | null;
  alEnviar: (texto: string) => void;
  alCerrar: () => void;
  inicial?: string;
  maxLength?: number;
  /** Botón de confirmar en rojo: rechazar, no estar de acuerdo. */
  peligro?: boolean;
}

export function DialogoDeTexto({ abierto, titulo, alCerrar, ...resto }: Props) {
  return (
    <Dialogo abierto={abierto} titulo={titulo} alCerrar={alCerrar} acciones={null}>
      {abierto && <FormularioDeTexto alCerrar={alCerrar} {...resto} />}
    </Dialogo>
  );
}

function FormularioDeTexto({
  ayuda,
  etiqueta,
  vacio,
  boton,
  ocupado,
  error,
  alEnviar,
  alCerrar,
  inicial = '',
  maxLength = 1000,
  peligro = false,
}: Omit<Props, 'abierto' | 'titulo'>) {
  const [texto, setTexto] = useState(inicial);
  const [falta, setFalta] = useState(false);

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (texto.trim() === '') return setFalta(true);
    setFalta(false);
    alEnviar(texto.trim());
  }

  return (
    <form className="nx-formulario-dialogo" onSubmit={enviar} noValidate>
      {ayuda !== undefined && <p className="nx-sutil">{ayuda}</p>}
      <AreaDeTexto id="dialogo-texto" etiqueta={etiqueta} maxLength={maxLength} value={texto} onChange={(e) => setTexto(e.target.value)} />
      {(falta ? vacio : error) && <Aviso>{falta ? vacio : error}</Aviso>}
      <div className="nx-dialogo__acciones">
        <Boton variante="texto" onClick={alCerrar}>
          {T.app.cancelar}
        </Boton>
        <Boton type="submit" variante={peligro ? 'peligro' : 'primario'} ocupado={ocupado}>
          {boton}
        </Boton>
      </div>
    </form>
  );
}
