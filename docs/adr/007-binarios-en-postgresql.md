# 7. El CV y la foto, en PostgreSQL y en una tabla aparte

**Estado:** aceptada · **Fecha:** septiembre 2026

## Contexto

La ficha de empleado gana dos ficheros: un currículum en PDF y una foto de
perfil. Hay tres sitios donde ponerlos.

1. **El disco del servidor.** Lo más simple de escribir y lo primero que se
   rompe aquí: el contenedor de Render tiene un sistema de ficheros **efímero**,
   así que un CV subido hoy no existe después del siguiente despliegue —y Render
   despliega en cada push. Falla en silencio y semanas después, que es la peor
   combinación.
2. **Un bucket externo** (S3, R2, Cloudinary). Es lo correcto cuando el volumen
   crece: los bytes salen de la base, se sirven por CDN y se pagan por uso. A
   cambio hay que dar de alta un servicio de un tercero, guardar sus credenciales
   y renovarlas. Este proyecto tiene que poder levantarse entero con un
   `docker compose up` y presentarse en una entrevista sin pedirle a nadie una
   cuenta de AWS.
3. **La propia base de datos**, como `bytea`.

Los números importan para elegir: el CV se limita a 5 MB y la foto se reescala
en el servidor a 256×256 (unos 30 KB). Una plantilla de cien personas son ~500 MB
en el peor caso y en la práctica mucho menos, porque casi nadie sube un PDF de
cinco megas.

## Decisión

**Los binarios van en PostgreSQL**, y **en una tabla distinta de sus metadatos**:

```
adjuntos                       adjunto_datos
  id, empresa_id, usuario_id     adjunto_id  (PK y FK)
  tipo (CV | FOTO)               contenido   BYTEA
  nombre_original, mime
  tamano_bytes, subido_en
```

Las dos tablas no son un capricho de normalización, son un requisito técnico.
Si el `bytea` viviera en la misma fila, **Hibernate lo cargaría en cada
`findById`** aunque nadie pidiera el contenido: listar los adjuntos de una
empresa se traería todos los currículums a memoria. Lo que parece la solución
—`@Basic(fetch = LAZY)` sobre el campo binario— **solo funciona con *bytecode
enhancement* de Hibernate**, que aquí no está activado; sin él la anotación no
da error, simplemente se ignora. Un `LAZY` que no es lazy es peor que no
ponerlo, porque invita a confiarse.

Con dos tablas la entidad `Adjunto` no ve nunca el contenido, y este se sirve
con una consulta propia y en *streaming* al descargarlo, con el mismo patrón que
ya usan los informes (`StreamingResponseBody`).

Decisiones que van con esto:

- **El MIME se valida por los primeros bytes del fichero**, no por la extensión
  ni por el `Content-Type` que manda el cliente: los dos los elige quien sube.
  Un `.exe` renombrado a `.pdf` no pasa.
- **La foto se reescala en el servidor** a 256×256 JPEG con `javax.imageio`, sin
  dependencia nueva. Si el reescalado viviera solo en la app, cualquier cliente
  que llamara a la API directamente podría dejar 5 MB en la base, y esa foto
  viajaría entera en cada pantalla que enseñe el avatar.
- **Un CV y una foto vigentes por persona** (`UNIQUE(usuario_id, tipo)`): subir
  otro reemplaza el anterior. La aplicación no ofrece un historial de
  currículums, y guardarlo sin que nadie pueda verlo sería solo ocupar espacio.

## Consecuencias

- Las copias de seguridad de la base incluyen los ficheros: no hay dos sistemas
  que puedan quedar desincronizados, ni un backup que restaure filas apuntando a
  objetos que ya no están.
- El plan gratuito de Neon tiene un límite de almacenamiento, así que **esto no
  escala indefinidamente**. El día que deje de valer, lo que cambia es el
  cuerpo de una clase: nadie fuera de `AttachmentService` sabe dónde viven los
  bytes.
- La fase H (ofertas internas) congelará el `adjunto_id` del CV que se adjuntó a
  cada candidatura. **Entonces habrá que dejar de borrar la fila vieja al
  reemplazar el CV**, o el gestor perdería el currículum que leyó en enero. Hoy
  no hay quien referencie un adjunto, y se resuelve en su fase.
