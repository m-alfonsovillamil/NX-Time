# 13. La candidatura congela el CV que se presentó

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

La fase B2 dejó una regla sencilla para los adjuntos: **un CV y una foto
vigentes por persona**. Subir otro reemplaza al anterior, el `UNIQUE (usuario_id,
tipo)` lo garantiza, y la fila vieja se borra con sus bytes detrás. Sin nadie que
referenciara esos ficheros, guardar versiones antiguas habría sido solo ocupar
espacio, y el propio `V8__adjuntos.sql` lo dejó anotado:

> OJO para la fase H: una candidatura congela el `adjunto_id` del CV de ese
> momento, así que el reemplazo tendrá que dejar de borrar la fila vieja cuando
> alguien la referencie. Hoy no hay quien lo haga.

Esta fase es ese "alguien". Una candidatura a una vacante interna se valora
leyendo un CV, y entre que se presenta y se decide pasan semanas.

## Decisión

**La candidatura guarda el `adjunto_id` concreto que había al presentarse, no
una referencia a "el CV de esta persona".**

La alternativa —resolver el CV al leer, a partir del usuario— parece más simple y
tiene un fallo que no se ve hasta que ocurre: si el candidato sube una versión
nueva en marzo, el expediente que el gestor leyó en enero **cambia solo**. No es
que quede desactualizado: es que la decisión y el documento sobre el que se tomó
dejan de coincidir, y nadie se entera. Un expediente que se reescribe hacia atrás
no es un expediente.

De ahí sale todo lo demás.

### `adjuntos` gana una columna `vigente`, y su índice único se vuelve parcial

El `UNIQUE (usuario_id, tipo)` mezclaba dos ideas: "existe" y "es el vigente".
Mientras el reemplazo borraba, daban igual. Ahora no, así que se separan:

```sql
ALTER TABLE adjuntos ADD COLUMN vigente BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE adjuntos DROP CONSTRAINT uq_adjuntos_usuario_tipo;
CREATE UNIQUE INDEX uq_adjuntos_usuario_tipo_vigente
    ON adjuntos (usuario_id, tipo) WHERE vigente;
```

Uno vigente por persona y tipo; los anteriores, los que hagan falta. Un adjunto
no vigente **no se lista en el perfil ni se puede volver a elegir**: solo se llega
a él desde la candidatura que lo congeló.

Quien impide de verdad el borrado no es el servicio sino la base:
`fk_candidaturas_cv ... ON DELETE RESTRICT`. El servicio lo comprueba antes para
dar un mensaje que se entienda, pero si esa comprobación desapareciera un día, la
garantía seguiría en pie.

### Borrar el CV propio deja de destruir los bytes, y no da error

Se decidió **no** devolver un 409 cuando alguien borra un CV que una candidatura
congeló. Negarle a una persona borrar su propio CV por algo que hizo el mes
pasado es incomprensible desde la pantalla, y el resultado que ve es el mismo en
los dos casos: desaparece de su perfil y no se descarga desde ahí.

Lo que no se puede es fingir que los bytes se han ido, y eso sí lo dice la
documentación del endpoint. La contrapartida asumida: un CV referenciado sigue
existiendo aunque su dueño quiera eliminarlo. Es la misma lógica por la que un
expediente laboral se conserva.

### El CV no viaja en la petición: lo pone el servidor

`POST /ofertas/{id}/candidaturas` solo lleva la carta. El servidor adjunta el CV
**vigente** de quien se presenta, en ese instante. Dejarlo elegir tendría dos
costes: habría que comprobar en cada llamada que el adjunto es tuyo —una
comprobación más que se puede olvidar— y permitiría presentar una versión ya
retirada. Sin CV en el perfil, un 400 con el motivo escrito.

### Crear una oferta no es publicarla

Una oferta nace en `BORRADOR` siempre, y publicar es un `PATCH` propio. No es
burocracia: publicar **avisa a toda la plantilla de una vez**, y eso no puede ser
el efecto colateral de guardar un formulario a medio escribir.

El aviso masivo sale **una sola vez**, la primera que se publica. Sin eso,
retirarla al borrador para corregir una errata y volver a publicarla avisaría a
toda la empresa otra vez de la misma vacante.

Y sí, este es el único aviso masivo del proyecto, en contra de lo que decidió la
fase F —que un aviso por cada hecho detectado es spam por diseño—. La diferencia
es la frecuencia: los excesos de jornada salen a decenas al mes y una vacante
interna a unas pocas al año. Con esa frecuencia el aviso es lo que hace que el
tablón exista; sin él, quien podría dar el paso no se entera.

### `ABIERTA` no significa que admita candidaturas

Son dos condiciones —publicada **y** en plazo— y se resuelven al leer, como los
plazos de las denuncias o la bolsa de horas extra. No hay proceso nocturno que
cierre ofertas vencidas: un estado que cambia solo obliga a mirar cuándo corrió
el proceso por última vez para saber si lo que se ve es verdad.

`admiteCandidaturas` viaja resuelto al cliente, junto con `plazoVencido` y
`yaMePresente`, para que la app no reimplemente la regla y para que pueda decir
**por qué** no se puede optar. Un botón gris sin explicación se lee como una
aplicación rota.

### Nadie valora su propia candidatura

Un GESTOR puede optar a una vacante interna como cualquiera. Cuando lo hace, esa
candidatura concreta la decide otro. Es la misma regla que la fase F aplicó a las
horas extra y, como allí, **no la puede poner un `@PreAuthorize`**: quien valora
tiene la authority; lo que falla es que el expediente sea suyo.

### Quién puede leer un CV ajeno (corregido en septiembre de 2026)

La fase B2 dejó que **descargar un adjunto fuera cosa de empresa**, con este
razonamiento escrito en el propio controlador: «un gestor necesita poder leer el
CV de su equipo». El razonamiento era correcto y la regla, demasiado ancha. Los
identificadores son números corridos, así que **cualquiera con sesión podía
bajarse el currículum de todos sus compañeros probando números**, sin que nada
en el sistema dijera por qué lo hacía.

Leer el CV de otra persona solo tiene un motivo legítimo en esta aplicación, y
esta fase lo nombra: **valorar a quien se ha presentado a una vacante**. Así que
eso es exactamente lo que se exige ahora:

- **su dueño**, siempre; o
- quien tiene `candidatura:gestionar` **y** el adjunto es el CV congelado de una
  candidatura de una oferta **de su empresa** (las dos cosas, en la misma
  consulta: que exista la candidatura no basta si es de otra empresa).

Tiene una consecuencia que conviene ver como lo que es —una mejora, no un
efecto colateral—: un gestor puede abrir **el CV que se le presentó**, y no el
que esa persona tenga hoy en su perfil. Es la misma idea que congelar el
adjunto, aplicada al permiso.

Y la foto queda, por el mismo camino, solo para su dueño: es lo único que la
aplicación pide hoy. Si algún día hay avatares en un listado de equipo, será una
decisión que se tome entonces y con su regla, no algo heredado.

### Descartar exige comentario

La asimetría es la de las correcciones de la fase E, con una vuelta de tuerca:
aquí lo lee un compañero, sobre sí mismo, en la empresa en la que sigue
trabajando mañana. Decirle que no sigue adelante sin una palabra más es la peor
forma de usar un canal de promoción interna. Seleccionar no lo pide.

## Consecuencias

**Lo que se gana.** El expediente de una candidatura es estable: lo que el gestor
leyó es lo que se decidió, y sigue ahí meses después. La garantía vive en el
esquema, no en la disciplina de quien escriba el próximo endpoint.

**`adjuntos` acumula filas.** Un CV por candidatura presentada, en el peor caso.
Con currículums de pocos MB y candidaturas contadas al año, la base aguanta de
sobra —el mismo razonamiento del ADR 007—. Si algún día dejara de aguantar, lo
correcto es archivar candidaturas antiguas con su CV, no volver a borrarlo.

**El listado del perfil cambió de significado y hubo que tocarlo.**
`findByUsuario` devolvía "los adjuntos de esta persona" y ahora eso incluye los
congelados; pasó a ser `findByUsuarioAndVigenteTrue`. Es el tipo de cambio que
compila igual y enseña de más, así que está cubierto por un test.

**Una oferta cerrada no se reabre.** Reabrirla dejaría a quien ya se presentó sin
saber si su candidatura sigue contando. Se publica otra.
