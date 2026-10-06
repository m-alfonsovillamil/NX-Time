# 34. Endurecimiento de octubre: confirmar el correo al registrarse y topes donde no los había

**Estado:** aceptada · **Fecha:** octubre 2026 · Sale de la revisión de
seguridad del 1/10/2026

## Contexto

La revisión del 1/10/2026 buscó lo que se podía hacer **desde fuera, sin
romper nada**, solo usando la API como está pensada pero a escala. Salieron
problemas de un mismo tipo: puertas abiertas a propósito (el registro de
empresas es público, las altas mandan correo, el kiosco acepta un PIN de 4
cifras) a las que les faltaba un tope.

Los más graves:

- **Relé de correo abierto.** Cualquiera registraba una empresa con el correo
  que quisiera, entraba al momento como su ADMIN y daba de alta a gente sin
  límite. Cada alta manda un correo a la dirección tecleada, con NX Time en el
  remitente: un cañón de spam con nuestra reputación de envío.
- **Enumeración de cuentas.** El registro respondía «El email ya está
  registrado», y `/auth/recuperar`, que ya daba la misma respuesta exista o no
  la cuenta, tardaba decenas de milisegundos más cuando existía (un BCrypt).
- **Bomba de memoria en la foto** y el resto de puntos de la revisión, que
  están en los PR #137 y siguientes.

## Decisión

### Quien registra una empresa confirma su correo antes de entrar

Decisión de Miguel, frente a dejarle entrar y no dejarle invitar hasta
confirmar: es más estricto y más fácil de explicar.

- **V37**: `usuarios.correo_sin_confirmar_desde`. Se marca **lo pendiente**,
  no lo confirmado: todas las cuentas que ya existían quedan a NULL, y las que
  da de alta un ADMIN también, porque esas ya demuestran su correo con el
  código de alta (ADR 014).
- `POST /auth/register-manager` **ya no abre sesión**: responde 202 y manda un
  código de 6 cifras (tipo `CONFIRMACION`, una hora) después del commit, como
  la recuperación. El correo no va dentro de la transacción porque el registro
  es público (ver ADR 014 y la Fase A9).
- `POST /auth/registro/confirmar` canjea el código y abre la sesión como el
  login, con las mismas reglas de `origen` (cookie en la web, ADR 030).
- El **login** de una cuenta pendiente, con la contraseña buena, responde
  **403** y manda otro código. El 403 solo lo ve quien sabe la contraseña, y
  el login no da 403 por nada más, así que los clientes lo usan para pasar a
  la pantalla del código.
- Registrarse otra vez con un correo **a medio confirmar** rehace el registro
  con los datos nuevos y manda otro código. Quien no tiene el buzón sigue sin
  poder entrar, así que no importa quién lo rehaga.
- El código es un código de acceso más: mismo hash BCrypt, 5 intentos y como
  mucho 3 por hora y cuenta, que es también lo que impide usar el registro
  repetido para llenarle el buzón a alguien.
- Recuperar la contraseña también confirma el correo: el código ha llegado a
  ese buzón igual.

### El mismo trato exista o no la cuenta

- El registro con un correo que **ya tiene cuenta** responde lo mismo (202,
  «te hemos mandado un código») y no crea ni manda nada. Gasta el mismo
  BCrypt que un registro nuevo, para que tampoco lo diga el tiempo.
- `/auth/recuperar` sin cuenta gasta el BCrypt de un código que no emite.
- Lo que **sí** se sigue diciendo: que ya existe una empresa con ese nombre.
  Los nombres de empresa no son datos personales, y no hay forma razonable de
  registrar una empresa sin saber si el nombre está libre.
- Lo que también se sigue diciendo, a sabiendas: «El email ya está registrado»
  al dar de alta a alguien. Quien lo ve es un ADMIN con el correo confirmado y
  con el tope de abajo, y necesita saberlo para no dar de alta dos veces.

### Topes donde no había

| Qué | Tope | Por qué ese número |
|---|---|---|
| Altas (códigos de alta) por empresa | 50 al día | Una plantilla entera en un día; una más grande, en dos |
| Confirmar códigos de kiosco | 10 por ADMIN y hora | Quien empareja una tablet acierta a la primera o a la segunda |
| Dispositivos push por persona | 10, se quedan los usados más recientemente | Más navegadores de los que nadie usa; un token viejo no recibe |
| Foto de perfil | 40 megapíxeles, leídos de la cabecera | Más que cualquier móvil; el fichero ya tenía 5 MB, la imagen no |
| Heap de la JVM | 75 % de la memoria del contenedor | Por defecto era el 25 %: 128 MB en Render |
| PIN del kiosco | 5 fallos bloquean 15 min; al 3.er bloqueo se anula (V38) | Contra un PIN se prueban 15 como mucho, no ~480 al día |
| Login, por cuenta | Desde el 5.º fallo seguido, la espera se dobla hasta 15 min | ~100 intentos al día como mucho, sin bloqueo duro |

### El PIN del kiosco y las tarjetas

- Los bloqueos del PIN se cuentan (`usuarios.kiosco_pin_bloqueos`) y **no
  vuelven a cero al acertar**: si lo hicieran, quien prueba tendría 14
  intentos cada vez que el dueño ficha bien. Vuelven a cero al elegir otro
  PIN. Al anularlo se avisa a su dueño (al perfil) y a quien tiene
  `empresa:configurar` (a los ajustes de la empresa). El PIN sigue siendo de
  4 a 6 cifras: con el tope, 4 bastan, y exigir 6 invalidaba los que ya hay.
- Las tarjetas de toda la plantilla (con una se ficha por su dueño) pasan a
  `POST /api/v1/empresa/kioscos/tarjetas`, solo con `empresa:configurar`, y
  queda en el log quién las saca. Antes era un GET que escribía en la base y
  lo podía hacer cualquiera con `empleado:gestionar`.

### Las sesiones

- El login espera más tras cada fallo seguido contra una cuenta, en vez de un
  bloqueo duro de un día que cualquiera podría usar para dejar a otro sin
  entrar. Va en memoria, como el límite que ya había.
- **Cambiar la contraseña cierra las demás sesiones**, como ya hacía elegirla
  con un código. Para no cerrar la propia, el access token lleva su sesión
  (claim `sid`, la familia del refresh, ADR 019) y se revocan todas menos esa.

### Lo que sale de la aplicación

- Los logs escriben el **id** de quien hace algo, no su correo, y
  `LimpiezaDeSentry` tapa cualquier correo que se cuele (una excepción de la
  base, del servidor de correo) en las migas, el mensaje o las excepciones.
- El código de seguimiento de una denuncia va en el **cuerpo**, no en la URL:
  es la credencial de una denuncia anónima, y una URL acaba en los logs de
  acceso, el historial del navegador y Sentry. Las rutas viejas, con el
  código en la URL, se quitaron el 4/10/2026, cuando ya nadie usaba apps
  anteriores a la 1.10.

### Rendimiento y la cuota de Neon

- `TimeEntry.registroOriginal` y `Kiosk.creadoPor` pasan a `LAZY`: el
  historial del equipo baja de 9 a 5 consultas por página
  (`ConsultasDelHistorialIT` lo fija). `TimeEntry.kiosco` sigue `EAGER`
  porque los controladores convierten el fichaje en respuesta fuera de la
  transacción (`open-in-view` apagado); lo caro era lo que el kiosco
  arrastraba.
- **No** se cachea el usuario en `JwtAuthenticationFilter`, aunque estaba en
  el plan. Hay servicios que guardan la entidad del principal (cambiar la
  contraseña, editar el perfil); con un `User` cacheado y desfasado, la
  siguiente escritura daría un error de `@Version`. Una consulta por petición
  no compensa ese riesgo.
- La campana de la web pregunta cada cinco minutos y no cada minuto: con una
  pestaña a la vista, Neon no llegaba a suspender nunca la base. Un push y
  volver a la pestaña la refrescan al momento.
- Dependencias al día dentro de sus versiones mayores (Spring Boot 3.5.16,
  Retrofit 2.12, bucket4j 8.21 con su artefacto nuevo, etc.).

### Lo que se apaga o se escapa

- Swagger UI y `/v3/api-docs`, apagados en producción salvo con
  `SWAGGER_PUBLICO=true`. No esconden nada que no esté en `docs/openapi.json`,
  pero un mapa navegable de la API con «Try it out» ayuda más a quien ataca.
- `Content-Disposition` de los adjuntos con el nombre escapado (RFC 6266).
- HSTS en la web, un año con subdominios y sin `preload`.

## Consecuencias

- **Las apps Android anteriores a la 1.10 no pueden registrar empresas**:
  reciben un 202 sin sesión. Entrar sí pueden, salvo quien registró la empresa
  y no ha confirmado su correo. Se acepta porque registrar una empresa desde
  el móvil es raro y el piloto es pequeño; la 1.10 (versionCode 11) lo trae.
- Un registro que nadie confirma deja una empresa vacía con su nombre
  ocupado. No se limpia con una tarea programada (la cuota de Neon, ver el
  plan de la web); si llega a molestar, se borra al registrar otra con ese
  nombre si la pendiente tiene más de unos días.

  **Desde el 6/10/2026 sí se limpia**, a los dos días, y sin tarea nueva: lo
  hace la de las 3:45 (`DataDeletionScheduler`), que ya corría cada noche, así
  que la base no se despierta a ninguna hora más. Solo se borra la empresa que
  no tiene a nadie más, y las claves `RESTRICT` impiden llevarse por delante
  una cuenta que tuviera cualquier otra cosa
  (`UnconfirmedRegistrationCleaner`).
- Los tests de integración que registran una empresa por la API canjean el
  código que recogen del evento (`CodigosEnviados`), como haría una persona.

## Alternativas descartadas

- **Dejar entrar y no dejar invitar hasta confirmar.** Menos fricción, pero
  más estados que explicar («¿por qué no puedo dar de alta?») y más sitios
  que comprobar. Miguel prefirió la estricta.
- **Un CAPTCHA en el registro.** Frena bots, no a una persona con un correo
  ajeno, que era el problema; y obliga a cargar un tercero que la CSP tendría
  que admitir.
