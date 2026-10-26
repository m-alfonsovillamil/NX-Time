# Conservación de los datos

Un plazo por cada dato. La columna «Hoy» dice si ya se borra solo. Lo
automático corre dentro de la tarea de las 03:45, para no despertar Neon a otra
hora: `DataDeletionScheduler`, que llama a `ConservacionDeDatos` (ADR 047).
**Si se cambia un plazo aquí, se cambia allí, y al revés.**

Cada paso va en su transacción y busca «más viejo que», no «de ayer». Si una
noche falla uno, los demás siguen, y lo que no se borró se borra la noche
siguiente. El resumen de cada pasada queda en `/estado/tareas`.

| Dato | Plazo | Por qué | Hoy |
|---|---|---|---|
| Registro de jornada y su auditoría | 4 años desde el fichaje (los conserva la empresa) | Art. 34.9 ET | Se queda; lo que se quita es quién era (fila siguiente) |
| Persona dada de baja | Anonimizada cuando han pasado 4 años de su último fichaje y de su baja, aunque nadie lo pida | Fin del plazo del ET | Sí (`ConservacionDeDatos`); a petición, como antes (ADR 016) |
| Registro de empresa sin confirmar | 48 horas | No hay relación | Sí (`UnconfirmedRegistrationCleaner`) |
| Empresa rechazada | En el acto | No hay relación | Sí (ADR 046) |
| Empresa que se da de baja | 30 días desde que la pide un ADMIN (para llevarse los datos; se puede cancelar) | Condiciones, punto 9 | Sí (`borrar_empresa`, ADR 050); queda solo su nombre en `empresas_borradas` y las lápidas de su tramo común |
| Sesiones (refresh tokens) caducadas | 30 días desde que caduca la última de la familia | Detectar reutilización | Sí |
| Códigos de acceso usados, anulados o caducados | 90 días | Investigar abusos | Sí |
| Avisos dentro de la aplicación | 1 año | Ya no se miran | Sí |
| Dispositivos de push sin ver | 1 año | El token ya no vale | Sí |
| Ejecuciones de tareas programadas | 1 año | Diagnóstico | Sí |
| Fichajes sin conexión recibidos (`fichajes_recibidos`: qué se contestó a cada uno) | 30 días | No aplicar dos veces lo que la app reenvía (ADR 054); el fichaje en sí sigue en el registro de jornada | Sí |
| Currículos y fotos sustituidos | La noche siguiente, si ninguna candidatura los usa | Sin finalidad | Sí |
| Candidaturas resueltas | 1 año desde la resolución | Reclamaciones del proceso | Sí |
| Registro de accesos a datos personales | 2 años | Demostrar quién consultó qué (art. 5.2) | Sí; antes no se puede borrar (trigger de V44) |
| Canal de denuncias: contenido sin investigación | 3 meses: se suprimen relato, mensajes, quién la puso y su contacto, y se archiva | Ley 2/2023, art. 32.3 | Sí (ADR 048); queda la fila como evidencia |
| Canal de denuncias: expedientes y libro-registro | 10 años desde la recepción | Ley 2/2023, art. 26.2 | Sí (ADR 048) |
| Canal de denuncias: textos con categorías especiales, no pertinentes o no veraces | En cuanto se detectan | Ley 2/2023, arts. 30 y 32 | A mano, por quien instruye (queda la marca) |
| Historial del panel de plataforma | Mientras dure el servicio | Demostrar cómo se ha tratado a cada empresa (art. 5.2): solo lleva el nombre de la empresa y el correo del operador | Se guarda, decidido en J5 |
| Registros del servidor (Render) | Lo que guarde Render (días) | Seguridad | Del proveedor |
| Informes de errores (Sentry) | Hasta 90 días | Arreglar fallos | Del proveedor |
| Copias de seguridad | 30 días, y nunca menos de 7 copias | Recuperación | Sí (`copia-neon.ps1`) |
| Correos de privacidad y seguridad | 3 años | Demostrar cómo se atendió | A mano |

## Lo que se minimiza, además de conservarlo menos

- **La fecha de nacimiento** solo la ven la propia persona y quien gestiona la
  plantilla (`empleado:gestionar`: RRHH y ADMIN). Antes la veía cualquier GESTOR.
- **Los correos no copian texto libre**: motivos de ausencias y correcciones,
  comentarios, disputas y cartas de presentación se leen en la aplicación. Un
  motivo puede revelar datos de salud, y el correo pasa por proveedores y
  buzones que no controlamos.

## Los derechos de las personas

- **Empleados**: la responsable de sus datos es su empresa, así que los
  derechos (acceso, rectificación, supresión, oposición, limitación) se
  ejercen ante ella. La aplicación se lo dice a cada persona la primera vez
  que entra, con el contacto que pone el ADMIN en los ajustes. Para la
  supresión hay un flujo propio (ADR 016), con el mes del art. 12.3 contado en
  pantalla; la oposición y la limitación las resuelve la empresa, y NX Time la
  ayuda como encargado (contrato de encargo, apartado de asistencia).
- **Administradores y quien escribe a NX Time**: en estos tratamientos NX Time
  es responsable. Se atienden en el correo de privacidad, en el mes del art.
  12.3.
