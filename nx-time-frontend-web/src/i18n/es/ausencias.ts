/** Mis ausencias y el calendario. Los textos siguen a los de la app. */

type TipoAusencia =
  | 'VACACIONES'
  | 'ASUNTOS_PROPIOS'
  | 'MATRIMONIO'
  | 'FALLECIMIENTO_FAMILIAR'
  | 'HOSPITALIZACION_FAMILIAR'
  | 'LACTANCIA'
  | 'MATERNIDAD_PATERNIDAD'
  | 'MEDICO'
  | 'TRASLADO_DOMICILIO'
  | 'VIAJE_TRABAJO'
  | 'OTROS';

type EstadoAusencia = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';

const tipos: Record<TipoAusencia, string> = {
  VACACIONES: 'Vacaciones',
  ASUNTOS_PROPIOS: 'Asuntos propios',
  MATRIMONIO: 'Matrimonio',
  FALLECIMIENTO_FAMILIAR: 'Fallecimiento de un familiar',
  HOSPITALIZACION_FAMILIAR: 'Hospitalización de un familiar',
  LACTANCIA: 'Lactancia',
  MATERNIDAD_PATERNIDAD: 'Maternidad o paternidad',
  MEDICO: 'Visita médica',
  TRASLADO_DOMICILIO: 'Traslado de domicilio',
  VIAJE_TRABAJO: 'Viaje de trabajo',
  OTROS: 'Otros',
};

const estados: Record<EstadoAusencia, string> = {
  PENDIENTE: 'Pendiente',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
};

export const ausencias = {
  tipos,
  estados,
  /** El orden en que se ofrecen al solicitar: las más habituales primero. */
  ordenDeTipos: Object.keys(tipos) as TipoAusencia[],

  titulo: 'Mis ausencias',
  solicitar: 'Solicitar ausencia',
  vacioTitulo: 'No tienes solicitudes',
  vacioTexto: 'Aquí verás las ausencias que solicites y su estado.',

  saldo: {
    titulo: (anio: number) => `Vacaciones de ${anio}`,
    disponibles: 'Disponibles',
    consumidos: 'Usados',
    pendientes: 'Pedidos sin aprobar',
    totales: 'Del año',
    dias: (n: number) => (n === 1 ? '1 día' : `${n} días`),
  },

  filtros: {
    titulo: 'Filtrar',
    anio: 'Año',
    todosLosAnios: 'Todos los años',
    tipo: 'Tipo',
    todosLosTipos: 'Todos los tipos',
    estado: 'Estado',
    todosLosEstados: 'Todos los estados',
    quitar: 'Quitar filtros',
    sinResultados: 'No hay ausencias con estos filtros.',
  },

  tabla: {
    titulo: 'Mis solicitudes de ausencia',
    fechas: 'Fechas',
    tipo: 'Tipo',
    dias: 'Días hábiles',
    estado: 'Estado',
    detalle: 'Detalle',
  },
  rango: (desde: string, hasta: string) => (desde === hasta ? desde : `Del ${desde} al ${hasta}`),
  diasHabiles: (n: number) => (n === 1 ? '1 día hábil' : `${n} días hábiles`),
  resueltaPor: (nombre: string) => `Resuelta por ${nombre}`,
  respuesta: (texto: string) => `Respuesta: ${texto}`,
  motivo: (texto: string) => `Motivo: ${texto}`,

  solicitud: {
    titulo: 'Solicitar ausencia',
    tipo: 'Tipo de ausencia',
    desde: 'Fecha de inicio',
    hasta: 'Fecha de fin',
    motivo: 'Motivo (opcional)',
    enviar: 'Enviar solicitud',
    fechasIncompletas: 'Elige las dos fechas.',
    fechaInvertida: 'La fecha de fin no puede ser anterior a la de inicio.',
    enviada: 'Solicitud enviada. Te avisaremos cuando la resuelvan.',
    explicacion:
      'Los días hábiles los calcula el servidor: no cuentan los fines de semana ni los festivos de tu empresa.',
  },

  calendario: {
    titulo: 'Calendario',
    anterior: 'Mes anterior',
    siguiente: 'Mes siguiente',
    hoy: 'Hoy',
    verEquipo: 'Ver también al equipo',
    festivo: 'Festivo',
    ausencia: 'Ausencia',
    festivosDelMes: 'Festivos del mes',
    sinFestivos: 'Este mes no tiene ningún festivo.',
    diaSinAusencias: 'Nadie está ausente este día.',
    propia: 'Tú',
    ausentes: (n: number) => (n === 1 ? '1 persona ausente' : `${n} personas ausentes`),
    ambitos: {
      NACIONAL: 'Nacional',
      AUTONOMICO: 'Autonómico',
      LOCAL: 'Local',
      EMPRESA: 'Empresa',
    } as Record<string, string>,
    nota:
      'Los festivos nacionales los calcula la aplicación. Los autonómicos y locales los añade quien gestiona el calendario de la empresa.',
    diasDeLaSemana: ['L', 'M', 'X', 'J', 'V', 'S', 'D'],
    diasDeLaSemanaLargos: ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'],
  },
} as const;

export type { EstadoAusencia, TipoAusencia };
