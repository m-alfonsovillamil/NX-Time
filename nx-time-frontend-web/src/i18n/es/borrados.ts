/** Los borrados de datos (RGPD, ADR 016). Los textos siguen a los de la app. */
export const borrados = {
  titulo: 'Borrados de datos',
  explicacion:
    'Cuando alguien pide que se borren sus datos hay un mes para responder. Se ejecuta o se rechaza con un motivo que le llega a la persona.',
  vacioTitulo: 'Nada pendiente',
  vacioTexto: 'Cuando alguien pida que se borren sus datos, aparecerá aquí. Hay un mes para responder.',
  pedidaEl: (dia: string) => `Pedido el ${dia}`,
  registradaPor: (quien: string) => `Registrada por ${quien}`,
  motivo: (texto: string) => `Motivo: ${texto}`,
  bloqueos: 'Antes hay que resolver:',

  registrar: 'Registrar solicitud',
  registrarTitulo: 'Registrar una solicitud recibida fuera de la app',
  registrarAyuda:
    'Para quien no puede pedirlo desde la app, por ejemplo porque ya está de baja y lo ha pedido por correo o por carta. Le enviaremos un acuse de recibo.',
  persona: 'Persona',
  elegir: 'Elige a alguien',
  deBaja: (nombre: string) => `${nombre} · de baja`,
  comoLlego: 'Cómo llegó (p. ej. correo del 12/09)',
  faltan: 'Elige a la persona e indica cómo llegó la solicitud.',
  nadie: 'No hay nadie para quien registrar una solicitud.',
  registrarConfirmar: 'Registrar',
  registrada: 'Solicitud registrada.',

  rechazar: 'Rechazar',
  rechazarTitulo: (quien: string) => `Rechazar el borrado de ${quien}`,
  rechazarAyuda: 'Explica por qué no se ejecuta. Se lo enviaremos a la persona, que podrá volver a pedirlo cuando se resuelva.',
  comentario: 'Comentario',
  comentarioVacio: 'Hay que explicar por qué no se ejecuta el borrado.',
  rechazada: 'Solicitud rechazada.',

  ejecutar: 'Ejecutar',
  ejecutarTitulo: (quien: string) => `¿Borrar los datos de ${quien}?`,
  ejecutarDetalle: [
    'No se puede deshacer. Se desactiva su cuenta y se borran su foto, su CV, su fecha de nacimiento, sus candidaturas, sus avisos y sus sesiones.',
    'Su registro horario se conserva con su nombre los cuatro años que exige la ley, y después se anonimiza solo. Le llegará un correo explicándoselo.',
  ],
  confirmarNombre: (nombre: string) => `Para confirmar, escribe «${nombre}»`,
  ejecutarConfirmar: 'Borrar datos',
  ejecutado: 'Datos borrados.',
} as const;
