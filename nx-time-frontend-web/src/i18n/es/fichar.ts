/** Mi jornada: el cronómetro, el resumen, el gráfico y las pausas. Los textos siguen a los de la app. */
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
  enPausaDesde: (hora: string) => `En pausa desde las ${hora}`,
  pausaAcumulada: (texto: string) => `Pausa: ${texto}`,
  noLaborable: (motivo: string) => `Hoy no es laborable: ${motivo}`,
  sinJornada: 'Todavía no has fichado hoy.',
  reanudaAntes: 'Reanuda la jornada antes de finalizarla.',

  entradaPrevista: (hora: string) => `Entrada prevista: ${hora}`,
  sinTurnoHoy: 'Hoy no tienes turno en tu cuadrante.',

  confirmarFin: {
    titulo: '¿Terminas la jornada?',
    entrada: (hora: string) => `Entraste a las ${hora}`,
    trabajado: (texto: string) => `Llevas trabajadas ${texto}`,
    pausa: (texto: string) => `Has hecho ${texto} de pausa`,
    aviso: 'Una vez cerrada, cambiarla exige pedir una corrección y que la apruebe tu gestor.',
    si: 'Terminar',
    no: 'Sigo trabajando',
  },

  noLaborableDialogo: {
    titulo: 'Hoy no es un día laborable',
    texto: (motivo: string) => `${motivo}. ¿Quieres iniciar la jornada igualmente? Se avisará a tu responsable.`,
    si: 'Iniciar igualmente',
  },

  proyecto: {
    elegirTitulo: '¿En qué proyecto vas a trabajar?',
    empezar: 'Empezar',
    cambiarTitulo: 'Cambiar de proyecto',
    cambiar: 'Cambiar',
    enCurso: (codigo: string, nombre: string) => `Proyecto: ${codigo} · ${nombre}`,
    sin: 'Sin proyecto',
    actual: (texto: string) => `${texto} (ahora)`,
    etiqueta: 'Proyecto',
  },

  resumen: {
    titulo: 'Mi tiempo',
    hoy: 'Hoy',
    semana: 'Esta semana',
    mes: 'Este mes',
    vacaciones: 'Vacaciones',
    vacacionesDetalle: (disponibles: number, totales: number) => `${disponibles} de ${totales} días disponibles`,
    deJornada: (texto: string) => `de ${texto} a la semana`,
    ausenciasPendientes: (n: number) =>
      n === 1 ? 'Tienes 1 solicitud de ausencia sin responder' : `Tienes ${n} solicitudes de ausencia sin responder`,
  },

  horas: {
    titulo: 'Mis horas',
    pestanas: 'Periodo del gráfico',
    semana: 'Esta semana',
    mes: 'Este mes',
    total: (trabajado: string, esperado: string) => `${trabajado} de ${esperado}`,
    leyenda:
      'La línea discontinua es la jornada esperada de cada día. En otro color, los días festivos o de ausencia.',
    tablaTitulo: 'Horas trabajadas y esperadas por día',
    dia: 'Día',
    trabajado: 'Trabajado',
    esperado: 'Esperado',
    motivo: 'Motivo',
    festivo: (nombre: string) => `Festivo: ${nombre}`,
    verTabla: 'Ver como tabla',
    verGrafico: 'Ver como gráfico',
  },

  pausa: {
    boton: 'Añadir pausa',
    titulo: 'Añadir una pausa',
    explicacionDirecta: 'Se descontará de tu jornada en cuanto la guardes.',
    explicacionAprobacion:
      'Es de un día pasado: la tendrá que aprobar tu gestor, y tu jornada no cambia hasta entonces.',
    inicio: 'Empezó a las',
    fin: 'Acabó a las',
    finOtroDia: 'Acabó al día siguiente',
    motivo: 'Motivo',
    motivoAyuda: 'Por ejemplo: «Olvidé fichar la comida».',
    motivoVacio: 'Explica por qué añades la pausa.',
    finAnterior: 'La pausa tiene que acabar después de empezar.',
    guardarDirecta: 'Añadir pausa',
    guardarAprobacion: 'Pedir que la añadan',
    aplicada: 'Pausa añadida. Tu jornada ya la descuenta.',
    pedida: 'Pausa pedida. Tu jornada no cambia hasta que la aprueben.',
    yaAnadidas: 'Pausas que ya añadiste a esta jornada',
    rango: (inicio: string, fin: string, duracion: string) => `${inicio} – ${fin} · ${duracion}`,
    deshacer: 'Deshacer',
  },
} as const;
