/** El armazón: menú, cabecera, campana y menú de usuario. */
export const navegacion = {
  saltarAlContenido: 'Saltar al contenido',
  menuPrincipal: 'Menú principal',
  mas: 'Más',
  todasLasSecciones: 'Todas las secciones',

  grupos: {
    personal: 'Lo mío',
    gestion: 'Gestión',
  },

  /**
   * Los apartados plegables del menú. Distintos de los nombres de las
   * secciones a propósito: «Ausencias» como apartado y como sección a la vez
   * sería un botón y un enlace que se llaman igual.
   */
  subgrupos: {
    jornada: 'Jornada y fichajes',
    ausencias: 'Mis ausencias',
    'en-la-empresa': 'En la empresa',
    cuenta: 'Mi cuenta',
    equipo: 'Mi equipo',
    organizacion: 'Organización',
    control: 'Informes y control',
    administracion: 'Administración',
  },

  /** Una por sección del catálogo (`navegacion/secciones.tsx`), también las que aún no tienen página. */
  secciones: {
    fichar: 'Mi jornada',
    historial: 'Historial',
    calendario: 'Calendario',
    ausencias: 'Ausencias',
    avisos: 'Avisos',
    cuadrante: 'Mi cuadrante',
    incidencias: 'Incidencias',
    firmas: 'Firma mensual',
    horasExtra: 'Horas extra',
    correcciones: 'Correcciones',
    ofertas: 'Ofertas internas',
    misCandidaturas: 'Mis candidaturas',
    denuncias: 'Canal de denuncias',
    perfil: 'Mi perfil',
    ajustes: 'Ajustes',
    gestion: 'Panel de gestión',
    equipo: 'Historial del equipo',
    ausenciasEquipo: 'Ausencias del equipo',
    ausenciasEquipoResueltas: 'Ausencias resueltas',
    empresa: 'Panel de empresa',
    plantilla: 'Plantilla',
    departamentos: 'Departamentos',
    proyectos: 'Proyectos',
    calendarioLaboral: 'Calendario laboral',
    informes: 'Informes',
    borrados: 'Borrados de datos',
    gestionOfertas: 'Gestión de ofertas',
    canalDenuncias: 'Denuncias recibidas',
    integridad: 'Integridad de la auditoría',
    cuadrantes: 'Cuadrantes',
    analitica: 'Analítica',
    visadoFirmas: 'Visado de firmas',
    ajustesEmpresa: 'Ajustes de la empresa',
    tarjetasKiosco: 'Tarjetas del kiosco',
  },

  /** El número al lado de una entrada del menú: lo que espera una decisión. */
  pendientes: (n: number) => (n === 1 ? '1 pendiente' : `${n} pendientes`),

  /** El estado de la jornada en la barra superior, que lleva a «Mi jornada». */
  jornada: {
    trabajando: 'Trabajando',
    enPausa: 'En pausa',
    sinFichar: 'Sin fichar',
    /** Lo que oye un lector de pantalla: el estado, el tiempo y a dónde lleva. */
    ir: (estado: string, tiempo: string | null) =>
      tiempo === null ? `${estado}. Ir a Mi jornada` : `${estado}, ${tiempo}. Ir a Mi jornada`,
  },

  usuario: {
    menu: (nombre: string) => `Menú de ${nombre}`,
    salir: 'Cerrar sesión',
  },

  campana: {
    etiqueta: (n: number) =>
      n === 0 ? 'Avisos: ninguno sin leer' : n === 1 ? 'Avisos: 1 sin leer' : `Avisos: ${n} sin leer`,
    titulo: 'Avisos',
    ninguno: 'No tienes avisos.',
    marcarTodos: 'Marcar todos como leídos',
    sinLeer: 'Sin leer',
    verTodos: 'Ver todos',
  },
} as const;
