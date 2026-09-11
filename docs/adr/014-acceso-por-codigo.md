# 14. Nadie teclea la contraseña de otro: acceso por código

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Hasta aquí había dos problemas que eran el mismo:

1. **No había recuperación de contraseña.** Los únicos endpoints de `/auth` eran
   `register-manager`, `login`, `refresh` y `logout`, y el único cambio de
   contraseña exigía estar ya dentro. La primera persona que la olvidara se
   quedaba fuera para siempre; la única salida era tocar la base a mano.
2. **Quien daba el alta tecleaba la contraseña del empleado**
   (`CreateEmployeeRequest.contrasena`), y el correo de bienvenida decía "la
   contraseña te la facilitará la persona que ha creado tu cuenta". El jefe
   conocía la contraseña de todos, y nada obligaba a cambiarla.

La cuenta no era de quien la usa. Mientras la app la usaba una sola persona daba
igual; en cuanto entra alguien más, deja de dar igual.

## Decisión

**Un código de un solo uso que llega por correo, y con el que la propia persona
elige su contraseña.** Sirve igual para entrar por primera vez que para
recuperarla.

### Un código de 6 dígitos, no un enlace

Un enlace que abra la app exige verificar el dominio para Android App Links, y
varios clientes de correo —Gmail entre ellos— rompen o esconden los enlaces que no
son `https`. Seis dígitos se leen en el móvil y se teclean en cualquier sitio.

Seis dígitos son un millón de combinaciones, y eso por sí solo no protege nada.
Lo que protege es que **no se pueden probar**: cada código admite **5 intentos**
(al quinto se anula), caduca pronto, y a una cuenta no se le mandan más de **3
códigos por hora**. Como mucho, 15 intentos a la hora contra una cuenta, con una
probabilidad de acertar de 1 entre 200.000 por código. Encima está el límite de
10 peticiones por minuto por IP que ya tenía el login.

### Del código se guarda el hash, pero con BCrypt y no con SHA-256

El código de seguimiento de las denuncias (ADR 012) usa SHA-256 sin sal, y está
bien razonado para aquel caso: se busca *por* el código, así que el hash tiene que
ser determinista, y es un UUID de 122 bits imposible de adivinar.

Aquí las dos premisas son al revés. Este código **se busca por el usuario**, no
por sí mismo, así que BCrypt —con sal distinta en cada fila— no estorba. Y son
**seis dígitos**: pasados por SHA-256, un millón de hashes se calculan en menos de
un segundo, y una copia filtrada de la tabla daría todos los códigos vigentes.
Con BCrypt, cada uno cuesta lo que cuesta una contraseña.

### No se dice nunca si un correo tiene cuenta

- `POST /auth/recuperar` responde **202 siempre**: exista la cuenta o no, esté
  dada de baja, se haya pasado del límite de códigos o haya fallado el correo.
- `POST /auth/recuperar/confirmar` da **el mismo 400 con el mismo mensaje** para
  un correo sin cuenta y para un código incorrecto, caducado, usado o agotado.
- Y **tarda lo mismo**: cuando no hay cuenta, se hace igualmente una
  comprobación BCrypt contra un hash de relleno. Sin eso, "no existe" respondería
  en un milisegundo y "código incorrecto" en decenas, y el tiempo de respuesta
  diría lo que el mensaje calla.

### El alta ya no lleva contraseña

`CreateEmployeeRequest` y `CreateManagerRequest` pierden el campo. La cuenta nace
con el hash de 122 bits de azar que no se guardan en ningún sitio —la columna es
`NOT NULL` y no puede ir vacía—, así que no hay contraseña con la que entrar hasta
que su dueño use el código.

Esto hace innecesario el `debe_cambiar_contrasena` que proponía el plan: no existe
nunca una contraseña provisional que haya que obligar a cambiar. Tampoco hace falta
un "reenviar código de alta": si caduca, el empleado pide uno con "He olvidado mi
contraseña", porque tener acceso a su correo es justo lo que demuestra quién es.

### Los dos correos se envían distinto, y es a propósito

Todo el correo del proyecto se envía **después del commit, en segundo plano, y
tragándose los fallos** (Fase 10): un aviso de "te han aprobado las vacaciones" no
puede hacer fallar la aprobación. Para el código **eso no vale**, y además cada
caso lo necesita distinto:

- **Alta**: el correo sale **dentro de la transacción del alta**, y si falla,
  lanza un 503 y el alta se deshace entera. Una cuenta creada cuyo dueño no ha
  recibido el código es una cuenta en la que nadie puede entrar, y quien da el
  alta tiene que enterarse en ese momento.
- **Recuperación**: si el correo falla **no se lanza** —un error solo para los
  correos que sí tienen cuenta diría cuáles la tienen— ni se guarda el código,
  así que el anterior sigue valiendo. Queda como `log.error`, que con Sentry es un
  aviso a quien mantiene el servicio.

Por eso `EmailSender` tiene ahora dos métodos: `enviar`, el de siempre, y
`enviarObligatorio`, que propaga el fallo.

### Un fallo tiene que contar aunque la petición falle

`confirmar` va con `@Transactional(noRollbackFor = BusinessException.class)`. Un
código incorrecto **suma un intento y lanza**; con un `@Transactional` normal, la
excepción desharía también la suma y los intentos serían infinitos. Es el tipo de
error que un test con mocks no ve: lo comprueba `AccessCodeIT` contra PostgreSQL.

### Fijar la contraseña cierra todas las sesiones

Quien tuviera la sesión abierta con la contraseña anterior —que puede ser justo
quien la robó— deja de tenerla: se revocan todos los refresh tokens de la cuenta.

## Consecuencias

- **Sin servidor de correo no se pueden dar altas.** Antes, con el SMTP caído, el
  alta funcionaba y el correo de bienvenida se perdía en silencio; ahora falla con
  un 503 y un mensaje claro. Es lo correcto, pero convierte el correo en
  **infraestructura obligatoria** de producción, no en una cortesía.
- **El correo de bienvenida de la Fase 10 desaparece**: lo sustituye el del código
  de alta. El aviso dentro de la app se mantiene.
- Los mensajes de log de este flujo llevan el correo de la persona, y con Sentry
  salen del servidor. Aceptable mientras los datos sean propios; hay que revisarlo
  antes de tratar datos de terceros.
- Un código anulado o usado no se borra: cuenta para el límite por hora, y la
  tabla crece con cada petición. A este volumen no importa; si importara, se
  purgan los de más de un día.
