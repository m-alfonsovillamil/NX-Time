# Conservación de los datos

Un plazo por cada dato. La columna «Hoy» dice si ya se borra solo; lo que falta
lo aplica la fase J5 del plan J, dentro de la tarea de las 03:45 (para no
despertar Neon a otra hora).

| Dato | Plazo | Por qué | Hoy |
|---|---|---|---|
| Registro de jornada y su auditoría | 4 años desde el fichaje (los conserva la empresa) | Art. 34.9 ET | Se anonimiza a petición tras 4 años (ADR 016) |
| Persona dada de baja | Anonimizada a los 4 años de su último fichaje, aunque nadie lo pida | Fin del plazo del ET | **No** (J5) |
| Registro de empresa sin confirmar | 48 horas | No hay relación | Sí (`UnconfirmedRegistrationCleaner`) |
| Empresa rechazada | En el acto | No hay relación | Sí (ADR 046) |
| Empresa que se da de baja | 30 días tras entregarle sus datos | Condiciones, punto 9 | A mano por ahora (J5-J7) |
| Refresh tokens caducados | 30 días | Detectar reutilización | **No** (J5) |
| Códigos de acceso usados o caducados | 90 días | Investigar abusos | **No** (J5) |
| Avisos dentro de la aplicación | 1 año | Ya no se miran | **No** (J5) |
| Dispositivos de push sin ver | 1 año | El token ya no vale | **No** (J5) |
| Ejecuciones de tareas programadas | 1 año | Diagnóstico | **No** (J5) |
| Currículos sin candidatura | En cuanto quedan huérfanos | Sin finalidad | **No** (J5) |
| Candidaturas cerradas | 1 año | Reclamaciones del proceso | **No** (J5) |
| Canal de denuncias: contenido sin investigación | 3 meses | Ley 2/2023, art. 32 | **No** (J6) |
| Canal de denuncias: libro-registro | 10 años | Ley 2/2023, art. 26 | **No** (J6) |
| Historial del panel de plataforma | Mientras dure el servicio | Demostrar el cumplimiento (art. 5.2) | Se guarda; revisar en J5 |
| Registros del servidor (Render) | Lo que guarde Render (días) | Seguridad | Del proveedor |
| Informes de errores (Sentry) | Hasta 90 días | Arreglar fallos | Del proveedor |
| Copias de seguridad | 30 días, y nunca menos de 7 copias | Recuperación | Sí (`copia-neon.ps1`) |
| Correos de privacidad y seguridad | 3 años | Demostrar cómo se atendió | A mano |
