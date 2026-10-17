/**
 * Los textos legales del servicio (plan J, fase J3; ADR 046), uno por página
 * bajo `/legal/<ruta>`. Públicos: se abren con sesión y sin ella, desde el pie
 * de las pantallas de acceso, el registro, el kiosco y Ajustes en la app.
 *
 * Todos los datos de quién presta el servicio salen de `TITULAR`, que se
 * genera desde la configuración del backend (scripts/titular.mjs): aquí no se
 * escribe ni el nombre del producto, ni el titular, ni un correo.
 */

import type { ComponentType } from 'react';

import { AvisoLegal } from './textos/AvisoLegal';
import { Condiciones } from './textos/Condiciones';
import { Cookies } from './textos/Cookies';
import { Encargo } from './textos/Encargo';
import { Privacidad } from './textos/Privacidad';
import { Seguridad } from './textos/Seguridad';
import { Subencargados } from './textos/Subencargados';

export interface DocumentoLegal {
  ruta: string;
  titulo: string;
  /** Una frase: qué es, para el índice. */
  resumen: string;
  Texto: ComponentType;
}

export const DOCUMENTOS: readonly DocumentoLegal[] = [
  { ruta: 'aviso-legal', titulo: 'Aviso legal', resumen: 'Quién presta el servicio y cómo contactar.', Texto: AvisoLegal },
  {
    ruta: 'condiciones',
    titulo: 'Condiciones del servicio',
    resumen: 'Lo que acepta la empresa al registrarse: qué es el servicio, qué no garantiza y qué le toca a ella.',
    Texto: Condiciones,
  },
  {
    ruta: 'encargo',
    titulo: 'Contrato de encargo del tratamiento',
    resumen: 'Cómo tratamos los datos de la plantilla por cuenta de la empresa (RGPD, art. 28).',
    Texto: Encargo,
  },
  {
    ruta: 'subencargados',
    titulo: 'Subencargados',
    resumen: 'Los proveedores que usamos para prestar el servicio, y dónde están.',
    Texto: Subencargados,
  },
  {
    ruta: 'privacidad',
    titulo: 'Política de privacidad',
    resumen: 'Los datos que tratamos por nuestra cuenta: quien registra y administra una empresa, la web y la app.',
    Texto: Privacidad,
  },
  {
    ruta: 'cookies',
    titulo: 'Cookies y almacenamiento',
    resumen: 'Solo técnicas, sin publicidad ni analítica: cuáles son y para qué.',
    Texto: Cookies,
  },
  {
    ruta: 'seguridad',
    titulo: 'Avisar de un fallo de seguridad',
    resumen: 'Cómo avisarnos de una vulnerabilidad y qué hacemos con el aviso.',
    Texto: Seguridad,
  },
];

export function documento(ruta: string | undefined): DocumentoLegal | undefined {
  return DOCUMENTOS.find((doc) => doc.ruta === ruta);
}
