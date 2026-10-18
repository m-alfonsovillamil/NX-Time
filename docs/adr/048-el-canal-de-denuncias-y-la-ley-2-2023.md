# 48. El canal de denuncias a la altura de la Ley 2/2023

**Estado:** aceptada · **Fecha:** octubre 2026 · Amplía el [ADR 012](012-anonimato-estructural-en-el-canal-de-denuncias.md)

## Contexto

El canal de la fase G (ADR 012) resolvía bien lo más difícil, el anonimato
estructural. Pero tenía huecos frente a la Ley 2/2023 y a la Recomendación
1/2026 de la Autoridad Independiente de Protección del Informante (AIPI):

- **Instruía cualquier ADMIN.** La ley pide un Responsable del Sistema designado
  (art. 8), que actúe con independencia. Con tres ADMIN, los tres leían todo.
- **El acuse dependía de que alguien se acordara** de escribir en siete días.
- **No se decía lo que hay que decir**: la protección de quien informa, los
  canales externos, qué queda fuera de la protección, la vía verbal.
- **Nada caducaba.** Sin investigación, el contenido tiene que suprimirse a los
  tres meses (art. 32.3), y el libro-registro no puede pasar de diez años (art.
  26.2).
- **No había libro-registro** (art. 26), ni prórroga de la respuesta (art.
  9.2.d), ni dónde dar un domicilio para notificaciones (art. 7.2).
- **El registro de quién abre cada expediente** (ADR 047) lo veía el ADMIN. La
  AIPI recomienda reservarlo al Responsable.

## Decisión

### El Responsable del Sistema, en los ajustes de la empresa

`empresas.responsable_canal_id` (V45). Lo designa el ADMIN entre la gente
activa de la empresa. **Desde ese momento solo él tiene `denuncia:instruir`,
tenga el rol que tenga**, y los ADMIN dejan de tenerla. Sin designar, instruyen
los ADMIN, como desde la fase G, para no dejar sin canal a las empresas de antes.

- **La regla vive en un solo sitio**: `RoleAuthorities.de(User)`. Lo usan el
  principal de cada petición, las respuestas de sesión, el perfil y todos los
  servicios que preguntaban `forRole(x.getRol())`. Así lo que autoriza el
  servidor y lo que enseña el cliente no pueden decir cosas distintas.
- **Un id, no una relación JPA**: se mira en cada petición, y la empresa ya
  viene con el usuario.
- **Los avisos de denuncia nueva van solo al Responsable.** Lo de no excluir a
  nadie (ADR 012) sigue valiendo dentro de quien instruye.
- **Si le dan de baja o se le borra, deja de serlo** (`quitarDelCanal`), y
  vuelven a instruir los ADMIN. Con un Responsable de baja no instruiría nadie.
- **Sin delegados.** La ley permite que el Responsable se apoye en otros para
  investigar, pero dentro de la herramienta solo entra él: cada persona más que
  lee el canal es una persona más que puede filtrarlo. Si se va de vacaciones,
  el ADMIN designa a otro y lo comunica.
- La web, al guardar, vuelve a pedir los permisos de la sesión: al ADMIN se le
  va el canal del menú en el acto.

Comunicar el nombramiento a la AIPI en 10 días hábiles no lo puede hacer la
aplicación. Se recuerda en la pantalla, con el enlace al formulario.

### Acuse de recibo al presentarla

`acuse_recibo_en = creado_en`, y la respuesta lo dice («Esto es el acuse de
recibo»). Los siete días se cumplen siempre. El ADR 012 fechaba el acuse en el
primer contacto real; se cambia porque lo que pide la ley es **enviar el
acuse**, y un acuse automático e inmediato lo cumple sin depender de nadie. Las
denuncias de antes siguen con su regla.

No hay un mensaje automático «del sistema»: haría falta otro tipo de autor en
`denuncia_mensajes`, y la app publicada no sabría pintarlo.

### Lo que la ley obliga a decir, en la pantalla del canal

En web y app, antes de denunciar: quién lo gestiona, la vía verbal (la escribe
el ADMIN), la confidencialidad y la prohibición de represalias, que el
contenido se suprime a los tres meses sin investigación, qué queda fuera de la
protección de la ley y los canales externos (AIPI, autoridades autonómicas,
instituciones de la UE), con el enlace a «Cómo informar» de la AIPI.

### Plazos y conservación

- **Prórroga a seis meses**, una vez, antes de que venza y con motivo. El motivo
  le llega a quien denunció como un mensaje del expediente.
- **Tres meses sin investigación** (`investigacion_iniciada_en` nula, que se
  fecha al pasar a EN_INVESTIGACION): la tarea de las 03:45 borra los mensajes,
  el relato, quién la puso y su contacto, y la archiva. Queda la fila (fecha,
  categoría, estado) como evidencia del funcionamiento del canal, que la ley
  permite. El CHECK del acuse se relaja para estas: no se inventa un acuse que
  no existió. Quien instruye ve en el expediente cuántos días quedan.
- **Diez años**: el expediente entero. Va antes que la supresión en la pasada,
  para no suprimir lo que se va a borrar.
- **Las de antes de V45**: EN_INVESTIGACION y RESUELTA se dan por investigadas
  desde su acuse. Las ARCHIVADAS no se sabe, y se tratan como no investigadas,
  que es lo que protege a quien aparece en ellas.

### Supresión a mano

Quien instruye puede suprimir el relato o un mensaje porque contiene categorías
especiales, no es pertinente o se acreditó que no es veraz (arts. 30 y 32). No
se deshace: el texto se sustituye por una marca con la fecha y el motivo. El log
dice que se suprimió, no qué.

### El libro-registro

`GET /api/v1/denuncias/libro-registro.csv`: número, recepción, objeto, anónima,
estado, acuse, investigación, prórroga, fin y supresión. **Sin relato, sin
conclusión y sin nombres**: es el registro de que el canal funciona, y puede
tener que enseñarse a una autoridad. La descarga queda en el registro de
accesos.

### El registro de accesos a expedientes, para quien instruye

`/api/v1/denuncias/accesos`. En el registro general de la empresa (ADR 047), los
accesos a expedientes solo salen si quien mira tiene `denuncia:instruir`: con
Responsable designado, el ADMIN ya no los ve.

## Consecuencias

- Un EMPLEADO puede tener una authority que no da su rol. Es la segunda
  excepción a «los permisos salen del rol», tras la del operador de plataforma
  (ADR 040), y por eso va en el mismo sitio.
- La AIPI recomienda segundo factor para el Responsable: se hará obligatorio en
  la fase J8.
- La herramienta no comprueba que la empresa haya comunicado el nombramiento, ni
  que su política esté aprobada, ni que el Responsable sea independiente. Lo
  dice el contrato de encargo y la guía para clientes.
- La prórroga y la supresión a mano están en la web. La app las enseña (plazo
  ampliado, contenido suprimido, días hasta la supresión) pero no las hace.
