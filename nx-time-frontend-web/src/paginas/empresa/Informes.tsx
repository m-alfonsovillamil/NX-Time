/**
 * Los informes que se descargan: el Excel de horas de la empresa y el registro
 * mensual en PDF de una persona (RD-ley 8/2019).
 *
 * Por defecto, el **mes anterior**: un informe se pide casi siempre de un mes
 * ya cerrado, y el del mes en curso saldría a medias. Se descargan con
 * `descargar()` (fetch + Blob) porque un enlace directo no llevaría el token
 * de la sesión; y si el servidor dice que no, se lee su mensaje.
 */

import { useState } from 'react';

import { cliente } from '../../api/cliente';
import { ErrorDeApi } from '../../api/consultas';
import { Aviso, Boton, Campo, Selector } from '../../componentes/Basicos';
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
    <div className="nx-pagina nx-pagina--estrecha">
      <header className="nx-cabecera">
        <h1>{I.titulo}</h1>
      </header>

      <section className="nx-tarjeta">
        <div className="nx-filtros">
          <Campo id="informe-mes" etiqueta={I.mes} type="month" max={hoyEnEmpresa().slice(0, 7)} value={mes} onChange={(e) => e.target.value !== '' && setMes(e.target.value)} />
        </div>
      </section>

      <section className="nx-tarjeta nx-expediente" aria-labelledby="informe-excel">
        <h2 id="informe-excel">{I.excelTitulo}</h2>
        <p className="nx-sutil">{I.excelTexto}</p>
        {error?.de === 'excel' && <Aviso>{error.mensaje}</Aviso>}
        <div>
          <Boton ocupado={bajando === 'excel'} onClick={() => void bajar('excel')}>
            {I.excel}
          </Boton>
        </div>
      </section>

      <section className="nx-tarjeta nx-expediente" aria-labelledby="informe-pdf">
        <h2 id="informe-pdf">{I.pdfTitulo}</h2>
        <p className="nx-sutil">{I.pdfTexto}</p>
        <Selector
          id="informe-persona"
          etiqueta={I.persona}
          value={persona}
          onChange={(e) => setPersona(e.target.value)}
          opciones={[{ valor: '', texto: I.elegir }, ...ordenar(personas.data ?? []).map((p) => ({ valor: String(p.id), texto: p.nombre ?? '' }))]}
        />
        {error?.de === 'pdf' && <Aviso>{error.mensaje}</Aviso>}
        <div>
          <Boton variante="secundario" ocupado={bajando === 'pdf'} onClick={() => void bajar('pdf')}>
            {I.pdf}
          </Boton>
        </div>
      </section>
    </div>
  );
}
