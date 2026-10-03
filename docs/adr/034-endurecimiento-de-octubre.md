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
- Los tests de integración que registran una empresa por la API canjean el
  código que recogen del evento (`CodigosEnviados`), como haría una persona.

## Alternativas descartadas

- **Dejar entrar y no dejar invitar hasta confirmar.** Menos fricción, pero
  más estados que explicar («¿por qué no puedo dar de alta?») y más sitios
  que comprobar. Miguel prefirió la estricta.
- **Un CAPTCHA en el registro.** Frena bots, no a una persona con un correo
  ajeno, que era el problema; y obliga a cargar un tercero que la CSP tendría
  que admitir.
