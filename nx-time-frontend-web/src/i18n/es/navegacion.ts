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
  },
} as const;
