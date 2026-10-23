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
    legal: {
      titulo: 'Información legal',
      descripcion: 'Quién presta el servicio, las condiciones y cómo se tratan los datos.',
    },
    tema: 'Tema',
    temas: { sistema: 'El del sistema', claro: 'Claro', oscuro: 'Oscuro' },

    /** Las cuentas de Google o de Microsoft con las que se entra (ADR 036). */
    vinculadas: {
      titulo: 'Cuentas vinculadas',
      explicacion:
        'Entra con tu cuenta de Google o de Microsoft en vez de con la contraseña. La contraseña sigue valiendo.',
      vincular: (proveedor: string) => `Vincular ${proveedor}`,
      desvincular: 'Desvincular',
      desvincularDe: (proveedor: string) => `Desvincular ${proveedor}`,
      sinVincular: 'Sin vincular',
      vinculadaEl: (dia: string) => `Vinculada el ${dia}`,
      confirmarTitulo: (proveedor: string) => `¿Desvincular ${proveedor}?`,
      confirmarTexto:
        'Dejarás de poder entrar con esa cuenta. Tu contraseña sigue valiendo, y puedes volver a vincularla cuando quieras.',
      hecho: 'Cuenta vinculada: ya puedes entrar con ella.',
      /** Por qué no se ha vinculado. La clave es lo que manda el servidor en `?sso=…`. */
      motivos: {
        cancelado: 'No has terminado de vincular la cuenta.',
        'ya-vinculada': 'Esa cuenta ya está vinculada a otra persona de NX Time.',
        'ya-tiene-otra': 'Ya tienes vinculada otra cuenta de ese proveedor. Desvincúlala antes.',
        'sin-sesion': 'Tu sesión había caducado. Vuelve a intentarlo.',
        'no-disponible': 'Ese proveedor no está disponible ahora mismo.',
        fallo: 'No se ha podido vincular la cuenta. Vuelve a intentarlo.',
      },
    },

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
    /** El segundo factor (ADR 051): un código de una aplicación de autenticación al entrar. */
    segundoFactor: {
      titulo: 'Segundo factor',
      descripcion:
        'Al entrar con tu contraseña, te pediremos también un código de 6 cifras de una aplicación de autenticación (Google Authenticator, Microsoft Authenticator, tu gestor de contraseñas…). Entrar con Google o Microsoft no lo pide: ya lo hace el proveedor.',
      activo: 'Activado',
      desde: (dia: string) => `Desde el ${dia}`,
      inactivo: 'No lo tienes activado.',
      obligatorio: (motivo: string) => `${motivo}: tienes que tenerlo activado, y no lo puedes quitar.`,
      quedan: (n: number) =>
        n === 1 ? 'Te queda 1 código de recuperación.' : `Te quedan ${n} códigos de recuperación.`,
      pocos: 'Te quedan pocos códigos de recuperación: genera otros.',
      activar: 'Activarlo',
      desactivar: 'Desactivarlo',
      codigosNuevos: 'Códigos de recuperación nuevos',

      activacion: {
        titulo: 'Activar el segundo factor',
        paso1: 'Abre tu aplicación de autenticación, añade una cuenta y escanea este código.',
        qr: 'Código QR para añadir NX Time a la aplicación de autenticación',
        sinCamara: '¿No puedes escanearlo? Escribe esta clave en la aplicación:',
        copiarClave: 'Copiar la clave',
        claveCopiada: 'Clave copiada.',
        paso2: 'Después, escribe el código que te da la aplicación y tu contraseña.',
        codigo: 'Código de la aplicación',
        contrasena: 'Tu contraseña',
        faltaCodigo: 'Escribe el código de 6 cifras que te da la aplicación.',
        faltaContrasena: 'Escribe tu contraseña.',
        confirmar: 'Activar',
        preparando: 'Preparando…',
      },

      recuperacion: {
        titulo: 'Tus códigos de recuperación',
        explicacion:
          'Guárdalos en un sitio seguro (tu gestor de contraseñas, o impresos). Cada uno sirve una vez para entrar si pierdes el móvil. No los volverás a ver.',
        copiar: 'Copiarlos',
        copiados: 'Códigos copiados.',
        descargar: 'Descargarlos',
        guardados: 'Los he guardado',
        listo: 'Listo',
        fichero: (codigos: readonly string[]) =>
          `Códigos de recuperación de NX Time\nCada uno sirve una vez.\n\n${codigos.join('\n')}\n`,
      },

      desactivacion: {
        titulo: '¿Desactivar el segundo factor?',
        explicacion: 'Volverá a bastar con tu contraseña para entrar. Escríbela, y un código de la aplicación o de recuperación.',
        codigo: 'Código',
        contrasena: 'Tu contraseña',
        confirmar: 'Desactivar',
        hecho: 'Segundo factor desactivado.',
      },

      regenerar: {
        titulo: 'Códigos de recuperación nuevos',
        explicacion: 'Los que tienes ahora dejarán de valer. Escribe un código de la aplicación.',
        codigo: 'Código de la aplicación',
        confirmar: 'Generar',
      },

      /** La pantalla de quien está obligado y aún no lo tiene. */
      obligado: {
        titulo: 'Activa el segundo factor',
        explicacion: (motivo: string) =>
          `${motivo}, así que para entrar en NX Time necesitas, además de la contraseña, un código de una aplicación de autenticación. Actívalo ahora: son dos minutos.`,
        sinMotivo: 'Para seguir, necesitas activar el segundo factor.',
        salir: 'Salir',
      },
    },

    cerrarTodas: {
      boton: 'Cerrar sesión en todos los dispositivos',
      detalle:
        'Se cerrará también aquí, y en los demás sitios donde la tengas abierta, al momento.',
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
