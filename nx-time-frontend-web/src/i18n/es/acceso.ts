/** Entrar, recuperar el acceso y registrar una empresa. Los textos siguen a los de la app. */
export const acceso = {
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

  contrasenas: {
    corta: 'La contraseña debe tener al menos 8 caracteres.',
    larga: 'La contraseña no puede pasar de 72 caracteres.',
    noCoinciden: 'Las dos contraseñas no coinciden.',
  },
} as const;
