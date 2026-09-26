# 28. Push con FCM: cuelga del aviso, dice solo de qué va y se enciende en el móvil

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Hasta la Fase B5, un aviso llegaba por dos canales: dentro de la app, que solo
se ve al abrirla, y por correo. Faltaba lo que la gente espera de una app: que
el móvil avise cuando pasa algo, con la app cerrada.

La alternativa sin Google era una tarea periódica del móvil que preguntara cada
15 minutos por avisos nuevos. Se descartó: Android retrasa esas tareas horas con
el móvil en reposo, y en el plan gratuito de Render cada consulta despertaría al
backend dormido (30-50 s de arranque) o lo mantendría despierto siempre. Para
avisos con horas de retraso ya está el correo.

## Decisión

### Firebase Cloud Messaging, y solo eso

FCM es gratuito, sin límite de mensajes y sin tarjeta. Del resto de Firebase no
se usa nada. **Google Analytics está apagado en el proyecto y la app no lleva su
SDK**: medir a la plantilla pediría su consentimiento.

### El push cuelga del aviso, no de cada evento

`NotificationListener` tiene una veintena de métodos que publican aviso y
correo. Añadir una tercera llamada a cada uno garantizaría que el próximo evento
se olvidara de una. Todo aviso pasa por `NoticeServiceImpl.publicar`, que al
guardarlo publica `NoticePublished`; `PushSender` lo escucha en el AFTER_COMMIT
de esa transacción, en otro hilo. Todo aviso futuro es también un push sin tocar
nada, y un aviso que no llega a guardarse no avisa.

### El push dice de qué va, no qué pasa

Un push viaja por los servidores de Google, y un aviso puede ser delicado («tu
ausencia ha sido rechazada: …»). El push lleva un texto genérico por tipo
(`TextoDePush`: «Hay novedades en tus ausencias») y la ruta de la pantalla. El
detalle se lee al abrir la app, por el canal de siempre. Es el criterio que ya
seguían los avisos del canal de denuncias, llevado a todos. `TextoDePush` es un
`switch` sin `default`: un tipo de aviso nuevo no compila hasta que alguien
decide qué dice su push.

### Mensajes solo de datos

Sin bloque `notification`, el mensaje llega siempre a
`NxTimeMessagingService`, con la app abierta o no, y la app decide si lo enseña.
Con ese bloque, Android lo pintaría por su cuenta en segundo plano, **también en
un móvil del que ya se cerró la sesión**. Prioridad alta, para que llegue con el
móvil en reposo, y un día de vida: un aviso de hace dos días ya está en la app.

### Se enciende en el móvil, no en la cuenta

Los push van **apagados por defecto**, como el recordatorio de fichar, y se
encienden en Ajustes, que es cuando se pide el permiso de Android: uno pedido al
arrancar, sin contexto, se deniega. Hasta entonces, Firebase no pide token a
Google (`firebase_messaging_auto_init_enabled=false`). Es un ajuste del móvil y
no de la cuenta: no hay columna `push_activado` en `usuarios`, porque encenderlo
es registrar el token y apagarlo, borrarlo. Una bandera en el servidor sería una
segunda verdad.

### El token cambia de dueño; al salir, se olvida

`dispositivos_push.token` es único. En un móvil compartido, cuando entra otra
persona el token pasa a ser suyo: ese móvil deja de recibir los avisos de la
anterior. Al cerrar sesión, por cualquiera de los tres caminos (salir, sesión
caducada, cerrar todas), la app invalida el token en Google. No llama al
servidor, porque ya no tiene sesión; no hace falta: en el siguiente envío Google
responde `UNREGISTERED` y `PushSender` borra la fila. Y mientras tanto la app no
enseña ningún push sin sesión.

Solo se borran los tokens con `UNREGISTERED` o `SENDER_ID_MISMATCH`, **no con
`INVALID_ARGUMENT`**, aunque FCM también lo use para un token malformado: sale
igual si el mensaje está mal construido. Tratarlo como token muerto borraría los
de todo el mundo por un fallo nuestro.

### Sin credenciales, sin push y sin error

`FirebaseConfig` solo existe si hay `FIREBASE_CREDENTIALS_JSON`, igual que
Sentry sin DSN. En local, en CI y en cualquier clon del repositorio la aplicación
arranca y funciona; solo le falta el push. La credencial es la clave de una
cuenta de servicio que puede mandar push a toda la plantilla: va solo en el panel
de Render, en base64 porque Render maltrata los saltos de línea de la clave
privada, y el `.gitignore` la tapa por si alguien la descarga dentro del
proyecto. El `google-services.json` de la app, en cambio, **sí se versiona**: no
es secreto (va dentro del APK), y sin él nadie podría compilar.

## Consecuencias

- V34: `dispositivos_push`, con `plataforma IN ('ANDROID','WEB','IOS')` desde el
  principio. Dos rutas: `POST /api/v1/dispositivos-push` y `/baja`, con el token
  en el cuerpo y no en la URL (las URL acaban en los registros de acceso).
- Los tokens son dato personal: el borrado de datos se los lleva y la
  exportación los incluye.
- A quien está dado de baja no se le manda nada, aunque su móvil siga
  registrado.
- Un móvil sin servicios de Google (Huawei recientes) no recibe push. Le siguen
  llegando el aviso en la app y el correo.
- **Web Push queda preparado, no hecho.** Faltaría el `firebase-messaging-sw.js`,
  la clave VAPID (`VITE_FIREBASE_VAPID_KEY`), el `getToken()` del SDK web y
  registrar con `plataforma=WEB`. Safari en iOS solo entrega push a una web
  instalada como app.
- Al añadir esta fase, la suite del backend se quedó sin memoria: cada test de
  integración crea su propia base, así que su contexto de Spring nunca se
  reutiliza, pero se guardaba en caché. `spring.test.context.cache.maxSize=8`.
- **El primer despliegue con la credencial se cayó** (26/09/2026) con
  `ClassNotFoundException: JacksonFactory`. Al excluir `google-cloud-storage`
  para quitar el XML, se fue también `google-http-client-jackson2`, que llegaba de
  rebote y Firebase sí usa para leer la credencial. Ningún test lo vio: ninguno
  arrancaba Firebase con una credencial bien formada. Render mantuvo la versión
  anterior, así que no hubo caída del servicio. Se arregló en tres frentes: la
  dependencia explícita; `FirebaseConfigTest` genera una credencial de mentira
  bien formada e inicializa Firebase de verdad; y `FirebaseConfig` captura
  también `LinkageError`, para que un fallo así deje el backend sin push en vez de
  sin arrancar. Se comprobó arrancando el jar empaquetado con esa credencial,
  que es lo que hace Render.
