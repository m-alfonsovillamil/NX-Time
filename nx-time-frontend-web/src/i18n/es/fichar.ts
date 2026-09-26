/** Mi jornada. */
export const fichar = {
  titulo: 'Mi jornada',
  saludo: (nombre: string) => `Hola, ${nombre}`,

  parado: 'Sin fichar',
  trabajando: 'Trabajando',
  enPausa: 'En pausa',

  entrar: 'Fichar entrada',
  salir: 'Fichar salida',
  pausar: 'Pausar',
  reanudar: 'Reanudar',

  desde: (hora: string) => `Desde las ${hora}`,
  pausaAcumulada: (texto: string) => `Pausa: ${texto}`,
  noLaborable: (motivo: string) => `Hoy no es laborable: ${motivo}`,
  sinJornada: 'Todavía no has fichado hoy.',
} as const;
