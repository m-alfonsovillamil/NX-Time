/** El canal de denuncias visto por quien denuncia. Los textos siguen a los de la app. */
export const denuncias = {
  titulo: 'Canal de denuncias',
  queEsTitulo: 'Canal interno de información',
  queEsTexto:
    'Aquí puedes comunicar irregularidades, con tu nombre o de forma anónima. El acuse de recibo es el aviso que recibes al enviarla, y la empresa tiene 3 meses para responderte (6 si el caso es complejo y te lo explica).',

  /** Lo que la Ley 2/2023 obliga a decir a quien va a informar (ADR 048). */
  gestiona: (quien: string) => `La gestiona ${quien}, Responsable del Sistema Interno de Información de la empresa.`,
  gestionaAdministracion: 'La gestiona la administración de la empresa.',
  viaVerbalTitulo: 'De palabra o en persona',
  viaVerbalSinDatos:
    'También puedes comunicarlo de palabra o pedir una reunión en persona, que tiene que celebrarse en 7 días: pregúntalo a quien gestiona el canal.',
  viaVerbalNota: 'Si pides una reunión, tiene que celebrarse en 7 días.',
  proteccionTitulo: 'Tus datos y tu protección',
  proteccionTexto:
    'Tu identidad es confidencial: no se comunica a las personas a las que se refieren los hechos ni a nadie más, salvo a un juez, al Ministerio Fiscal o a la autoridad competente en una investigación. Tu empresa trata tus datos solo para gestionar la comunicación, y si en tres meses no se abre una investigación, su contenido se suprime. Las represalias contra quien informa de buena fe están prohibidas.',
  ambito:
    'La protección de la Ley 2/2023 cubre las infracciones penales, las administrativas graves o muy graves y las del Derecho de la Unión Europea. Puedes comunicar aquí otras cosas, pero quedan fuera de esa protección.',
  externosTitulo: 'Fuera de la empresa',
  externosTexto:
    'También puedes acudir, antes o en lugar de este canal, a la Autoridad Independiente de Protección del Informante, a la autoridad de tu comunidad autónoma si la hay, o a las instituciones de la Unión Europea.',
  externosEnlace: 'Cómo informar a la Autoridad',
  externosUrl: 'https://www.proteccioninformante.gob.es/nuestro-trabajo/investigacion/como-informar',

  presentar: 'Presentar una denuncia',
  categoria: 'Categoría',
  descripcion: 'Qué ha pasado',
  descripcionAyuda: 'Cuenta los hechos con fechas y lugares si los recuerdas. Cuanto más concreto, más se puede investigar.',
  sinDescripcion: 'Hay que describir los hechos que se denuncian.',
  anonima: 'Enviar de forma anónima',
  anonimaSi:
    'No se guardará quién eres. Recibirás un código: es la única forma de volver a tu denuncia, y no se puede recuperar.',
  anonimaNo: 'Se guardará tu nombre. Podrás seguirla desde aquí y recibirás avisos cuando haya novedades.',
  contacto: 'Dónde recibir notificaciones (opcional)',
  contactoAyuda: 'Un domicilio, un correo o un lugar seguro. Si la envías anónima, ten en cuenta que esto puede identificarte.',
  enviar: 'Enviar denuncia',
  categorias: {
    ACOSO: 'Acoso laboral o sexual',
    DISCRIMINACION: 'Discriminación',
    FRAUDE: 'Fraude o irregularidad contable',
    SEGURIDAD: 'Seguridad y salud en el trabajo',
    CORRUPCION: 'Corrupción o soborno',
    PROTECCION_DATOS: 'Protección de datos',
    OTRA: 'Otra',
  },

  codigoTitulo: 'Tu código de seguimiento',
  codigoUnaVez: 'Solo se enseña esta vez. Guárdalo ahora: sin él no podrás volver a tu denuncia.',
  copiar: 'Copiar',
  copiado: 'Copiado.',
  codigoGuardado: 'Ya lo he guardado',
  presentadaIdentificada: 'Denuncia enviada. La tienes en «Mis denuncias».',

  seguir: 'Seguir una denuncia',
  seguirAyuda: 'Con el código que recibiste al enviarla. Es la única vía para una denuncia anónima.',
  codigo: 'Código de seguimiento',
  sinCodigo: 'Escribe el código de seguimiento.',
  buscar: 'Buscar',

  mias: 'Mis denuncias',
  miasVacio:
    'Aquí solo salen las que enviaste con tu nombre. Las anónimas no aparecen: no hay ningún dato que las relacione contigo, y a esas se llega con su código.',
  ver: 'Ver',

  estados: {
    RECIBIDA: 'Recibida',
    EN_INVESTIGACION: 'En investigación',
    RESUELTA: 'Resuelta',
    ARCHIVADA: 'Archivada',
  } as Record<string, string>,
  presentadaAnonima: (cuando: string) => `Anónima · ${cuando}`,
  presentadaConNombre: (cuando: string) => `Con tu nombre · ${cuando}`,
  mensajesCuenta: (n: number) => (n === 1 ? '1 mensaje en el expediente' : `${n} mensajes en el expediente`),
  plazoAcuse: (dias: number) => `Quedan ${dias} días para acusar recibo`,
  plazoAcuseVencido: (dias: number) => `Sin acuse de recibo desde hace ${dias} días`,
  plazoRespuesta: (dias: number) => `Quedan ${dias} días para responder`,
  plazoRespuestaVencido: (dias: number) => `Sin respuesta desde hace ${dias} días`,

  acuse: (cuando: string) => `Acuse de recibo: ${cuando}`,
  contactoDado: (contacto: string) => `Notificaciones: ${contacto}`,
  plazoAmpliado: 'El plazo de respuesta se amplió a seis meses.',
  suprimida: 'Su contenido se suprimió: pasaron tres meses sin abrir una investigación (Ley 2/2023, art. 32).',

  conclusion: 'Conclusión',
  conversacion: 'Conversación',
  autores: { DENUNCIANTE: 'Denunciante', INSTRUCTOR: 'Instrucción' } as Record<string, string>,
  responder: 'Escribir un mensaje',
  enviarMensaje: 'Enviar',
  mensajeVacio: 'Escribe el mensaje.',
  mensajeEnviado: 'Mensaje enviado.',
  cerrado: 'El expediente está cerrado: ya no admite mensajes.',

  canal: {
    titulo: 'Canal interno',
    explicacion:
      'Las denuncias de la empresa, las abiertas primero y las más antiguas arriba: son las que están más cerca de que venza el plazo. El acuse de recibo se da al enviarla; la respuesta, en 3 meses (6 si se amplía).',
    vacioTitulo: 'Ninguna denuncia',
    vacioTexto: 'Cuando alguien use el canal, su expediente aparecerá aquí con los plazos legales contando.',
    esAnonima: 'Denuncia anónima',
    laPuso: (quien: string) => `La presentó ${quien}`,
    responder: 'Responder al denunciante',
    acusa: 'Lo que escribas aquí lo lee quien denunció.',
    supresion: (dias: number) =>
      dias <= 0
        ? 'Si no la pasas hoy a investigación, esta noche se suprime su contenido (Ley 2/2023, art. 32).'
        : `Si no la pasas a investigación, su contenido se suprime en ${dias} días (Ley 2/2023, art. 32).`,
    prorrogar: 'Ampliar el plazo a seis meses',
    prorrogaTitulo: 'Ampliar el plazo de respuesta',
    prorrogaAyuda: 'Solo en casos de especial complejidad, una vez y antes de que venza. El motivo le llega a quien denunció.',
    prorrogaEtiqueta: 'Por qué se amplía',
    prorrogaVacia: 'Explica por qué se amplía.',
    prorrogada: 'Plazo ampliado a seis meses.',
    suprimir: 'Suprimir',
    cerrarCancelar: 'Cancelar',
    suprimirDescripcion: 'Suprimir el relato',
    suprimirTitulo: 'Suprimir un texto',
    suprimirAyuda: 'Lo suprimido no se puede recuperar: queda la marca de cuándo y por qué. Hazlo con lo que no debe estar en el expediente.',
    motivo: 'Motivo',
    motivos: {
      CATEGORIAS_ESPECIALES: 'Contiene datos de categorías especiales (salud, ideología, vida sexual…)',
      NO_PERTINENTE: 'No es pertinente para la investigación',
      NO_VERAZ: 'Se ha acreditado que no es veraz',
    },
    suprimido: 'Texto suprimido.',
    libro: 'Descargar el libro-registro',
    libroAyuda: 'El registro que exige la ley (art. 26): fechas, objeto y estado de cada comunicación, sin relatos ni nombres.',
    accesosTitulo: 'Quién ha abierto cada expediente',
    accesosVacio: 'Nadie ha abierto todavía ningún expediente.',
    columnasAccesos: { cuando: 'Cuándo', quien: 'Quién', expediente: 'Expediente' },
    aInvestigacion: 'Pasar a investigación',
    resolver: 'Cerrar como resuelta',
    archivar: 'Cerrar y archivar',
    conclusionAyuda: 'Obligatoria para cerrar el expediente, tanto si se resuelve como si se archiva. La ley obliga a responder, no a dar la razón.',
    conclusionVacia: 'Escribe la conclusión.',
    cerrarTitulo: (como: string) => `${como}: conclusión`,
    actualizada: 'Expediente actualizado.',
    cerrada: 'Expediente cerrado con su conclusión.',
    abrir: 'Abrir',
    elige: 'Elige una denuncia',
    eligeTexto: 'Su expediente se abre aquí, con los mensajes y lo que se puede hacer con ella.',
  },
} as const;
