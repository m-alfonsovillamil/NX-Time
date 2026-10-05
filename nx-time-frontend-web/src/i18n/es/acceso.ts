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
