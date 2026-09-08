# 12. El anonimato del canal de denuncias es estructural, no una promesa

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

La **Ley 2/2023** obliga a las empresas de 50 o más empleados a tener un canal
interno de información con tres garantías: anonimato opcional, acuse de recibo
en 7 días naturales y respuesta en 3 meses. Un buzón de sugerencias con un
desplegable no cumple ninguna de las tres.

De las tres, el anonimato es la que decide si el canal sirve para algo. Quien
tiene que denunciar a su jefe no lo hace porque una pantalla diga «esto es
anónimo»: lo hace si cree que el sistema **no puede** identificarle aunque
alguien quiera. Y este canal vive dentro de una aplicación en la que el
denunciante está autenticado, con su token, su empresa y su id delante del
servidor en el mismo momento de escribir la denuncia.

Ahí está el problema real de la fase. La identidad no se filtra por un fallo
espectacular: se filtra por las mismas cosas que en las otras siete fases eran
buenas prácticas.

## Decisión

**El sistema no guarda la identidad de un denunciante anónimo en ninguna parte,
y las reglas que la protegen viven en el esquema, no en el criterio de quien
escriba el próximo endpoint.**

Cuatro sitios por los que se escapaba, y qué se hizo en cada uno:

| Fuga | En cualquier otra fase sería | Aquí |
|---|---|---|
| `log.info("{} ha presentado…", actor.getEmail())` | trazabilidad normal | el log dice **qué** entró y de qué categoría, nunca de quién |
| Excluir al autor de los avisados | cortesía (no avisarte de lo tuyo) | **no se excluye a nadie**: un avisado de menos delata al denunciante |
| Guardar `autor_id` en sus mensajes | trazabilidad | **null**, aunque el token esté delante |
| Guardar el código de seguimiento | un identificador más | se guarda **solo su hash** |

La segunda es la menos evidente y la más peligrosa. Con dos personas que
instruyen, si una no recibe el aviso de una denuncia anónima, la otra sabe de
quién es. La lista de destinatarios no puede depender de quién denunció, y la
única forma segura de garantizarlo es que **no lo mire**.

### El código de seguimiento se guarda hasheado

Si la denuncia es anónima no hay forma de listársela a su autor: no hay ninguna
columna que los relacione. Por eso al presentarla se le entrega **una sola vez**
un `codigo_seguimiento`, con el que consulta el estado y contesta. Es como
funcionan los canales reales.

De ese código se guarda **SHA-256 sin sal**, no el código. Es una credencial
—quien la tiene, lee la denuncia y escribe en ella—, así que guardarla en claro
significaría que cualquiera con acceso de lectura a la base (una copia de
seguridad, un volcado de soporte) puede suplantar al denunciante en cualquier
expediente abierto.

Que no lleve sal, ni sea BCrypt, es deliberado y no un descuido:

- **Se busca POR ella.** Llega un código y hay que encontrar su fila, así que el
  hash tiene que ser determinista e indexable. Con una sal por fila habría que
  recorrer la tabla entera comparando una a una.
- **No la elige una persona.** Es un UUIDv4: 122 bits de `SecureRandom`, sin
  diccionario que probar. Lo que la sal y el coste de BCrypt protegen
  —contraseñas humanas, cortas y reutilizadas— aquí no existe. `usuarios.password`
  sigue con BCrypt, que es donde ese razonamiento sí aplica.

La consecuencia buena de esto es que **no hay ningún endpoint que reenvíe un
código**, y no porque se nos haya olvidado: no se puede escribir. Poder
recuperarlo sería poder demostrar que una denuncia anónima es de alguien.

La mala es que un código perdido no se recupera, y la app tiene que decirlo
antes de que el usuario cierre el diálogo. Por eso el aviso viaja **desde el
servidor** (`avisoImportante`) y no en los recursos de la app: si dependiera de
que cada cliente se acuerde de enseñarlo, el primero que lo olvide deja a
alguien sin acceso a su propio expediente para siempre.

### Las lee solo ADMIN, y eso incluye no dárselas a RRHH

`denuncia:instruir` es la única authority del proyecto que **empieza en ADMIN y
no baja de ahí**. No es un reparto de roles perezoso: la Ley 2/2023 obliga a
designar un Responsable del Sistema Interno de Información, y darle esa lectura
también a un GESTOR haría que la denuncia sobre un GESTOR la leyera él.

Que la jerarquía `EMPLEADO < GESTOR < RRHH < ADMIN` solo la conceda arriba es
aquí **el requisito**, no un efecto colateral de dónde se escribió la línea. Si
algún día hace falta un responsable que no administre la empresa, lo correcto es
un rol dedicado (`RESPONSABLE_CANAL`), no repartir esta authority hacia abajo.

Por lo mismo, el contador de denuncias abiertas del panel de empresa llega
**null** a quien no instruye, y no cero. Un cero afirma que no hay ninguna, y
afirmarlo ante quien no tiene derecho a saberlo ya es información —a veces,
además, falsa.

### Los plazos se calculan al leer

`diasHastaAcuse` y `diasHastaRespuesta` no son columnas: se derivan de
`creado_en` cada vez que se lee. Un plazo guardado nace caducado, y el patrón ya
está en el proyecto (el saldo de vacaciones y la bolsa de horas extra se derivan
igual).

Llegan **en negativo** cuando el plazo ya pasó, en vez de cero o de un booleano
`vencido`: «hace once días que había que haber acusado» es justo lo que quien
instruye necesita ver, y un incumplimiento no se arregla dejando de contarlo. Se
cuentan sobre fechas en `Europe/Madrid` y no restando instantes, porque un plazo
en días naturales se mide en el calendario: «quedan 2 días» tiene que decir lo
mismo a las 9:00 que a las 23:00.

### Cerrar exige conclusión, y archivar también

`RESUELTA` y `ARCHIVADA` cierran el expediente y las dos piden conclusión
escrita, con un `CHECK` en la base además de la comprobación del servicio. La
ley obliga a **responder**, no a dar la razón: que la denuncia no se sostenga es
un desenlace legítimo, pero hay que decirlo.

## Consecuencias

**Lo que se gana.** El anonimato es comprobable leyendo el esquema, no confiando
en que nadie añada un `log.info` de más. Los tests de la fase son en su mitad
aserciones de que algo **no** se guarda, que es rara de escribir y es la que
importa: un fallo ahí no rompe ninguna pantalla, solo deja de proteger a alguien
en silencio.

**A un denunciante anónimo no se le puede avisar de nada.** Ni aviso in-app —no
hay `destinatario_id`— ni correo. Tiene que volver con su código. No es una
limitación que arreglar: es la contrapartida exacta del anonimato, y la app lo
dice desde el primer momento.

**Ningún correo lleva contenido de la denuncia.** Ni el relato ni la categoría
de quién. Un correo es texto plano por una red que no controlamos, que acaba
guardado en un buzón personal y en el de un servidor ajeno; el aviso dice que
hay una denuncia y que hay 7 días para acusarla, y para leerla hay que entrar en
la aplicación.

**Nadie instruye una denuncia que presentó él, y esa regla solo puede aplicarse
a las identificadas.** De una anónima el sistema no sabe —ni puede saber— que es
suya. La asimetría no delata nada, porque que una denuncia sea anónima ya viaja
en el expediente.

**Si quien instruye es el único ADMIN de la empresa y la denuncia es suya, se
queda sin nadie que la tramite.** Es la consecuencia asumida: la salida correcta
es designar a otra persona o externalizar el canal, que la propia ley permite,
no dejar que alguien se instruya a sí mismo. La aplicación no puede resolver un
conflicto de interés inventándose un tercero que no existe.

**Un expediente cerrado no admite más mensajes ni se reabre por la API.** La vía
de un denunciante que no acepta la conclusión es externa (la Autoridad
Independiente de Protección del Informante), no un botón aquí.
