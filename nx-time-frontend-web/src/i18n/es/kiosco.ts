/** El kiosco de fichaje (ADR 033): la tablet, su gestión y lo de cada persona. */
export const kiosco = {
  tablet: {
    titulo: 'Kiosco de fichaje',
    // Emparejar
    preparando: 'Preparando el kiosco…',
    emparejarTitulo: 'Empareja esta tablet',
    emparejarTexto:
      'Quien administra la empresa tiene que teclear este código en la web: Ajustes de la empresa → Kioscos.',
    caduca: (hora: string) => `El código vale hasta las ${hora}.`,
    esperando: 'Esperando a que alguien lo confirme…',
    caducado: 'El código ha caducado sin que nadie lo confirmara.',
    otroCodigo: 'Pedir otro código',
    yaEntregado: 'Esta tablet ya se había emparejado y perdió su clave. Empieza otra vez.',
    // Espera
    pasaTuTarjeta: 'Pasa tu tarjeta por la cámara',
    oBuscaTuNombre: 'Busca tu nombre',
    sinCamara: 'No se puede usar la cámara. Busca tu nombre y teclea tu PIN.',
    // Lista y PIN
    buscar: 'Tu nombre',
    nadie: 'Nadie con ese nombre tiene PIN. Elige uno en tu perfil, desde la app o la web.',
    nadieConPin: 'Todavía nadie ha elegido un PIN para fichar aquí.',
    pinDe: (nombre: string) => `PIN de ${nombre}`,
    borrar: 'Borrar',
    entrar: 'Aceptar',
    volver: 'Volver',
    // Identificado
    hola: (nombre: string) => `Hola, ${nombre}`,
    sinJornada: 'No tienes ninguna jornada abierta.',
    trabajando: 'Estás trabajando.',
    enPausa: 'Estás en pausa.',
    ficharEntrada: 'Fichar la entrada',
    empezarPausa: 'Empezar una pausa',
    volverDePausa: 'Volver de la pausa',
    ficharSalida: 'Fichar la salida',
    elegirProyecto: '¿En qué proyecto vas a trabajar?',
    sinProyecto: 'Sin proyecto',
    // Hecho
    hecho: {
      INICIO: (nombre: string, hora: string) => `${nombre}: entrada a las ${hora}`,
      FIN: (nombre: string, hora: string) => `${nombre}: salida a las ${hora}`,
      PAUSA_INICIO: (nombre: string, hora: string) => `${nombre}: pausa desde las ${hora}`,
      PAUSA_FIN: (nombre: string, hora: string) => `${nombre}: de vuelta a las ${hora}`,
    },
    hasta: 'Hasta luego.',
  },

  gestion: {
    titulo: 'Kioscos',
    explicacion:
      'Una tablet en la entrada donde la plantilla ficha sin abrir su sesión: con su tarjeta o con su nombre y su PIN. Abre nxtime-web.com/kiosco en la tablet y teclea aquí el código que enseña.',
    codigo: 'Código de la tablet',
    nombre: 'Nombre del kiosco',
    nombreAyuda: 'Para reconocerlo: «Entrada almacén», «Obra calle Mayor».',
    anadir: 'Dar de alta',
    anadido: (nombre: string) => `Kiosco «${nombre}» dado de alta. La tablet se pondrá en marcha sola en unos segundos.`,
    faltan: 'Teclea el código y ponle un nombre.',
    ninguno: 'Todavía no hay ningún kiosco.',
    ultimoUso: (cuando: string) => `Último fichaje: ${cuando}`,
    sinUso: 'Nadie ha fichado todavía en él.',
    revocado: 'Revocado',
    revocar: 'Revocar',
    revocarTitulo: (nombre: string) => `¿Revocar «${nombre}»?`,
    revocarTexto: 'La tablet deja de poder fichar al momento. Sus fichajes se quedan como están.',
    revocadoAviso: 'Kiosco revocado.',
  },

  perfil: {
    titulo: 'Fichar en un kiosco',
    explicacion:
      'Si tu empresa tiene una tablet de fichaje, puedes fichar en ella pasando tu tarjeta o eligiendo tu nombre y tecleando tu PIN.',
    pin: 'Tu PIN',
    pinAyuda: 'De 4 a 6 cifras, sin repetidas ni seguidas. Nadie más lo ve, ni quien administra.',
    tienePin: 'Tienes un PIN para el kiosco.',
    sinPin: 'Todavía no tienes PIN: no apareces en la lista del kiosco.',
    bloqueado: (hasta: string) => `Tu PIN está bloqueado por demasiados intentos hasta las ${hasta}. Si lo cambias, se desbloquea.`,
    guardarPin: 'Guardar el PIN',
    cambiarPin: 'Cambiar el PIN',
    quitarPin: 'Quitar el PIN',
    pinGuardado: 'PIN guardado.',
    pinQuitado: 'PIN quitado: ya no apareces en la lista del kiosco.',
    pinInvalido: 'El PIN tiene que tener de 4 a 6 cifras.',
    tarjeta: 'Tu tarjeta',
    verTarjeta: 'Ver mi tarjeta',
    tarjetaAyuda: 'Enséñala a la cámara del kiosco. Puedes descargarla para imprimirla o llevarla en el móvil.',
    descargar: 'Descargar',
    ocultarTarjeta: 'Ocultar',
    regenerar: 'Hacer una nueva',
    regenerarTitulo: '¿Hacer una tarjeta nueva?',
    regenerarTexto: 'La que tienes ahora, impresa o en el móvil, dejará de valer.',
    regenerada: 'Tarjeta nueva. La anterior ya no vale.',
  },

  tarjetas: {
    titulo: 'Tarjetas del kiosco',
    explicacion:
      'Una tarjeta por persona de la plantilla, para recortar. Con ella se ficha en los kioscos de la empresa; no abre ninguna sesión.',
    imprimir: 'Imprimir',
    ninguna: 'No hay nadie de alta en la plantilla.',
  },

  distintivo: (nombre: string) => `Kiosco · ${nombre}`,
} as const;
