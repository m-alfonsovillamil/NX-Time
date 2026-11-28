# 64. Versiones de la app que ya no se pueden usar

**Estado:** aceptada · **Fecha:** noviembre 2026 · Plan P, paso P6

## Contexto

Hasta ahora la app se repartía como APK desde GitHub, y la API no sabía con qué
versión de la app hablaba. Con Google Play eso cambia dos veces:

- **Habrá versiones mezcladas.** Play actualiza cuando quiere el móvil, y hay
  quien tiene las actualizaciones automáticas apagadas. El día que un cambio de
  la API rompa a una versión vieja, quien la tenga verá errores sin entender por
  qué, y no hay manera de decirle «actualiza».
- **El cambio de nombre cambia el paquete** (`applicationId`, ver
  [CAMBIO-DE-NOMBRE](../CAMBIO-DE-NOMBRE.md)). Para Android, la app nueva es
  otra app: la vieja no se actualiza, se queda instalada al lado y sigue
  funcionando, y su dueño puede seguir fichando con ella sin saber que hay otra.

## Decisión

- **La app dice quién es en cada petición**: `X-App-Version` (su `versionCode`) y
  `X-App-Id` (su `applicationId`). Lo pone el cliente HTTP base, así que va
  también en el login y en la renovación de la sesión.
- **El backend puede retirar versiones** con dos variables, apagadas por
  defecto: `APP_VERSION_MINIMA` (un `versionCode`; 0 = ninguna) y
  `APP_IDS_RETIRADOS` (paquetes separados por comas; la variante `.dev` de cada
  uno cuenta como él). `VersionDeLaAppFilter`, el primero de la cadena de
  seguridad, responde **426 Upgrade Required** con `codigo`
  `APP_DESACTUALIZADA` o `APP_RETIRADA` y `urlDescarga` (la de
  `APP_DOWNLOAD_URL`). Va antes que la autenticación: con un 401, la app
  volvería a pedir que se entre, una y otra vez.
- **Lo que sigue abierto siempre**: entrar, renovar y cerrar la sesión, y
  **mandar los fichajes hechos sin conexión** (`/api/v1/fichaje/sin-conexion`).
  Un fichaje guardado en el móvil es un registro de jornada: que la app sea vieja
  no puede hacer que se pierda. La app vieja, al abrirse con red, los manda antes
  de quedarse en la pantalla de actualizar.
- **La app enseña una pantalla que no se puede saltar**: «Actualiza la app»
  (o «Esta app ya no se usa» si el paquete está retirado), con un botón al enlace
  que manda el servidor y, si quedan fichajes sin enviar, cuántos.
- **Solo la app**: la web siempre es la última y el kiosco es la web. Sin las
  cabeceras, el filtro no hace nada.

## Consecuencias

- Retirar una versión es poner una variable en Render y reiniciar; volver
  atrás, quitarla. No hace falta publicar nada.
- Las versiones de antes de esta (1.26 y anteriores) no mandan las cabeceras:
  no se pueden retirar así. A partir de la 1.27 sí.
- Para el cambio de nombre: publicar la app nueva, esperar a que la plantilla la
  instale, y entonces `APP_IDS_RETIRADOS=com.nxtime.app` (y `APP_DOWNLOAD_URL`
  apuntando a la nueva en Play).
- El enlace de descarga es uno para todos: si un día conviven Play y el APK de
  GitHub, apunta a Play.
