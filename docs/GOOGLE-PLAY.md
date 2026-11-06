# Publicar en Google Play: el borrador

Lo que hay que rellenar en Play Console para publicar la app, sacado de lo que
la app hace de verdad (manifiesto, dependencias y pantallas, a 6/11/2026).
**Antes de nada, el cambio de nombre**: el `applicationId` (`com.nxtime.app`) no
se puede cambiar una vez publicada.

## Lo que falta antes de enviarla

1. **El nombre y el `applicationId` definitivos** (ver el plan del cambio de nombre).
2. **Una página para pedir el borrado de la cuenta.** Play la exige a toda app
   que deja crear cuentas, y esta deja («Registrar una empresa»). Tiene que ser
   pública, sin entrar, y decir cómo se pide y qué se borra. **No existe todavía**:
   es un PR pequeño (una página en `/legal`).
3. **Una cuenta para la revisión de Google**: Play pide usuario y contraseña de
   una cuenta que entre sin segundo factor. Vale la de demostración, en una
   empresa de demostración, nunca una real.
4. **El AAB firmado** (`bundleProdRelease`). En *Play App Signing*, **subir la
   clave con la que se firma hoy el APK** como clave de firma de la app, y no
   dejar que Google genere una nueva. Si no, la app de Play y el APK de GitHub
   llevarían firmas distintas (ver el final). Guardar la clave de subida junto a
   la de firma.
5. Cuenta de desarrollador (25 €, una vez). Como empresa, Google pide el número
   D-U-N-S; como persona física, verificar la identidad.

## Ficha de la tienda

- **Categoría**: Empresa. **Etiquetas**: control horario, registro de jornada.
- **Descripción corta** (80 caracteres como mucho):
  `Registro de jornada para tu empresa: fichar, pausas, ausencias y vacaciones.`
- **Descripción larga**, punto de partida:

  > El registro de jornada que exige la ley, para la plantilla y para quien la
  > gestiona.
  >
  > - Ficha la entrada, las pausas y la salida con un toque.
  > - Sin cobertura también: el fichaje se guarda en el móvil y se envía al volver la red.
  > - Pide vacaciones y ausencias y consulta los días que te quedan.
  > - Firma tu registro de cada mes.
  > - Si eres responsable: aprueba ausencias y correcciones, revisa las horas
  >   extra y el historial de tu equipo.
  >
  > Necesitas una cuenta de tu empresa. Si tu empresa todavía no usa [nombre],
  > regístrala desde la app o en [web].

- **Capturas**: las de `docs/capturas` sirven de base; Play pide al menos dos de
  teléfono, de 1080 px de lado o más.
- **Política de privacidad**: `https://[web]/legal/privacidad`.

## Seguridad de los datos

Respuestas generales:

| Pregunta | Respuesta |
|---|---|
| ¿Recoge o comparte datos de los tipos obligatorios? | Sí |
| ¿Se cifran en tránsito? | Sí (HTTPS en todo; el tráfico en claro está prohibido en producción) |
| ¿Se puede pedir que se borren? | Sí: la página del punto 2 de arriba |
| ¿Se comparten con terceros? | **No.** Sentry y Firebase son proveedores que tratan por encargo, y Play no cuenta eso como compartir |

Tipos de datos (todos **recogidos**, ninguno **compartido**, ninguno para
publicidad):

| Tipo en Play | Qué es en la app | ¿Obligatorio? | Finalidad |
|---|---|---|---|
| Nombre | Nombre y apellidos | Sí | Funcionalidad de la app; gestión de la cuenta |
| Dirección de correo | Usuario de la cuenta | Sí | Funcionalidad; gestión de la cuenta; comunicaciones |
| ID de usuario | Id interno y código de empleado | Sí | Funcionalidad |
| Otra información personal | Fecha de nacimiento, puesto, departamento | Opcional | Funcionalidad |
| **Ubicación precisa** | Solo al fichar la entrada y la salida, si la empresa lo pide. Nunca en segundo plano | Opcional (se ficha sin ella) | Funcionalidad |
| Información de salud | El tipo de ausencia puede ser «Consulta médica» u «Hospitalización de un familiar». No hay diagnósticos, pero mejor declararlo | Opcional | Funcionalidad |
| Fotos | Foto de perfil | Opcional | Funcionalidad |
| Archivos y documentos | El currículum (PDF) | Opcional | Funcionalidad |
| Otro contenido generado por el usuario | Fichajes, solicitudes de ausencia y de corrección, comunicaciones del canal de denuncias | Sí | Funcionalidad |
| Registros de fallos y diagnósticos | Sentry, sin datos personales por defecto (`send-default-pii` apagado) | Sí | Análisis; funcionalidad |
| ID del dispositivo u otros | El token de notificaciones de Firebase | Opcional (sin él no hay avisos) | Funcionalidad |

**No** recoge: contactos, calendario, SMS, historial de navegación, información
financiera, audio, ni la ubicación aproximada aparte de la precisa (el permiso
`ACCESS_COARSE_LOCATION` va con el de precisa porque Android lo exige).

## Permisos y declaraciones

- **Ubicación**: `ACCESS_FINE_LOCATION`, solo en primer plano. No hace falta el
  formulario de ubicación en segundo plano. La app ya hace lo que Play exige
  (*prominent disclosure*): explica para qué es **antes** de pedir el permiso, y
  sin el permiso se ficha igual.
- **Notificaciones**: `POST_NOTIFICATIONS` (Android 13 o más).
- **Instalar paquetes, SMS, llamadas, accesibilidad**: ninguno.

## Resto del cuestionario

- **Clasificación de contenido**: app de utilidad o productividad, sin
  violencia, sexo, lenguaje ni compras. Saldrá «PEGI 3» o equivalente.
- **Público objetivo**: 18 años o más. No es para menores.
- **Anuncios**: no tiene.
- **App gubernamental, de salud o financiera**: no.
- **Acceso a la app**: «Algunas funciones están restringidas», con la cuenta del
  punto 3 y una frase: «Inicia sesión con estas credenciales. Para ver la parte de
  gestión, la cuenta es de un administrador».

## Orden de publicación

1. **Prueba interna** (hasta 100 personas, sin revisión larga): Miguel y dos o
   tres personas del piloto.
2. **Prueba cerrada**: el piloto de IIDD. Una **cuenta personal** creada después
   del 13/11/2023 no puede publicar en producción sin una prueba cerrada con al
   menos 12 personas apuntadas durante los últimos 14 días seguidos, y después
   Google lo aprueba con un cuestionario. La prueba interna no cuenta. **Una
   cuenta de organización (con D-U-N-S) está exenta**: si va a haber empresa,
   merece la pena abrir la cuenta a su nombre desde el principio.
3. **Producción**.

Mientras tanto, el APK de GitHub sigue valiendo. Ojo: el APK y la versión de
Play deben firmarse con la misma clave. Si no, quien tenga uno no puede
actualizar al otro sin desinstalar, y pierde los fichajes sin enviar.
