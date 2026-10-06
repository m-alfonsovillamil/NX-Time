/**
 * Los informes que se descargan: el Excel de horas de la empresa y el registro
 * mensual en PDF de una persona (RD-ley 8/2019).
 *
 * Por defecto, el **mes anterior**: un informe se pide casi siempre de un mes
 * ya cerrado, y el del mes en curso saldría a medias. Se descargan con
 * `descargar()` (fetch + Blob) porque un enlace directo no llevaría el token
 * de la sesión; y si el servidor dice que no, se lee su mensaje.
 *
 * A lo ancho (5/10/2026): el mes, que vale para los dos, va en la cabecera; y
 * cada informe es una tarjeta con su icono, una al lado de la otra.
 */

import { useState } from 'react';

import { cliente } from '../../api/cliente';
import { ErrorDeApi } from '../../api/consultas';
import { Aviso, Boton, Campo, Selector, Tarjeta } from '../../componentes/Basicos';
import { CabeceraDePagina } from '../../componentes/CabeceraDePagina';
import { empresa } from '../../i18n/es/empresa';
import { descargar } from '../../util/descargar';
import { hoyEnEmpresa, primeroDeMes, sumarDias } from '../../util/fechas';
import { ordenar, usePlantilla } from '../plantilla/Plantilla';

const I = empresa.informes;

/** `2026-09` del mes anterior al de hoy (en España). */
export function mesAnterior(hoy: string = hoyEnEmpresa()): string {
  return sumarDias(primeroDeMes(hoy), -1).slice(0, 7);
}

export function Informes() {
  const { consulta: personas } = usePlantilla();
  const [mes, setMes] = useState(mesAnterior());
  const [persona, setPersona] = useState('');
  const [bajando, setBajando] = useState<'excel' | 'pdf' | null>(null);
  const [error, setError] = useState<{ de: 'excel' | 'pdf'; mensaje: string } | null>(null);

  const anio = Number(mes.slice(0, 4));
  const numeroDeMes = Number(mes.slice(5, 7));

  async function bajar(que: 'excel' | 'pdf') {
    if (que === 'pdf' && persona === '') return setError({ de: 'pdf', mensaje: I.faltaPersona });
    setError(null);
    setBajando(que);
    try {
      if (que === 'excel') {
        await descargar(
          cliente.GET('/api/v1/informes/horas', { params: { query: { anio, mes: numeroDeMes } }, parseAs: 'blob' }),
          `horas-${mes}.xlsx`,
        );
      } else {
        await descargar(
          cliente.GET('/api/v1/informes/mensual/{empleadoId}', {
            params: { path: { empleadoId: Number(persona) }, query: { anio, mes: numeroDeMes } },
            parseAs: 'blob',
          }),
          `registro-${mes}.pdf`,
        );
      }
    } catch (fallo) {
      setError({ de: que, mensaje: fallo instanceof ErrorDeApi ? fallo.message : String(fallo) });
    } finally {
      setBajando(null);
    }
  }

  return (
    <div className="nx-pagina">
      <CabeceraDePagina
        titulo={I.titulo}
        descripcion={I.explicacion}
        acciones={
          <Campo
            id="informe-mes"
            etiqueta={I.mes}
            type="month"
            max={hoyEnEmpresa().slice(0, 7)}
            value={mes}
            onChange={(e) => e.target.value !== '' && setMes(e.target.value)}
          />
        }
      />

      <div className="nx-composicion nx-composicion--mitades">
        <Tarjeta className="nx-tarjeta--accion" icono="hoja" titulo={I.excelTitulo} descripcion={I.excelTexto}>
          {error?.de === 'excel' && <Aviso>{error.mensaje}</Aviso>}
          <div className="nx-tarjeta__pie">
            <Boton icono="descargar" ocupado={bajando === 'excel'} onClick={() => void bajar('excel')}>
              {I.excel}
            </Boton>
          </div>
        </Tarjeta>

        <Tarjeta className="nx-tarjeta--accion" icono="pdf" titulo={I.pdfTitulo} descripcion={I.pdfTexto}>
          <Selector
            id="informe-persona"
            etiqueta={I.persona}
            value={persona}
            onChange={(e) => setPersona(e.target.value)}
            opciones={[{ valor: '', texto: I.elegir }, ...ordenar(personas.data ?? []).map((p) => ({ valor: String(p.id), texto: p.nombre ?? '' }))]}
          />
          {error?.de === 'pdf' && <Aviso>{error.mensaje}</Aviso>}
          <div className="nx-tarjeta__pie">
            <Boton icono="descargar" variante="secundario" ocupado={bajando === 'pdf'} onClick={() => void bajar('pdf')}>
              {I.pdf}
            </Boton>
          </div>
        </Tarjeta>
      </div>
    </div>
  );
}
