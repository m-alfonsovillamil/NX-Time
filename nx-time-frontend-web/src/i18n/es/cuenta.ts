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
    explicacion: 'Lo que ha pasado con tus ausencias, tus fichajes y tu cuenta. Abrir uno te lleva a su sitio.',
    resumen: 'Sin leer',
    alDia: 'Estás al día',
    ajustesTitulo: 'Que no se te pase ninguno',
    ajustesTexto: 'Puedes recibirlos en este navegador aunque tengas la web cerrada.',
    ajustesEnlace: 'Ajustes de notificaciones',
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
    indice: 'Secciones de los ajustes',
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

    notificaciones: {
      titulo: 'Notificaciones',
      encendidas: 'Encendidas',
      encender: 'Recibir notificaciones aquí',
      apagar: 'Dejar de recibirlas aquí',
      detalle: {
        'sin-configurar': 'Esta instalación de NX Time no tiene configuradas las notificaciones push.',
        'instalar-primero':
          'En el iPhone, Safari solo avisa a las webs añadidas a la pantalla de inicio. Pulsa Compartir y «Añadir a pantalla de inicio», abre NX Time desde ese icono y vuelve aquí.',
        'no-soportado': 'Este navegador no admite notificaciones push. Los avisos te siguen llegando a la campana y por correo.',
        bloqueado:
          'Las notificaciones de NX Time están bloqueadas en este navegador. Para recibirlas, permítelas en la configuración del sitio (el candado de la barra de direcciones).',
        apagado:
          'Recibe un aviso en este navegador cuando haya novedades, aunque no tengas NX Time abierto. Solo dice de qué va («Hay novedades en tus ausencias»); el detalle lo ves al abrirlo.',
        encendido:
          'Este navegador te avisa cuando hay novedades. Se deja de avisar al cerrar la sesión, y vuelve al entrar.',
      },
      sinPermiso: 'No has dado permiso para las notificaciones.',
      sinContestar: 'El navegador no ha recibido respuesta a su pregunta. Vuelve a intentarlo y pulsa «Permitir».',
      error: 'No se han podido activar. Inténtalo de nuevo en un rato.',
      reintentar: 'Volver a intentarlo',
      apagando: 'Apagando…',
      /** Lo que dice el botón mientras va por cada paso. */
      pasos: {
        permiso: 'Esperando tu permiso…',
        sdk: 'Activando…',
        'service-worker': 'Activando…',
        token: 'Activando…',
        servidor: 'Guardando este navegador…',
      },
      // Chrome a veces no enseña la pregunta: la deja en un icono de la barra
      // de direcciones (la «interfaz silenciosa»), y la página no lo puede saber.
      permisoSinVer:
        '¿No ves la pregunta? Puede que el navegador la haya dejado en un icono de la barra de direcciones (una campana o el candado): púlsalo y permite las notificaciones.',
      /** Qué falló, según el paso. */
      fallos: {
        permiso: 'No has dado permiso para las notificaciones.',
        sdk: 'No se ha podido cargar el servicio de avisos. Comprueba la conexión y vuelve a intentarlo.',
        'service-worker': 'El navegador no ha podido preparar los avisos. Recarga la página y vuelve a intentarlo.',
        token:
          'El servicio de avisos no ha respondido. Si usas Brave u otro navegador que bloquea los servicios de Google, permítelos para las notificaciones o prueba con Chrome, Edge o Firefox.',
        servidor: 'NX Time no ha podido guardar este navegador. Vuelve a intentarlo en un rato.',
      },
      detalleTecnico: (texto: string) => `Detalle: ${texto}`,
    },

    enLaApp: 'Solo en la app',
    enLaAppDetalle:
      'El recordatorio de fichar y entrar con huella están en la app Android: el recordatorio necesita avisarte a una hora aunque no hayas abierto nada, y la huella en la web serían las passkeys, que son otra fase.',
  },
} as const;
