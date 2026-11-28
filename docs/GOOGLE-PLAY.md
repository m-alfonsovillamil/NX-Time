# Publicar en Google Play

Lo que hay que rellenar en Play Console para publicar la app, sacado de lo que
la app hace de verdad (manifiesto, dependencias y pantallas). Revisado el
28/11/2026 (plan P).

Aquí, `{producto}` es el nombre de la app y `{web}` su web: los dos salen de
`application.titular` y cambian con `scripts/dev/cambiar-nombre.py`.
**Antes de nada, el cambio de nombre** ([CAMBIO-DE-NOMBRE](CAMBIO-DE-NOMBRE.md)):
el paquete (`applicationId`) no se puede cambiar una vez publicada.

## 1. Lo que tiene que estar antes

| | Estado |
|---|---|
| Nombre y paquete definitivos | Pendiente: [CAMBIO-DE-NOMBRE](CAMBIO-DE-NOMBRE.md) |
| Página pública para pedir el borrado de la cuenta | Hecha: `{web}/legal/borrar-cuenta` (también se pide desde la app, en Ajustes) |
| Política de privacidad pública | Hecha: `{web}/legal/privacidad` |
| Nivel de API | `targetSdk 36` (Android 16): cumple lo que Play pide en 2026 |
| Páginas de memoria de 16 KB | Cumple: la app no tiene código nativo propio, y las dos `.so` de androidx están alineadas. Lo comprueba `scripts/dev/comprobar-16kb.py` en cada release |
| Retirar versiones viejas | Hecho desde la 1.27 ([ADR 064](adr/064-versiones-retiradas-de-la-app.md)): `APP_VERSION_MINIMA` en Render |
| Icono de 512 px y gráfico de funciones de 1024×500 | Se generan con `npm run iconos` (web) desde los SVG de la marca: `nx-time-frontend-android/src/main/ic_launcher-playstore.png` y `docs/play/grafico-de-funciones.png` |
| Capturas | Después del cambio de nombre (ver el punto 4) |
| Cuenta para la revisión de Google | Miguel: en producción, la cuenta de administrador de la empresa de demostración, **sin segundo factor** y con datos (fichajes, ausencias) para que se vea algo |
| Cuenta de desarrollador | Miguel: ver el punto 6 |

## 2. El AAB, firmado

```bash
scripts/dev/publicar-app.sh            # compila main en un worktree aparte y lo comprueba todo
```

Deja en `~/nxtime-releases/<versión>/` el AAB (para Play), el APK (para
GitHub) y `mapping.txt`, después de comprobar que el `versionCode` sube, que los
dos van firmados con la clave de `assetlinks.json` y que pasan lo de 16 KB. No
publica nada.

En **Play App Signing**, la primera vez: **subir la clave con la que se firma
hoy el APK** como clave de firma de la app («Use a key from Android Studio /
Java keystore»), y no dejar que Google genere otra. Si no, la app de Play y el
APK de GitHub llevarían firmas distintas: quien tenga uno no podría actualizar
al otro sin desinstalar (y perder los fichajes sin enviar), y los botones de los
correos solo abrirían una de las dos (App Links). Si aun así la firma acaba
siendo otra, añadir su huella SHA-256 (*Play App Signing* la enseña) a
`nx-time-frontend-web/public/.well-known/assetlinks.json`.

Con cada versión, subir también `mapping.txt` como archivo de desofuscación,
para que los fallos de Play Console salgan legibles.

## 3. Ficha de la tienda

- **Nombre**: `{producto}` (30 caracteres como mucho).
- **Categoría**: Empresa. **Etiquetas**: control horario, registro de jornada.
- **Descripción corta** (80 caracteres como mucho):
  `Registro de jornada para tu empresa: fichar, pausas, ausencias y vacaciones.`
- **Descripción larga**, punto de partida:

  > El registro de jornada que exige la ley, para la plantilla y para quien la
  > gestiona.
  >
  > - Ficha la entrada, las pausas y la salida con un toque, también desde el
  >   widget de la pantalla de inicio.
  > - Sin cobertura también: el fichaje se guarda en el móvil y se envía al volver la red.
  > - Pide vacaciones, medios días y ausencias, adjunta el justificante y consulta los días que te quedan.
  > - Firma tu registro de cada mes.
  > - Si eres responsable: aprueba ausencias y correcciones, revisa las horas
  >   extra y mira quién está trabajando ahora.
  >
  > Necesitas una cuenta de tu empresa. Si tu empresa todavía no usa {producto},
  > regístrala desde la app o en {web}.

- **Icono** y **gráfico de funciones**: los del punto 1.
- **Sitio web**: `{web}`. **Correo de contacto**: el de privacidad del titular.

## 4. Capturas

Play pide al menos dos de teléfono (lado corto de 1080 px o más; mejor cuatro a
ocho) y, para salir bien en tablets, de 7 y 10 pulgadas. Se sacan **después del
cambio de nombre**, una sola vez, con la demo en una base desechable:

```bash
scripts/dev/backend-desechable.sh capturas 8080       # demo cargada
```

y en el emulador (`Medium_Phone_API_35`, **con ventana**, si no `screencap` sale
en blanco), con `. .\scripts\dev\ui.ps1`: entrar como empleado y `Captura` en
Mi jornada (trabajando), Historial, Ausencias (con el saldo) y el widget en la
pantalla de inicio; como gestor, el panel de gestión y «Ahora mismo». Capturar
desde bash (`adb exec-out screencap -p > f.png`): la redirección de PowerShell
5.1 corrompe el PNG. Las mismas sirven para el README.

## 5. Contenido de la app (cuestionarios de Play Console)

### Seguridad de los datos

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte datos de los tipos obligatorios? | Sí |
| ¿Se cifran en tránsito? | Sí (HTTPS en todo; el tráfico en claro está prohibido en producción) |
| ¿Se puede pedir que se borren? | Sí: `{web}/legal/borrar-cuenta`, y desde Ajustes en la app |
| ¿Se comparten con terceros? | **No.** Sentry y Firebase tratan los datos por encargo, y Play no cuenta eso como compartir |

Tipos de datos (todos **recogidos**, ninguno **compartido**, ninguno para
publicidad):

| Tipo en Play | Qué es en la app | ¿Obligatorio? | Finalidad |
|---|---|---|---|
| Nombre | Nombre y apellidos | Sí | Funcionalidad de la app; gestión de la cuenta |
| Dirección de correo | Usuario de la cuenta | Sí | Funcionalidad; gestión de la cuenta; comunicaciones |
| ID de usuario | Id interno y código de empleado | Sí | Funcionalidad |
| Otra información personal | Fecha de nacimiento, puesto, departamento | Opcional | Funcionalidad |
| **Ubicación precisa** | Solo al fichar la entrada y la salida, si la empresa lo pide. Nunca en segundo plano | Opcional (se ficha sin ella) | Funcionalidad |
| Información de salud | El tipo de ausencia puede ser «Consulta médica» u «Hospitalización de un familiar», y el justificante. No hay diagnósticos, pero mejor declararlo | Opcional | Funcionalidad |
| Fotos | Foto de perfil | Opcional | Funcionalidad |
| Archivos y documentos | El currículum (PDF) y el justificante de una ausencia | Opcional | Funcionalidad |
| Otro contenido generado por el usuario | Fichajes, solicitudes de ausencia y de corrección, comunicaciones del canal de denuncias | Sí | Funcionalidad |
| Registros de fallos y diagnósticos | Sentry, sin datos personales (`send-default-pii` apagado) | Sí | Análisis; funcionalidad |
| ID del dispositivo u otros | El token de notificaciones de Firebase | Opcional (sin él no hay avisos) | Funcionalidad |

**No** recoge: contactos, calendario, SMS, historial de navegación, información
financiera, audio, ni la ubicación aproximada aparte de la precisa (el permiso
`ACCESS_COARSE_LOCATION` va con el de precisa porque Android lo exige).

### Permisos y declaraciones

- **Ubicación**: `ACCESS_FINE_LOCATION`, solo en primer plano: no hace falta el
  formulario de ubicación en segundo plano. La app explica para qué es **antes**
  de pedir el permiso (*prominent disclosure*), y sin el permiso se ficha igual.
- **Notificaciones**: `POST_NOTIFICATIONS` (Android 13 o más).
- **Alarmas exactas, servicios en primer plano, fotos y vídeos
  (`READ_MEDIA_*`), instalar paquetes, SMS, llamadas, accesibilidad**: ninguno.
  El recordatorio de fichar usa WorkManager; la foto y los documentos, el
  selector del sistema.

### Resto

- **Clasificación de contenido** (IARC): utilidad o productividad, sin
  violencia, sexo, lenguaje ni compras. Saldrá «PEGI 3» o equivalente.
- **Público objetivo**: 18 años o más. No es para menores.
- **Anuncios**: no tiene.
- **Apps de salud**: no es una app de salud (si Play Console pide la
  declaración, se responde que no tiene funciones de salud).
- **App gubernamental, financiera o de noticias**: no.
- **Acceso a la app**: «Algunas funciones están restringidas», con la cuenta del
  punto 1 y una frase: «Inicia sesión con estas credenciales. Para ver la parte
  de gestión, la cuenta es de un administrador».

## 6. Cuenta de desarrollador y orden de publicación

- **Cuenta personal o de organización.** Una cuenta **personal** creada después
  del 13/11/2023 no puede publicar en producción sin una **prueba cerrada con al
  menos 12 personas apuntadas durante los últimos 14 días seguidos**, y después
  Google lo aprueba con un cuestionario. Una cuenta de **organización** (con
  número D-U-N-S) está exenta. El pago es único (25 $). Si va a haber empresa,
  merece la pena abrir la cuenta a su nombre desde el principio.
- **Verificación de desarrollador de Android.** Google la ha anunciado para
  todas las apps que se instalen en móviles certificados, **también las que no
  vienen de Play** (el APK de GitHub): primero en algunos países y, según lo
  anunciado, en todo el mundo a partir de 2027. Con la cuenta de Play se hace
  allí; hay que registrar el paquete y la clave de firma. Comprobar el calendario
  antes de dejar de publicar en GitHub.

Orden:

1. **Prueba interna** (hasta 100 personas, sin revisión larga): Miguel y dos o
   tres personas del piloto.
2. **Prueba cerrada**: el piloto de IIDD (si son 12 o más durante 14 días, sirve
   también para el requisito de la cuenta personal).
3. **Producción**.

Mientras tanto, el APK de GitHub sigue valiendo. Ojo: el APK y la versión de
Play tienen que ir firmados con la misma clave (punto 2). Cuando la app esté en
Play, `APP_DOWNLOAD_URL` pasa a apuntar a su ficha, y con eso el correo de alta,
la web y la pantalla de «Actualiza la app» llevan allí.
