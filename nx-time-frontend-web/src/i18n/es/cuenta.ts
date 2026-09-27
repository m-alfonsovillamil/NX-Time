/** Avisos, mi perfil y ajustes. Los textos siguen a los de la app. */
export const cuenta = {
  avisos: {
    titulo: 'Avisos',
    vacioTitulo: 'No tienes avisos',
    vacioTexto: 'Aquí aparecerán las novedades sobre tus ausencias y tu cuenta.',
    marcarTodos: 'Marcar todos como leídos',
    sinLeer: 'Sin leer',
    irAlDestino: 'Ver',
    todosLeidos: 'Todos los avisos marcados como leídos.',
  },

  perfil: {
    titulo: 'Mi perfil',
    personales: 'Datos personales',
    laborales: 'Datos laborales',
    editar: 'Editar',
    guardar: 'Guardar',
    guardado: 'Datos guardados.',
    nombre: 'Nombre',
    apellidos: 'Apellidos',
    fechaNacimiento: 'Fecha de nacimiento',
    puesto: 'Puesto',
    email: 'Correo electrónico',
    departamento: 'Departamento',
    rol: 'Rol',
    jornada: 'Jornada semanal',
    jornadaValor: (horas: string) => `${horas} horas`,
    vacaciones: 'Vacaciones de este año',
    vacacionesValor: (dias: number) => `${dias} días`,
    soloRrhh: 'Estos datos los gestiona Recursos Humanos.',
    sinDato: '—',
    nombreObligatorio: 'El nombre no puede quedarse vacío.',
    fechaFutura: 'La fecha de nacimiento tiene que ser anterior a hoy.',
    roles: { EMPLEADO: 'Empleado', GESTOR: 'Gestor', RRHH: 'Recursos Humanos', ADMIN: 'Administración' } as Record<
      string,
      string
    >,

    foto: {
      cambiar: 'Cambiar la foto',
      subida: 'Foto actualizada.',
      alternativa: (nombre: string) => `Foto de ${nombre}`,
      ayuda: 'JPG o PNG, hasta 5 MB. Se guarda recortada a 256 × 256.',
    },
    cv: {
      titulo: 'Mi currículum',
      vacio: 'Todavía no has subido ninguno.',
      detalle: (nombre: string, fecha: string) => `${nombre} · subido el ${fecha}`,
      subir: 'Subir mi currículum',
      reemplazar: 'Reemplazar el currículum',
      abrir: 'Descargar',
      borrar: 'Borrar',
      borrado: 'Currículum borrado.',
      subido: 'Currículum guardado.',
      ayuda: 'Solo PDF, hasta 5 MB. Se comprueba el contenido del fichero, no su extensión.',
      usoEnOfertas: 'Es el que se adjunta cuando te presentas a una oferta interna.',
    },
    adjuntoVacio: 'El fichero elegido está vacío.',
    adjuntoGrande: 'El fichero pasa de 5 MB.',
  },

  ajustes: {
    titulo: 'Ajustes',

    apariencia: 'Apariencia',
    tema: 'Tema',
    temas: { sistema: 'El del sistema', claro: 'Claro', oscuro: 'Oscuro' },

    cuenta: 'Cuenta',
    contrasena: {
      boton: 'Cambiar contraseña',
      titulo: 'Cambiar contraseña',
      actual: 'Contraseña actual',
      nueva: 'Nueva contraseña',
      repetir: 'Repetir nueva contraseña',
      guardar: 'Actualizar contraseña',
      noCoinciden: 'Las dos contraseñas nuevas no coinciden.',
      corta: 'La contraseña debe tener al menos 8 caracteres.',
      larga: 'La contraseña no puede pasar de 72 caracteres.',
      faltaActual: 'Escribe tu contraseña actual.',
      cambiada: 'Contraseña cambiada.',
    },
    cerrarTodas: {
      boton: 'Cerrar sesión en todos los dispositivos',
      detalle:
        'Se cerrará también aquí. Las sesiones abiertas en otros sitios dejan de poder renovarse y caducan en unos minutos.',
      confirmar: 'Cerrar todas',
      titulo: '¿Cerrar la sesión en todos los dispositivos?',
    },

    privacidad: 'Privacidad',
    misDatos: {
      titulo: 'Descargar mis datos',
      detalle:
        'Todo lo que NX Time guarda sobre ti: perfil, fichajes, ausencias, correcciones, horas extra y avisos. El PDF es para leerlo; el JSON, para llevártelo a otro servicio.',
      pdf: 'PDF',
      json: 'JSON',
    },
    borrado: {
      titulo: 'Borrar mis datos',
      detalle:
        'Puedes pedir que se borren tus datos personales. Lo revisa RRHH, que tiene un mes para responder. Tu registro horario no se borra enseguida: la empresa tiene que guardarlo cuatro años.',
      pedir: 'Pedir el borrado',
      pendiente: (fecha: string) => `Lo pediste el ${fecha}. Está pendiente de revisar.`,
      rechazado: (motivo: string) => `No se ejecutó: ${motivo}`,
      retirar: 'Retirar la solicitud',
      confirmarTitulo: '¿Pedir el borrado de tus datos?',
      confirmarDetalle: [
        'Si RRHH lo ejecuta, tu cuenta se desactivará y no podrás volver a entrar. Se borrarán tu foto, tu CV, tu fecha de nacimiento, tus candidaturas y tus avisos.',
        'Tus fichajes, ausencias y correcciones se conservan con tu nombre cuatro años desde tu último fichaje, porque lo exige la ley, y después se anonimizan.',
        'Si quieres una copia, descárgala antes.',
      ],
      motivo: 'Motivo (opcional)',
      enviada: 'Solicitud enviada.',
      retirada: 'Solicitud retirada.',
    },

    enLaApp: 'Solo en la app',
    enLaAppDetalle:
      'El recordatorio de fichar, entrar con huella y las notificaciones en el móvil están en la app Android: un navegador no puede avisarte sin estar abierto.',
  },
} as const;
