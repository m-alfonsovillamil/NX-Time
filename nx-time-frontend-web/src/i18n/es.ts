/**
 * Todos los textos de la interfaz, en un solo sitio.
 *
 * Ningún componente escribe una cadena literal, por el mismo motivo por el que
 * la app Android tiene `strings.xml` y `MensajeUi`: el día que haga falta otro
 * idioma, el trabajo es traducir este fichero y no releer cada pantalla
 * buscando texto suelto. Y de paso, los mensajes de error se revisan juntos,
 * que es como se nota si alguno está escrito para el programador y no para
 * quien lo va a leer.
 *
 * `as const` para que cada clave sea un tipo: escribir `T.lgin` no compila.
 */
export const T = {
  app: {
    nombre: 'NX Time',
    cargando: 'Cargando…',
    reintentar: 'Reintentar',
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
  },

  fichar: {
    titulo: 'Mi jornada',
    saludo: (nombre: string) => `Hola, ${nombre}`,
    salir: 'Cerrar sesión',

    parado: 'Sin fichar',
    trabajando: 'Trabajando',
    enPausa: 'En pausa',

    entrar: 'Fichar entrada',
    salir_: 'Fichar salida',
    pausar: 'Pausar',
    reanudar: 'Reanudar',

    desde: (hora: string) => `Desde las ${hora}`,
    pausaAcumulada: (texto: string) => `Pausa: ${texto}`,
    noLaborable: (motivo: string) => `Hoy no es laborable: ${motivo}`,
    sinJornada: 'Todavía no has fichado hoy.',
  },

  servidor: {
    despertando: 'El servidor está despertando',
    despertandoDetalle:
      'El plan gratuito de Render apaga el servicio cuando no se usa. La primera petición del día puede tardar hasta tres minutos.',
  },

  errores: {
    datosInvalidos: 'Revisa los datos: hay algo que no es válido.',
    credenciales: 'El correo o la contraseña no son correctos.',
    sesionCaducada: 'Tu sesión ha caducado. Vuelve a entrar.',
    sinPermisos: 'No tienes permiso para hacer esto.',
    noEncontrado: 'No se ha encontrado lo que buscabas.',
    conflicto: 'Esa operación choca con el estado actual.',
    demasiadosIntentos: 'Demasiados intentos. Espera un momento y vuelve a probar.',
    servidor: 'El servidor ha fallado. Inténtalo dentro de un momento.',
    red: 'No hay conexión a internet. Comprueba tu red.',
    sinServidor: 'No se ha podido contactar con el servidor. Inténtalo dentro de un momento.',
    inesperado: 'Ha ocurrido un error inesperado.',
  },

  noEncontrado: {
    titulo: 'Esta página no existe',
    volver: 'Volver a mi jornada',
  },
} as const;
