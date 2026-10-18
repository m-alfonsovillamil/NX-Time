# Registro de actividades de tratamiento

RGPD, artículo 30. Titular: Miguel Alfonso Villamil (NIF y contacto en
`application.titular`, `nx-time-backend/src/main/resources/application.yml`).
Sin delegado de protección de datos: no es obligatorio (art. 37) para este
tamaño ni para esta actividad. Actualizado: 17/10/2026.

## 1. Como responsable (art. 30.1)

### 1.1 Alta y relación con las empresas

| | |
|---|---|
| Finalidad | Registrar empresas, comprobar el correo, aprobar o rechazar el alta, avisar de cambios, atender peticiones |
| Base | Contrato con la empresa (6.1.b) e interés legítimo en saber con quién se trata (6.1.f) |
| Interesados | Quien registra una empresa y sus administradores |
| Datos | Nombre, apellidos, correo, nombre de la empresa, versión y fecha de las condiciones aceptadas, identificador de Google o Microsoft si se usa |
| Destinatarios | Subencargados (alojamiento, base de datos, correo) |
| Transferencias | Ver [transferencias](transferencias.md) |
| Conservación | Mientras dure la empresa; bloqueado 6 años; sin confirmar, 48 h; rechazada, en el acto |
| Seguridad | [Medidas](medidas-de-seguridad.md) |

### 1.2 Seguridad y funcionamiento del servicio

| | |
|---|---|
| Finalidad | Registros técnicos, límites de intentos, detección de abusos, informes de errores |
| Base | Interés legítimo en la seguridad (6.1.f, considerando 49) |
| Interesados | Toda persona que usa la web, la app o la API |
| Datos | IP, hora, ruta, navegador o versión de la app, intentos fallidos (en memoria), número interno de cuenta en los informes de errores (sin correos) |
| Destinatarios | Render (registros), Sentry (errores) |
| Conservación | Registros: los días que los guarda Render; errores: hasta 90 días en Sentry |

### 1.3 Panel de plataforma

| | |
|---|---|
| Finalidad | Que el operador apruebe, suspenda y contacte con las empresas, y que quede rastro de lo que mira |
| Base | Interés legítimo (6.1.f) y obligación de demostrar el cumplimiento (5.2) |
| Datos | Cifras de uso por empresa, nombre y correo de sus ADMIN, historial de acciones y consultas (`acciones_de_plataforma`) |
| Conservación | El historial, sin borrado (solo inserción) mientras dure el servicio; revisar en J5 |

### 1.4 Comunicaciones y ejercicio de derechos

| | |
|---|---|
| Finalidad | Contestar a quien escribe a privacidad@ o seguridad@ |
| Base | Interés legítimo; obligación legal en el ejercicio de derechos (6.1.c) |
| Conservación | La conversación; 3 años si es un derecho o un aviso de seguridad |

## 2. Como encargado (art. 30.2)

| | |
|---|---|
| Responsables | Cada empresa cliente aprobada en el panel de plataforma (la lista y su contacto están en él) |
| Categorías de tratamiento | Registro de jornada (fichajes, pausas, correcciones con autor y motivo, firma mensual), ausencias y sus motivos, cuadrantes, horas extra, reparto de horas por proyecto, analítica de absentismo y puntualidad, kioscos (PIN y tarjeta QR), avisos (en la app, por correo y push), canal interno de denuncias, ofertas internas y currículos, fotos de perfil, borrados a petición |
| Datos de categorías especiales | Posibles en motivos de ausencia (salud) y en denuncias; no se piden |
| Subencargados | `/legal/subencargados` |
| Transferencias | Ver [transferencias](transferencias.md) |
| Seguridad | [Medidas](medidas-de-seguridad.md) y anexo del contrato de encargo |
