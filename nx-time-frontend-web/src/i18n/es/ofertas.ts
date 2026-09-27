/** Ofertas internas y mis candidaturas. Los textos siguen a los de la app. */
export const ofertas = {
  titulo: 'Ofertas internas',
  pestanas: 'Qué ofertas',
  abiertas: 'Vacantes abiertas',
  misCandidaturas: 'Mis candidaturas',
  vacio: 'Ahora mismo no hay ninguna vacante interna publicada. Cuando se publique una, te avisaremos.',
  candidaturasVacio: 'Todavía no te has presentado a ninguna vacante.',
  publicadaPor: (quien: string) => `Publicada por ${quien}`,
  plazoHasta: (dia: string) => `Se puede optar hasta el ${dia}`,
  plazoTerminado: 'El plazo ya ha terminado',
  sinPlazo: 'Sin fecha de cierre',
  yaPresentado: 'Presentada',
  yaPresentadoDetalle: 'Ya te has presentado a esta vacante. Puedes seguir su estado en «Mis candidaturas».',
  noAdmite: 'Esta vacante ya no admite candidaturas.',
  estados: { BORRADOR: 'Borrador', ABIERTA: 'Abierta', CERRADA: 'Cerrada' } as Record<string, string>,

  presentar: 'Presentar candidatura',
  presentarTitulo: (oferta: string) => `Presentarte a «${oferta}»`,
  carta: 'Por qué te presentas (opcional)',
  cvAutomatico:
    'Se adjunta el CV que tengas subido en tu perfil, tal como está ahora. Si luego subes otro, esta candidatura seguirá con el de hoy.',
  sinCv: '¿Aún no lo has subido?',
  irAlPerfil: 'Súbelo en Mi perfil',
  presentada: 'Candidatura presentada.',

  presentadaEl: (dia: string) => `Presentada el ${dia}`,
  cvAdjunto: (nombre: string) => `CV adjunto: ${nombre}`,
  comentario: (texto: string) => `Comentario: ${texto}`,
  resueltaPor: (quien: string) => `Valorada por ${quien}`,
  estadosCandidatura: {
    RECIBIDA: 'Recibida',
    EN_PROCESO: 'En proceso',
    DESCARTADA: 'Descartada',
    SELECCIONADA: 'Seleccionada',
  } as Record<string, string>,
} as const;
