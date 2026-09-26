/** Lo que no es de ninguna pantalla: errores, estados de carga y páginas de error. */
export const comun = {
  app: {
    nombre: 'NX Time',
    cargando: 'Cargando…',
    reintentar: 'Reintentar',
    cerrar: 'Cerrar',
    cancelar: 'Cancelar',
  },

  listas: {
    cargandoMas: 'Cargando más…',
    cargarMas: 'Cargar más',
    falloAlCargarMas: 'No se ha podido cargar el resto de la lista.',
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
    descarga: 'No se ha podido descargar el fichero.',
  },

  noEncontrado: {
    titulo: 'Esta página no existe',
    volver: 'Volver a mi jornada',
  },

  sinPermiso: {
    titulo: 'Esta página no es para tu cuenta',
    detalle:
      'Tu cuenta no tiene el permiso que hace falta para verla. Si crees que debería tenerlo, pídeselo a quien administra la empresa.',
    volver: 'Volver a mi jornada',
  },
} as const;
