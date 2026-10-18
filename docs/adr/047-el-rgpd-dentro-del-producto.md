# 47. El RGPD dentro del producto: conservación, accesos y aviso de privacidad

**Estado:** aceptada · **Fecha:** octubre 2026

## Contexto

Con los textos legales y el alta con aprobación (ADR 046), NX Time ya decía qué
hacía con los datos. Faltaba que el producto lo hiciera:

- **Nada caducaba solo**, salvo los registros de empresa sin confirmar.
  Sesiones, códigos, avisos y candidaturas se acumulaban para siempre, y quien
  se iba de una empresa conservaba su nombre en el registro hasta que alguien
  pedía el borrado (ADR 016). El RGPD pide limitar la conservación (art. 5.1.e).
- **Nadie sabía quién había mirado qué.** Un GESTOR podía abrir el CV de una
  candidata o descargar el registro mensual de otra persona sin dejar rastro.
  La empresa responde de quién accede (arts. 5.2 y 32) y no tenía cómo
  demostrarlo.
- **Se enseñaba de más.** La fecha de nacimiento la veía cualquier GESTOR, y los
  correos copiaban motivos de ausencias, comentarios y cartas, que pueden
  revelar datos de salud.
- **Un empleado no sabía a quién dirigirse.** Lo natural es escribir a quien
  hace la aplicación, que es solo el encargado y no puede atender sus derechos
  sin la empresa.

## Decisión

### Conservación automática

`ConservacionDeDatos`, dentro de la tarea de las 03:45 (`DataDeletionScheduler`)
para no despertar la base a otra hora. Los plazos están en
`docs/cumplimiento/conservacion.md`.

- **SQL explícito**, como `PersonalDataEraser`: es código que destruye datos y
  tiene que poder leerse entero.
- **Cada paso en su transacción**, y buscando «más viejo que», no «de ayer». Un
  fallo no para los demás, y lo que no se borró se borra la noche siguiente.
- **Las sesiones, por familias enteras.** Un refresh rotado apunta a su sucesor:
  borrar uno sin el otro rompería el enlace (y el paso entero fallaría).
- **Anonimizar sin que nadie lo pida** a quien lleva cuatro años de baja desde su
  último fichaje y desde la baja. El registro de jornada se queda; lo que se
  quita es quién era. Se reutiliza `PersonalDataEraser`. Va en el contrato de
  encargo como instrucción de la empresa, que puede pedir otro plazo.

### Registro de accesos a datos ajenos

`accesos_a_datos` (V44): quién, qué, de quién y cuándo. Se anota el CV de una
candidatura, un perfil ajeno, el registro mensual de otro, el Excel de la
empresa, un expediente de denuncia y el absentismo por persona. Lo propio no se
anota.

- **Solo inserción, con un trigger**, como `acciones_de_plataforma` (V43). El
  trigger deja borrar dos cosas: lo que tiene más de dos años (el plazo) y lo de
  una empresa que ya no existe. El borrado nocturno usa el mismo
  `now() - INTERVAL '2 years'` que el trigger: con un límite calculado en Java,
  una fila en el borde haría saltar el trigger cada noche.
- **Sin claves ajenas y solo con números**: la fila sobrevive a la anonimización
  y al borrado de una cuenta, y los nombres se buscan al leer.
- **De una denuncia no se anota de quién es.** Quién la puso no queda en ninguna
  parte.
- **Se anota en una transacción propia** (`REQUIRES_NEW`). Casi todo se lee en
  transacciones de solo lectura, donde PostgreSQL no deja insertar. El test
  falla sin esto. Si anotar falla, falla la consulta: un acceso sin registrar es
  justo lo que la tabla tiene que impedir.
- **Lo ve el ADMIN** (`empresa:configurar`), desde los ajustes de la empresa.

### Minimización

- La fecha de nacimiento, solo para la propia persona y para
  `empleado:gestionar` (RRHH y ADMIN).
- Siete plantillas de correo dejan de copiar texto libre. El correo dice que hay
  algo y se lee en la aplicación.

### El aviso de privacidad de la primera entrada

Una vez por persona, en la web o en la app (lo que vea antes): su empresa es la
responsable de sus datos, NX Time los trata por encargo suyo, y a quién
dirigirse. El contacto lo pone el ADMIN (`empresas.contacto_privacidad`): un
correo o un enlace https, validado en el servidor (un `javascript:` o un
`http://` no pasan). Sin contacto, «dirígete a tu empresa».

`usuarios.aviso_privacidad_visto_en` **no está en la entidad `User`**: se lee y
se marca con sentencias propias, como `aviso_cuenta_existente_en`, para que un
`save` con una copia vieja no lo devuelva a null.

No bloquea nada, igual que el de las condiciones: informar a la plantilla es
cosa de la empresa (art. 13). Esto solo pone el contacto delante.

### El mes para responder a un borrado

`DeletionResponse.diasParaResponder`, calculado al leer como los plazos de las
denuncias: días naturales en el calendario de la empresa, negativo si ya pasó.
La prórroga de dos meses del art. 12.3 no se modela: exige avisar a la persona
con el motivo, y es una decisión de la empresa.

## Consecuencias

- La pasada de las 03:45 hace más trabajo. Con los volúmenes de hoy son unos
  pocos DELETE con índice, y el resumen sale en `/estado/tareas`.
- Una persona anonimizada sale como tal en todo, también en el registro de
  accesos.
- El contrato de encargo cambia. La versión de las condiciones se sube una sola
  vez al final del PR de J5 a J7, para que cada ADMIN las acepte una sola vez.
- Queda pendiente (J6): el registro de accesos a cada expediente lo verá solo el
  Responsable del Sistema, no cualquier ADMIN.
