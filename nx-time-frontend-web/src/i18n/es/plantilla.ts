/** La plantilla y los departamentos. Los textos siguen a los de la app. */
export const plantilla = {
  titulo: 'Plantilla',
  tablaTitulo: 'Personas de la empresa',
  buscar: 'Buscar por nombre o correo',
  verDeBaja: 'Incluir a quien está de baja',
  vacio: 'Todavía no hay nadie en la plantilla.',
  sinResultados: 'Nadie coincide con la búsqueda.',
  nombre: 'Nombre',
  email: 'Correo',
  rol: 'Rol',
  departamento: 'Departamento',
  jornada: 'Jornada',
  vacaciones: 'Vacaciones',
  estado: 'Estado',
  acciones: 'Acciones',
  accionesDe: (quien: string) => `Acciones de ${quien}`,
  horasSemana: (horas: string) => `${horas} h/semana`,
  dias: (n: number) => `${n} días`,
  sinDepartamento: 'Sin departamento',
  deBaja: 'De baja',
  deAlta: 'De alta',
  roles: { EMPLEADO: 'Empleado', GESTOR: 'Gestor', RRHH: 'RRHH', ADMIN: 'Administración' } as Record<string, string>,

  nuevoEmpleado: 'Nuevo empleado',
  nuevoGestor: 'Nuevo gestor',
  altaNombre: 'Nombre',
  altaApellidos: 'Apellidos',
  altaEmail: 'Correo electrónico',
  altaExplicacion:
    'Le llegará un correo con un código para que elija su propia contraseña. Nadie más la conocerá, tampoco quien da el alta.',
  altaFaltan: 'Rellena todos los campos.',
  crear: 'Crear',
  creado: (quien: string) => `${quien} ya está en la plantilla. Le hemos enviado el correo para que entre.`,

  editarFicha: 'Ficha',
  fichaTitulo: (quien: string) => `Ficha de ${quien}`,
  fichaHoras: 'Jornada semanal (horas)',
  fichaDias: 'Días de vacaciones al año',
  fichaAyuda: 'Los días de vacaciones se aplican al año en curso.',
  fichaHorasMal: 'La jornada tiene que ser de más de 0 y como mucho 60 horas, con un decimal como mucho.',
  fichaDiasMal: 'Los días de vacaciones tienen que estar entre 0 y 365.',
  guardar: 'Guardar',
  fichaGuardada: 'Ficha guardada.',

  darDeBaja: 'Dar de baja',
  reactivar: 'Dar de alta',
  bajaTitulo: (quien: string) => `¿Dar de baja a ${quien}?`,
  bajaTexto: 'No se borra nada: sus fichajes se conservan y podrás volver a darle de alta cuando quieras. Mientras tanto no podrá entrar.',
  /** Importar la plantilla desde un fichero (ADR 052). */
  importar: {
    boton: 'Importar desde Excel o CSV',
    titulo: 'Importar la plantilla',
    explicacion:
      'Sube un Excel (.xlsx) o un CSV con una persona por fila. La primera fila lleva los títulos: nombre, apellidos y email, y si quieres rol, departamento, horas y vacaciones. Primero lo comprobamos, sin crear nada.',
    ejemplo: 'Descargar un ejemplo',
    ficheroEjemplo:
      'nombre;apellidos;email;rol;departamento;horas;vacaciones\nAna;López García;ana.lopez@tuempresa.es;empleado;;40;22\nLuis;Martín Ruiz;luis.martin@tuempresa.es;gestor;;37,5;23\n',
    fichero: 'Fichero',
    comprobando: 'Comprobando…',
    resumen: (filas: number, conErrores: number) =>
      conErrores === 0
        ? `${filas} ${filas === 1 ? 'persona' : 'personas'}, todo correcto.`
        : `${filas} ${filas === 1 ? 'persona' : 'personas'}, ${conErrores} con errores. Corrige el fichero y vuelve a subirlo: hasta que no esté todo bien no se crea a nadie.`,
    ignoradas: (columnas: string) => `Columnas que no reconocemos y se ignoran: ${columnas}.`,
    tabla: 'Personas del fichero',
    fila: 'Fila',
    errores: 'Problemas',
    bien: 'Correcta',
    crear: (n: number) => `Crear ${n} ${n === 1 ? 'persona' : 'personas'}`,
    creando: 'Creando…',
    hecho: (creadas: number) => `${creadas} ${creadas === 1 ? 'persona creada' : 'personas creadas'}.`,
    invitaciones: (enCamino: number, pendientes: number) =>
      pendientes > enCamino
        ? `Les estamos mandando el correo para elegir contraseña a ${enCamino}. Las otras ${pendientes - enCamino} esperan: salen como mucho 50 invitaciones al día. Mañana, pulsa «Mandar las invitaciones» en esta página.`
        : `Les estamos mandando el correo para elegir contraseña.`,
    otro: 'Importar otro fichero',
  },
  /** Las invitaciones de alta que esperan cupo (ADR 052). */
  invitaciones: {
    aviso: (pendientes: number, cupo: number) =>
      cupo > 0
        ? `${pendientes} ${pendientes === 1 ? 'persona espera' : 'personas esperan'} el correo de alta. Hoy pueden salir ${Math.min(cupo, pendientes)}.`
        : `${pendientes} ${pendientes === 1 ? 'persona espera' : 'personas esperan'} el correo de alta. Hoy ya no caben más: vuelve mañana.`,
    enviar: 'Mandar las invitaciones',
    enviando: (n: number) => `Mandando ${n} ${n === 1 ? 'invitación' : 'invitaciones'}…`,
    pendiente: 'Sin invitar todavía',
  },
  /** Cambiar el rol (ADR 052). */
  cambioDeRol: {
    boton: 'Cambiar rol',
    titulo: (nombre: string) => `Rol de ${nombre}`,
    etiqueta: 'Rol',
    explicacion:
      'Gestor: lleva un equipo y aprueba sus ausencias. RRHH: además, gestiona la plantilla, corrige fichajes y saca informes. Administración: además, configura la empresa y nombra a otros. Vale en cuanto lo guardes.',
    guardar: 'Guardar',
    hecho: (nombre: string, rol: string) => `${nombre} es ahora ${rol}.`,
  },
  /** El segundo factor (ADR 051). */
  conSegundoFactor: 'Segundo factor',
  quitarSegundoFactor: 'Quitar segundo factor',
  quitarSegundoFactorTitulo: (nombre: string) => `¿Quitar el segundo factor a ${nombre}?`,
  quitarSegundoFactorTexto:
    'Hazlo solo si ha perdido el móvil y sus códigos de recuperación. Podrá entrar solo con su contraseña y volver a activarlo; le avisaremos por correo.',
  segundoFactorQuitado: (nombre: string) => `${nombre} ya puede entrar sin segundo factor.`,
  bajaHecha: (quien: string) => `${quien} está de baja.`,
  altaHecha: (quien: string) => `${quien} vuelve a estar de alta.`,

  departamentos: {
    titulo: 'Departamentos',
    vacio: 'Todavía no hay ninguno.',
    nuevo: 'Nombre del departamento',
    anadir: 'Añadir',
    nombreVacio: 'Escribe el nombre del departamento.',
    creado: 'Departamento creado.',
    personas: (n: number) => (n === 1 ? '1 persona' : `${n} personas`),
    renombrar: 'Renombrar',
    renombrarTitulo: (nombre: string) => `Renombrar «${nombre}»`,
    renombrado: 'Departamento renombrado.',
    borrar: 'Borrar',
    borrarTitulo: (nombre: string) => `¿Borrar «${nombre}»?`,
    borrarTexto: 'Solo se pueden borrar los departamentos que no tienen a nadie dentro.',
    borrado: 'Departamento borrado.',
    conGente: 'Para borrarlo, primero cambia de departamento a quien está dentro (desde su ficha en Plantilla).',
    sinAsignar: 'Sin departamento',
    sinAsignarAyuda: 'Personas de la plantilla que todavía no tienen departamento.',
    asignar: 'Asignar a…',
    asignarA: (quien: string) => `Departamento de ${quien}`,
    asignada: (quien: string, dep: string) => `${quien} ya está en ${dep}.`,
  },
} as const;
