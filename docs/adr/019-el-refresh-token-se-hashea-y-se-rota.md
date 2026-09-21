# 19. El refresh token se hashea, se rota, y reutilizarlo revoca la familia

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

Desde la Fase 4, entrar en NX Time devuelve dos cosas: un access token (JWT
HS256, 15 minutos) y un refresh token con el que renovarlo sin volver a pedir
la contraseña. El refresh **no** es un JWT sino una cadena opaca guardada en
base de datos, precisamente para poder revocarlo sin esperar a que caduque.

Tenía tres propiedades que, juntas, lo convertían en el punto más débil del
sistema:

1. **Se guardaba en claro.** Un volcado de la base —una copia de seguridad, un
   acceso de lectura mal dado— entregaba sesiones vivas listas para usar.
2. **No rotaba.** El mismo valor servía treinta días, así que robarlo una vez
   daba acceso durante un mes.
3. **Nada detectaba el robo.** El legítimo y el ladrón podían renovar en
   paralelo indefinidamente, sin que ninguno de los dos notara nada.

Con la app Android eso era discutible pero acotado: el token vive en
`SharedPreferences` de una aplicación concreta, con `allowBackup=false`.

Lo que cambia el cálculo es la web (bloque C del plan de septiembre). Ahí el
token pasa a vivir en un navegador, donde **cualquier XSS lo alcanza**. Poner
un refresh de treinta días, sin rotar y sin detección, en `localStorage`, es
regalar un mes de acceso a la primera inyección de script.

## Decisión

### Se guarda el hash, no el token

Solo `sha256(token)`. La comparación sigue siendo exacta y la base deja de
contener nada reutilizable.

**SHA-256 y no BCrypt**, al revés que con las contraseñas: esto no es un
secreto que alguien elige —es un UUID aleatorio de 122 bits—, así que no hay
diccionario contra el que defenderse. Lo que se busca es que un volcado no
sirva de nada, y para eso un hash rápido basta. BCrypt costaría decenas de
milisegundos **en cada renovación** sin comprar nada.

### Rota en cada renovación

`POST /auth/refresh` devuelve un refresh nuevo y marca el anterior como rotado,
enlazándolo con su sucesor. Un token robado sirve, como mucho, hasta que su
dueño renueve.

Esto es **un cambio incompatible**: antes la respuesta traía el mismo token que
se había mandado y el cliente podía ignorarlo. Ahora hay que guardarlo.

### Reutilizar un token rotado revoca la familia entera

Es la parte que de verdad protege. Si llega un token que ya se había usado para
renovar, hay dos clientes usando la misma cadena y **solo uno puede ser el
legítimo**. No hay forma de saber cuál —el ladrón copió un valor idéntico—, así
que se cierran los dos y ambos vuelven al login.

Para eso existe la columna `familia`: todos los tokens que descienden de un
mismo login la comparten, y se conserva al rotar. Revocar la familia cierra esa
sesión **sin tocar las demás**: si el móvil y el navegador entraron por
separado, cada uno tiene la suya.

Es ruidoso a propósito. Un robo silencioso dura un mes; esto se nota el mismo
día, y lo que se paga es un login de más en el caso raro de que dos peticiones
de refresco se crucen de verdad.

> ⚠️ **La revocación va con `noRollbackFor`**, igual que el contador de intentos
> de los códigos de acceso y por la misma razón: se revoca **y** se lanza, y sin
> eso la excepción deshace el UPDATE. La familia queda cerrada mientras dura la
> transacción y vuelve a estar viva al salir — es decir, la protección no
> protege nada.
>
> No es teoría. La primera versión salió a producción sin ello y se detectó
> verificando el despliegue: rotar, reutilizar el token viejo y volver a usar el
> nuevo devolvía 200 donde tenía que devolver 401. Los tests unitarios no lo
> veían porque con mocks no hay transacción que deshacer; lo cubre ahora
> `RefreshTokenRotadoIT`.

### La vida depende del origen

| Origen | Duración |
|---|---|
| `ANDROID`, `IOS` | 30 días |
| `WEB` | 12 horas |

Un navegador es una máquina que carga código de terceros y que a menudo se
comparte; un móvil con la app instalada no. Obligar a entrar cada doce horas en
el móvil sería castigar al usuario sin ganar nada.

El cliente lo declara al hacer login, y **no se adivina del `User-Agent`**: un
cliente que mienta solo se perjudica a sí mismo, porque declarar otro origen no
da ningún permiso, solo otra caducidad. Por defecto `ANDROID`, así que la app
publicada no cambia de comportamiento.

## Consecuencias

- **Al aplicar V30 se cierran todas las sesiones vivas.** No se puede hashear
  hacia atrás lo que ya no tenemos. El coste es que cada persona vuelva a entrar
  una vez, y la app ya trata el 401 del refresco llevando al login.
- **El cliente tiene que guardar el refresh nuevo.** En Android se guarda junto
  al access en una sola escritura: si solo se guardara el access, la siguiente
  renovación llegaría con un token rotado y el servidor cerraría la sesión.
- **A12 tenía que ir antes.** Sin serializar el refresco en el cliente, varias
  peticiones con 401 simultáneo disparaban varios refrescos, y con rotación el
  segundo llegaría con un token ya usado — es decir, la protección nueva echaría
  a la gente de la app. Por eso el orden de merge no era negociable.
- **Aparece `POST /api/v1/perfil/verificar-contrasena`.** La app comprobaba la
  contraseña para la huella haciendo un `/auth/login` completo, que emitía
  tokens nuevos y dejaba vivo el refresh anterior. Con rotación eso pasa de
  molesto a incorrecto.
- Queda pendiente decidir **dónde guarda el token la web** (en memoria, o cookie
  `HttpOnly` con dominio propio). Esta decisión no lo resuelve; lo que hace es
  que cualquiera de las dos opciones sea defendible.
