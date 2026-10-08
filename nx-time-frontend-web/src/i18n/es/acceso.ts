/** Entrar, recuperar el acceso y registrar una empresa. Los textos siguen a los de la app. */
export const acceso = {
  /** El panel de marca de las pantallas de acceso, en escritorio. */
  marca: {
    etiqueta: 'Qué es NX Time',
    lema: 'El registro de jornada que se lleva solo.',
    puntos: [
      { icono: 'reloj', titulo: 'Fichar en un toque', texto: 'Desde el navegador, el móvil o una tablet en la entrada.' },
      { icono: 'vacaciones', titulo: 'Ausencias sin papeles', texto: 'Las vacaciones y los permisos se piden y se aprueban aquí.' },
      {
        icono: 'verificado',
        titulo: 'Lo que pide la ley',
        texto: 'El registro que exige el RD-ley 8/2019, guardado cuatro años y a prueba de cambios.',
      },
    ],
  },

  login: {
    titulo: 'Entrar',
    subtitulo: 'Registro horario',
    email: 'Correo electrónico',
    contrasena: 'Contraseña',
    entrar: 'Entrar',
    entrando: 'Entrando…',
    faltaEmail: 'Escribe tu correo electrónico.',
    faltaContrasena: 'Escribe tu contraseña.',
    recuperar: '¿Has olvidado tu contraseña o es tu primera vez?',
    registrarEmpresa: 'Registrar una empresa',
  },

  /** Entrar con Google o con Microsoft (ADR 036). Solo si el servidor los tiene configurados. */
  sso: {
    separador: 'o',
    etiqueta: 'Entrar con otra cuenta',
    entrarCon: (proveedor: string) => `Entrar con ${proveedor}`,
    /**
     * Por qué no se ha entrado. La clave es lo que el servidor manda en la URL
     * de vuelta (`?sso=…`); una que esta versión no conozca cae en `fallo`.
     */
    motivos: {
      cancelado: 'No has terminado de entrar. Puedes volver a intentarlo.',
      'sin-cuenta':
        'No hay ninguna cuenta de NX Time con el correo de esa cuenta. Pide a tu empresa que te dé de alta con ese correo, o entra con el que te dieron.',
      'correo-sin-verificar':
        'No hemos podido comprobar que el correo de esa cuenta sea tuyo. Entra con tu contraseña y vincula la cuenta desde Ajustes.',
      'cuenta-inactiva': 'Tu cuenta de NX Time está dada de baja. Habla con tu empresa.',
      'ya-tiene-otra':
        'Tu cuenta de NX Time ya está vinculada a otra cuenta de ese proveedor. Entra con esa, o con tu contraseña.',
      'no-disponible': 'Ese acceso no está disponible ahora mismo. Entra con tu contraseña.',
      fallo: 'No se ha podido completar el acceso. Vuelve a intentarlo, o entra con tu contraseña.',
    },
  },

  recuperar: {
    titulo: 'Elegir contraseña',
    explicacion:
      'Escribe tu correo y te mandaremos un código para elegir una contraseña nueva. Si acabas de recibir el correo de bienvenida, pulsa «Ya tengo un código».',
    enviarCodigo: 'Enviarme un código',
    yaTengoCodigo: 'Ya tengo un código',
    codigoEnviado: (email: string) => `Si ${email} tiene una cuenta, le hemos enviado un código. Caduca en 15 minutos.`,
    codigoExplicacion: 'Escribe el código que te llegó por correo y elige tu contraseña.',
    codigo: 'Código de 6 dígitos',
    codigoIncompleto: 'El código son 6 dígitos.',
    nueva: 'Contraseña nueva',
    repetir: 'Repetir contraseña',
    guardar: 'Guardar contraseña',
    pedirOtro: 'No me ha llegado: pedir otro código',
    hechoTitulo: 'Contraseña guardada',
    hechoTexto:
      'Ya puedes entrar con tu correo y la contraseña nueva. Por seguridad, se han cerrado las sesiones que tuvieras abiertas en otros dispositivos.',
    irALogin: 'Ir a iniciar sesión',
    volver: 'Volver a entrar',
  },

  registro: {
    titulo: 'Registrar empresa',
    explicacion: 'Se creará la empresa y tu cuenta de administrador.',
    empresa: 'Nombre de la empresa',
    nombre: 'Tu nombre',
    apellidos: 'Tus apellidos',
    email: 'Tu correo electrónico',
    contrasena: 'Contraseña',
    contrasenaAyuda: 'Entre 8 y 72 caracteres.',
    crear: 'Crear empresa',
    faltan: 'Rellena todos los campos.',
    yaTengoCuenta: 'Ya tengo cuenta: entrar',
    /**
     * Registrar la empresa con una cuenta de Google o de Microsoft (ADR 038):
     * sin contraseña ni código, porque quién eres lo dice el proveedor.
     */
    sso: {
      etiqueta: 'Registrar con otra cuenta',
      registrarCon: (proveedor: string) => `Registrar con ${proveedor}`,
      comprobando: 'Comprobando tu cuenta…',
      explicacion: (proveedor: string, correo: string) =>
        `Has entrado con tu cuenta de ${proveedor} (${correo}). Ese será tu correo en NX Time, y entrarás con esa cuenta: no hace falta contraseña. Solo falta esto:`,
      otraForma: 'Registrarla con mi correo y una contraseña',
      /**
       * Por qué no se ha podido seguir. La clave es lo que el servidor manda en
       * la URL de vuelta (`?sso=…`); una que esta versión no conozca cae en `fallo`.
       */
      motivos: {
        cancelado: 'No has terminado de entrar con esa cuenta. Puedes volver a intentarlo.',
        'correo-sin-verificar':
          'No hemos podido comprobar que el correo de esa cuenta sea tuyo. Registra la empresa con tu correo y una contraseña.',
        'cuenta-inactiva': 'Ya tienes una cuenta de NX Time con ese correo, y está dada de baja. Habla con tu empresa.',
        'ya-tiene-otra':
          'Ya tienes una cuenta de NX Time con ese correo, vinculada a otra cuenta de ese proveedor. Entra con esa, o con tu contraseña.',
        'no-disponible': 'Ese registro no está disponible ahora mismo. Hazlo con tu correo y una contraseña.',
        caducado: 'El registro ha caducado. Vuelve a empezar con tu cuenta, o hazlo con tu correo y una contraseña.',
        fallo: 'No se ha podido completar. Vuelve a intentarlo, o registra la empresa con tu correo y una contraseña.',
      },
    },
  },

  /** Confirmar el correo con el código, tras registrar la empresa (ADR 034). */
  confirmarCorreo: {
    titulo: 'Confirma tu correo',
    explicacion: (email: string) =>
      `Te hemos mandado un código de 6 cifras a ${email}. Escríbelo para entrar. Si no llega en unos minutos, mira en la carpeta de spam.`,
    codigo: 'Código',
    faltaCodigo: 'El código son 6 cifras.',
    entrar: 'Confirmar y entrar',
    entrando: 'Comprobando…',
    otroCodigo:
      '¿No te ha llegado? Entra con tu correo y tu contraseña y te mandaremos otro, o regístrate otra vez con los mismos datos.',
    volver: 'Volver al inicio',
  },

  contrasenas: {
    corta: 'La contraseña debe tener al menos 8 caracteres.',
    larga: 'La contraseña no puede pasar de 72 caracteres.',
    noCoinciden: 'Las dos contraseñas no coinciden.',
  },
} as const;
