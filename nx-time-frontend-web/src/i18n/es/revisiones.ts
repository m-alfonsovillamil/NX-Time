/** Horas extra y correcciones. Los textos siguen a los de la app. */
export const revisiones = {
  horasExtra: {
    titulo: 'Horas extra',
    pestanas: 'Qué horas extra',
    mias: 'Mis excesos de jornada',
    delEquipo: 'Pendientes de revisar',
    anio: 'Año',
    vacio: 'No se ha detectado ninguna jornada por encima del límite en tus fichajes.',
    equipoVacio: 'No hay excesos de jornada esperando una decisión.',
    bolsa: (anio: number) => `Bolsa de ${anio}`,
    bolsaDetalle: (quedan: string, sinRevisar: number) =>
      `Quedan ${quedan} de las 80 h del año. ${sinRevisar} sin revisar.`,
    consumido: (consumido: string, tope: string) => `${consumido} de ${tope}`,
    alLimite: 'La bolsa anual está casi agotada: el Estatuto de los Trabajadores fija un máximo de 80 horas extra al año.',
    periodoDia: (dia: string) => `El ${dia}`,
    periodoSemana: (desde: string, hasta: string) => `Semana del ${desde} al ${hasta}`,
    // La cifra va aparte y en grande («+1h 35m»); esto es el listón, al lado.
    sobre: (esperado: string) => `por encima de ${esperado}`,
    notaJustificacion: 'Justificación',
    revisadoPor: (nombre: string) => `Revisado por ${nombre}`,
    tipos: { DIARIA: 'Jornada', SEMANAL: 'Semana' } as Record<string, string>,
    estados: { ABIERTO: 'Sin revisar', JUSTIFICADO: 'No computa', ACEPTADO: 'Horas extra' } as Record<string, string>,
    aceptar: 'Se pagan',
    // ADR 061: compensadas con descanso en los cuatro meses siguientes, no
    // gastan bolsa (art. 35.2 ET).
    compensar: 'Se compensan con descanso',
    // El mismo, corto: va en una fila de tres botones con «No computan» y «Se pagan».
    botonDescanso: 'Con descanso',
    // Pagar o compensar no se puede deshacer: se confirma diciendo qué pasa con la bolsa.
    confirmarPagoTitulo: '¿Se pagan estas horas?',
    confirmarPagoTexto: (horas: string, persona: string, cuando: string) =>
      `Las ${horas} de ${persona} (${cuando}) se pagan como horas extra y cuentan en la bolsa anual de 80 h. No se puede deshacer.`,
    confirmarDescansoTitulo: '¿Se compensan con descanso?',
    confirmarDescansoTexto: (horas: string, persona: string, cuando: string) =>
      `${persona} descansará las ${horas} (${cuando}) en los cuatro meses siguientes. Mientras, no cuentan en la bolsa anual. No se puede deshacer.`,
    cancelar: 'Cancelar',
    seCompensan: (antesDe: string) => `Se compensan con descanso antes del ${antesDe}. Mientras, no gastan bolsa.`,
    sePagan: 'Se pagan.',
    descansadas: (dia: string) => `Descansadas el ${dia}.`,
    caducadas: (antesDe: string) =>
      `No se descansaron antes del ${antesDe}: cuentan en la bolsa como horas extra.`,
    diaDelDescanso: 'Día del descanso',
    apuntarDescanso: 'Apuntar el descanso',
    descansoApuntado: 'Descanso apuntado.',
    compensada: 'Se compensarán con descanso.',
    justificar: 'No computan',
    justificarTitulo: 'No computan como horas extra',
    justificarAyuda:
      'El exceso se archiva y no consume bolsa anual. Hace falta explicar por qué: una jornada intensiva pactada, un turno partido mal fichado, una guardia…',
    motivo: 'Motivo',
    motivoVacio: 'Explica por qué no computan.',
    aceptada: 'Se pagarán como horas extra.',
    justificada: 'Archivado sin computar.',
  },

  correcciones: {
    titulo: 'Correcciones',
    pestanas: 'Qué correcciones',
    esperanPorTi: 'Esperan por ti',
    pedidasPorMi: 'Las que he pedido',
    vacioEsperan: 'No hay correcciones esperando por ti.',
    vacioPedidas: 'No has pedido ninguna corrección.',
    // La tarjeta: el nombre arriba y, debajo, de qué día es el fichaje.
    fichajeDel: (dia: string) => `Fichaje del ${dia}`,
    fichajeDelPropone: (dia: string, quien: string) => `Fichaje del ${dia} · lo propone ${quien}`,
    filas: { entrada: 'Entrada', salida: 'Salida', pausa: 'Pausa', modalidad: 'Modalidad', proyectos: 'Proyectos' },
    pausaValor: (inicio: string, fin: string) => `Añadir de ${inicio} a ${fin}`,
    notaMotivo: 'Motivo',
    notaNoAcepta: 'No lo acepta',
    comentario: (texto: string) => `Respuesta: ${texto}`,
    estados: {
      PENDIENTE: 'Pendiente',
      APROBADA: 'Aprobada',
      RECHAZADA: 'Rechazada',
      EN_DISPUTA: 'En revisión',
    } as Record<string, string>,
    aprobar: 'Aprobar',
    rechazar: 'Rechazar',
    rechazarTitulo: 'Rechazar la corrección',
    rechazarAyuda: 'La persona que la pidió verá este motivo, y el fichaje se queda como está.',
    disputar: 'No estoy de acuerdo',
    disputarTitulo: 'No estoy de acuerdo',
    disputarAyuda: 'La corrección no se aplica y pasa a revisarla Recursos Humanos, que decide en firme.',
    texto: 'Motivo',
    textoVacio: 'Escribe el motivo.',
    enviar: 'Enviar',
    aprobada: 'Corrección aprobada.',
    rechazada: 'Corrección rechazada.',
    disputada: 'Se ha enviado a Recursos Humanos.',
  },
} as const;
