/** El canal de denuncias visto por quien denuncia. Los textos siguen a los de la app. */
export const denuncias = {
  titulo: 'Canal de denuncias',
  queEsTitulo: 'Canal interno de información',
  queEsTexto:
    'Aquí puedes comunicar irregularidades. La empresa tiene 7 días naturales para acusar recibo y 3 meses para responder. Puedes hacerlo con tu nombre o de forma anónima.',

  presentar: 'Presentar una denuncia',
  categoria: 'Categoría',
  descripcion: 'Qué ha pasado',
  descripcionAyuda: 'Cuenta los hechos con fechas y lugares si los recuerdas. Cuanto más concreto, más se puede investigar.',
  sinDescripcion: 'Hay que describir los hechos que se denuncian.',
  anonima: 'Enviar de forma anónima',
  anonimaSi:
    'No se guardará quién eres. Recibirás un código: es la única forma de volver a tu denuncia, y no se puede recuperar.',
  anonimaNo: 'Se guardará tu nombre. Podrás seguirla desde aquí y recibirás avisos cuando haya novedades.',
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
      'Las denuncias de la empresa, las abiertas primero y las más antiguas arriba: son las que están más cerca de que venza el plazo. La ley obliga a acusar recibo en 7 días y a responder en 3 meses.',
    vacioTitulo: 'Ninguna denuncia',
    vacioTexto: 'Cuando alguien use el canal, su expediente aparecerá aquí con los plazos legales contando.',
    esAnonima: 'Denuncia anónima',
    laPuso: (quien: string) => `La presentó ${quien}`,
    responder: 'Responder al denunciante',
    acusa: 'El primer mensaje cuenta como acuse de recibo.',
    aInvestigacion: 'Pasar a investigación',
    resolver: 'Cerrar como resuelta',
    archivar: 'Cerrar y archivar',
    conclusionAyuda: 'Obligatoria para cerrar el expediente, tanto si se resuelve como si se archiva. La ley obliga a responder, no a dar la razón.',
    conclusionVacia: 'Escribe la conclusión.',
    cerrarTitulo: (como: string) => `${como}: conclusión`,
    actualizada: 'Expediente actualizado.',
    cerrada: 'Expediente cerrado con su conclusión.',
    abrir: 'Abrir',
  },
} as const;
