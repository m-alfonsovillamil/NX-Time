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
    inspeccionTitulo: 'Para la Inspección o la representación legal',
    inspeccionTexto:
      'Un ZIP con todo el registro de un periodo de hasta doce meses: el Excel de cada mes, el PDF mensual de cada persona, la traza de correcciones con quién las hizo y las firmas. La descarga queda registrada.',
    desde: 'Desde',
    hasta: 'Hasta',
    zip: 'Descargar el ZIP',
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
    contacto: 'Contacto de privacidad',
    contactoAyuda:
      'Un correo o un enlace (https) a vuestra política de privacidad. Es a donde se dirige la plantilla para ejercer sus derechos sobre sus datos: se lo enseñamos a cada persona la primera vez que entra.',
    accesosTitulo: 'Accesos a datos personales',
    accesosTexto: 'Quién ha consultado datos de otra persona en los dos últimos años: CV, perfiles, registros mensuales, el Excel, expedientes de denuncia y exportaciones.',
    verAccesos: 'Ver los accesos',
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

  /** El canal de denuncias de la empresa (Ley 2/2023, art. 8; ADR 048). */
  canal: {
    titulo: 'Canal de denuncias',
    explicacion:
      'Quién gestiona las comunicaciones del canal interno y cómo se comunica algo de palabra. La ley obliga a tenerlo a las empresas de 50 personas o más.',
    responsable: 'Responsable del Sistema Interno de Información',
    responsableAyuda:
      'Solo esta persona verá e instruirá las denuncias: los administradores dejan de verlas. Tiene que poder hacerlo con independencia. Sin Responsable, las gestionan los administradores.',
    nadie: 'Sin designar',
    desde: (dia: string) => `Designado el ${dia}.`,
    autoridad:
      'La empresa tiene que comunicar el nombramiento y el cese a la Autoridad Independiente de Protección del Informante (o a la de su comunidad autónoma) en 10 días hábiles.',
    autoridadEnlace: 'Formulario de la Autoridad',
    autoridadUrl: 'https://proteccioninformante.sede.gob.es/procedimiento/portada?idProc=155879',
    viaVerbal: 'Vía verbal',
    viaVerbalAyuda:
      'Un teléfono, un horario o a quién pedir una reunión en persona. Se lo enseñamos a quien vaya a denunciar. La reunión, si la piden, tiene que celebrarse en 7 días.',
    guardar: 'Guardar el canal',
    guardado: 'Canal de denuncias guardado.',
  },

  accesos: {
    titulo: 'Accesos a datos personales',
    explicacion:
      'Quién ha consultado datos de otra persona: el CV de una candidatura, un perfil, su registro mensual, el Excel de horas, un expediente de denuncia o una exportación. Lo de uno mismo no se anota. Se guarda dos años y nadie puede borrarlo ni cambiarlo.',
    tabla: 'Accesos a datos personales, lo último primero',
    columnas: { cuando: 'Cuándo', quien: 'Quién', que: 'Qué', sobre: 'De quién', detalle: 'Detalle' },
    que: {
      CV: 'CV',
      PERFIL: 'Perfil',
      INFORME_MENSUAL: 'Registro mensual',
      INFORME_EMPRESA: 'Excel de horas',
      DENUNCIA: 'Expediente de denuncia',
      EXPORTACION: 'Exportación',
    },
    varias: 'Varias personas',
    denuncia: 'No se anota',
    vacioTitulo: 'Nadie ha consultado datos ajenos',
    vacioTexto: 'Cuando alguien descargue un CV, un registro mensual o el Excel, o abra un expediente, aparecerá aquí.',
    maximo: 'Se enseñan los 500 últimos.',
  },

  integridad: {
    titulo: 'Integridad de la auditoría',
    explicacion: 'La prueba de que nadie ha tocado el registro de jornada después de escribirlo.',
    ultima: 'Última comprobación automática',
    cadaNoche: 'Cada noche se recorre la cadena entera. Las cifras son las de tu empresa.',
    cifras: {
      // «De tu empresa»: la cadena es común a todas las de este servicio, y
      // hasta octubre de 2026 aquí salía el total de la instalación. Una
      // empresa recién registrada veía los movimientos de las demás.
      movimientos: 'Movimientos de tu empresa, sin alteraciones',
      pendientes: 'Posteriores, aún sin comprobar',
      pendientesDetalle: 'Se comprueban esta noche, o ahora con el botón.',
      cuando: 'Comprobada el',
    },
    nunca: 'Todavía no se ha comprobado ninguna vez.',
    nuncaDetalle: 'La primera comprobación automática será esta noche. También puedes hacerla ahora.',
    comprobarTitulo: 'Comprobar ahora',
    comprobarTexto:
      'Recorre la cadena entera. Con años de traza puede tardar un poco; si alguien acaba de comprobarla, se enseña ese resultado.',
    comprobar: 'Comprobar la cadena',
    intacta: 'La traza está intacta.',
    rota: 'La traza NO está intacta.',
    detalle: (movimientos: number, comprobados: number, soloEnlace: number) =>
      movimientos === 0
        ? 'Tu empresa todavía no tiene movimientos. La cadena, que es común a todas las empresas de este servicio, se ha recorrido entera.'
        : `${movimientos} movimientos de tu empresa revisados: a ${comprobados} se les ha recalculado la huella${soloEnlace > 0 ? ` y de ${soloEnlace}, anteriores a septiembre de 2026, solo se ha podido comprobar el enlace con el anterior` : ''}.`,
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
        icono: 'empresa',
        titulo: 'La cadena es común; las cifras, las tuyas',
        texto: 'Todas las empresas de este servicio escriben en la misma cadena, y se comprueba entera. Aquí solo se cuentan los movimientos de la tuya, y nadie más ve los tuyos.',
      },
      {
        icono: 'verificado',
        titulo: 'Por qué importa',
        texto: 'El RD-ley 8/2019 exige conservar el registro de jornada cuatro años y que sea fiable. Esta comprobación te ayuda a demostrar ante una inspección que nadie lo ha cambiado sin dejar rastro.',
      },
    ],
  },
} as const;
