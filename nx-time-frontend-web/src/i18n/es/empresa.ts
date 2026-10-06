/** Panel de empresa, informes e integridad de la auditoría. Los textos siguen a los de la app. */
export const empresa = {
  panel: {
    titulo: 'Panel de empresa',
    delMes: (mes: string) => `Este mes · ${mes}`,
    empleadosActivos: 'Empleados activos',
    horasMes: 'Horas del mes',
    ausencias: 'Ausencias por aprobar',
    incidencias: 'Incidencias abiertas',
    incidenciasAyuda: 'Jornadas que cerró el sistema por no tener fichaje de salida y que nadie ha corregido todavía.',
    horasExtra: 'Horas extra sin revisar',
    denuncias: 'Denuncias abiertas',
    horasPorEmpleado: 'Horas por empleado',
    mediaEquipo: (media: string) => `La raya es la media del equipo: ${media}`,
    horasPorProyecto: 'Horas por proyecto',
    sinHoras: 'Nadie ha fichado todavía este mes.',
  },

  analitica: {
    titulo: 'Absentismo y puntualidad del mes',
    absentismo: 'Absentismo',
    puntualidad: 'Puntualidad',
    ayuda: 'Absentismo: días perdidos sobre días que se debían trabajar, sin contar vacaciones. Puntualidad: solo de quien tiene cuadrante.',
    alcance: (hasta: string, quien: string) => `Hasta el ${hasta} · ${quien}`,
    todaLaEmpresa: 'toda la empresa',
    sinDias: 'Todavía no ha terminado ningún día del mes.',
  },

  informes: {
    titulo: 'Informes',
    explicacion: 'Lo que se descarga para una inspección o para archivar. Salen del mes que elijas; por defecto, el anterior, que ya está cerrado.',
    mes: 'Mes del informe',
    excelTitulo: 'Horas de la empresa',
    excelTexto: 'Todas las jornadas cerradas del mes, con sus totales. Las que cerró el sistema por falta de fichaje de salida salen marcadas.',
    excel: 'Descargar Excel',
    pdfTitulo: 'Registro mensual de una persona',
    pdfTexto:
      'El registro de jornada individual que exige el RD-ley 8/2019: el detalle de cada día, el total del mes y el espacio para la firma de la persona y de la empresa.',
    persona: 'Persona',
    elegir: 'Elige a alguien',
    pdf: 'Descargar PDF',
    faltaPersona: 'Elige de quién es el informe.',
  },

  ajustes: {
    titulo: 'Ajustes de la empresa',
    explicacion: 'Cómo se llama la empresa, en qué hora vive y qué tablets fichan por ella.',
    datos: 'La empresa',
    datosTexto: 'El nombre sale en los informes y en los correos. La zona horaria decide a qué día pertenece cada fichaje.',
    nombre: 'Nombre de la empresa',
    nombreVacio: 'Pon el nombre de la empresa.',
    zona: 'Zona horaria',
    zonaAyuda:
      'Decide a qué día pertenece cada fichaje y en qué hora se ven las entradas y salidas, en la web, en la app y en los informes.',
    peninsula: 'Península, Baleares, Ceuta y Melilla',
    canarias: 'Canarias',
    otras: 'Otras zonas',
    guardar: 'Guardar',
    guardado: 'Ajustes guardados.',
    confirmarTitulo: '¿Cambiar la zona horaria?',
    confirmarTexto: (zona: string) =>
      `A partir de ahora, todos los días se contarán en ${zona}, también los del pasado: una jornada que empezó cerca de la medianoche puede pasar al día de antes o al de después. Si eso cambia un mes ya firmado, la firma se anula y la persona tendrá que volver a firmarlo.`,
    confirmar: 'Cambiar la zona',
    firmasAnuladas: (n: number) =>
      n === 1
        ? 'Zona cambiada. Se ha anulado 1 firma mensual cuyo mes ha cambiado: se le ha avisado para que vuelva a firmar.'
        : `Zona cambiada. Se han anulado ${n} firmas mensuales cuyo mes ha cambiado: se ha avisado a cada persona para que vuelva a firmar.`,
  },

  integridad: {
    titulo: 'Integridad de la auditoría',
    explicacion: 'La prueba de que nadie ha tocado el registro de jornada después de escribirlo.',
    ultima: 'Última comprobación automática',
    cadaNoche: 'Cada noche se recorre la cadena entera.',
    cifras: {
      movimientos: 'Movimientos sin alteraciones',
      pendientes: 'Posteriores, aún sin comprobar',
      pendientesDetalle: 'Se comprueban esta noche, o ahora con el botón.',
      cuando: 'Comprobada el',
    },
    nunca: 'Todavía no se ha comprobado ninguna vez.',
    nuncaDetalle: 'La primera comprobación automática será esta noche. También puedes hacerla ahora.',
    comprobarTitulo: 'Comprobar ahora',
    comprobarTexto: 'Recorre la cadena entera en este momento. Con años de traza puede tardar un poco.',
    comprobar: 'Comprobar la cadena',
    intacta: 'La traza está intacta.',
    rota: 'La traza NO está intacta.',
    detalle: (movimientos: number, comprobados: number, soloEnlace: number) =>
      `${movimientos} movimientos revisados: a ${comprobados} se les ha recalculado la huella${soloEnlace > 0 ? ` y de ${soloEnlace}, anteriores a septiembre de 2026, solo se ha podido comprobar el enlace con el anterior` : ''}.`,
    primerFallo: (id: number, motivo: string) => `Primer movimiento con problemas: el ${id}. ${motivo}`,
    comoFunciona: 'Cómo funciona',
    puntos: [
      {
        icono: 'enlace',
        titulo: 'Cada movimiento lleva la huella del anterior',
        texto: 'Cada cambio en un fichaje (entrar, salir, una corrección) queda en una traza encadenada.',
      },
      {
        icono: 'candado',
        titulo: 'Tocar uno rompe la cadena',
        texto: 'Si alguien modificara un movimiento directamente en la base de datos, la cadena dejaría de cuadrar a partir de ese punto, y se vería cuál es.',
      },
      {
        icono: 'verificado',
        titulo: 'Es lo que pide la ley',
        texto: 'El RD-ley 8/2019 exige conservar el registro de jornada cuatro años y que sea fiable. Esta comprobación es lo que lo demuestra ante una inspección.',
      },
    ],
  },
} as const;
