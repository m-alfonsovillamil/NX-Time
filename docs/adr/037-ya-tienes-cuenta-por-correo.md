# 37. Registrarse con un correo que ya tiene cuenta: se le dice a su dueño, por correo

**Estado:** aceptada · **Fecha:** octubre 2026 · Cambia un punto del
[ADR 034](034-endurecimiento-de-octubre.md)

## Contexto

Desde el ADR 034, registrar una empresa con un correo que ya tiene cuenta
respondía lo mismo que con uno nuevo («te hemos mandado un código») y **no
mandaba nada**. Era la forma de no decir qué correos tienen cuenta: el registro
es público, y un «ese correo ya está registrado» en la respuesta permite
comprobar direcciones una a una.

El 7/10/2026 Miguel se registró con su Gmail y con su Outlook, que ya tenían
cuenta, y se quedó esperando un código que no iba a llegar. Lo que concluyó fue
que el correo de producción fallaba. No fallaba: la cadena Render → Brevo →
Gmail funcionaba, y las secuencias de `usuarios` y `empresas` demostraban que
ningún registro había llegado a insertar nada.

Quien se registra con un correo que ya tiene cuenta es, casi siempre, su dueño,
que no se acordaba. El silencio protegía de un atacante hipotético a costa de
dejar sin respuesta al caso real.

## Decisión

### La respuesta no cambia; el dueño recibe un correo

- `POST /auth/register-manager` con un correo que ya tiene cuenta sigue
  respondiendo **202 con el mismo cuerpo** que un correo nuevo, y sigue sin
  crear ni cambiar nada.
- A esa dirección se le manda un correo de **«Ya tienes una cuenta en NX
  Time»** (plantilla `account-already-exists`): que alguien ha intentado
  registrar una empresa con su correo, que no se ha creado nada, en qué
  empresa está su cuenta y cómo entrar o recuperar la contraseña.
- **No lleva código ni enlace que hacer valer.** No hay nada en ese correo que
  sirva a quien lo intercepte, y no anula un código de recuperación que
  estuviera en vuelo.
- A una cuenta **dada de baja** no se le dice que entre, porque no puede: se le
  dice que hable con quien administra su empresa.
- Sale **después del commit y sin esperar** (el mismo evento que el código de
  confirmación), para que el registro no retenga una conexión esperando al
  servidor de correo y para que los dos caminos tarden parecido.

Quién se entera de que la cuenta existe es quien lee ese buzón, que ya lo
sabía o tiene derecho a saberlo. Quien hace la petición sigue sin saberlo.

### Uno al día por cuenta, y apuntado en la base

- **V40**: `usuarios.aviso_cuenta_existente_en`. Como mucho un aviso cada 24
  horas por cuenta. El correo no caduca ni lleva nada que usar, así que
  repetirlo antes no ayuda a su dueño y sí a quien quiera llenarle el buzón.
- Es una **columna y no un contador en memoria**: el servicio se reinicia con
  cada despliegue, y un límite que se olvida al reiniciar no limita.
- Comprobar y apuntar son **una sola sentencia** (`UPDATE … WHERE` el último
  aviso es anterior al plazo). Leyendo y guardando la entidad, dos registros a
  la vez mandarían dos correos o, peor, el segundo chocaría con `@Version` y
  respondería con un error: una respuesta distinta de la de un correo sin
  cuenta, que es justo lo que no se puede dar. Hay un test de integración que
  lanza seis a la vez.
- La columna **no está en la entidad** `User`: solo la toca esa sentencia, y
  así ningún `save` de otra parte la pisa con un valor viejo.

### La pantalla ya no promete un código

Tras registrar, la web y la app decían «Te hemos mandado un código de 6
cifras». Ahora dicen que se ha mandado **un correo**, que lo normal es que
traiga un código, y que si dice que ya hay cuenta se vuelva al inicio. El texto
es el mismo en los dos casos, porque la pantalla no sabe cuál ha sido.

## Consecuencias

- Quien ya tenía cuenta y se registra de nuevo recibe una respuesta en su
  buzón en vez de silencio.
- El registro público puede hacer que NX Time mande un correo a cualquiera que
  **ya tenga cuenta**: uno al día. Es menos que lo que ya permite
  `/auth/recuperar` (tres códigos por hora y cuenta), y a quien no tiene cuenta
  no le llega nada por esta vía.
- La pantalla de confirmación es un poco más larga de leer.
- Lo demás del ADR 034 sigue igual: el mismo BCrypt en los dos caminos, y el
  nombre de empresa repetido sí se dice.

## Alternativas descartadas

- **Decirlo en la pantalla** («ese correo ya tiene cuenta»). Es lo más cómodo y
  es la enumeración que cerró el ADR 034. Decisión de Miguel: por correo, no en
  pantalla.
- **Mandarle un código de recuperación** dentro del aviso. Dejaría a cualquiera
  anular, desde el registro, el código que el dueño acabara de pedir, y pondría
  algo canjeable en un correo que nadie ha pedido.
- **Un contador en memoria**, como el de intentos de login. Se pierde en cada
  despliegue.
- **Seguir callando y explicarlo mejor en la pantalla.** Quien espera un correo
  mira el buzón, no relee la pantalla.
