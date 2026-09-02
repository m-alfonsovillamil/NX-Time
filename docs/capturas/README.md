# Capturas de la app Android

Tomadas el **29/08/2026**, en la primera ejecución de la app tras la migración a
Jetpack Compose. Emulador `Medium_Phone_API_35` (Android 15, dispositivo en
`en-US`) contra el backend local con el perfil `demo`.

Son capturas reales de la aplicación hablando con la API, no maquetas: los datos
que se ven salen de los usuarios y fichajes que siembra `DemoDataSeeder`.

## Empleado

| Captura | Qué muestra |
|---|---|
| `01-login.png` | Pantalla de acceso |
| `02-login-error-credenciales.png` | El `detail` del ProblemDetail del backend llegando a la pantalla, en vez del código HTTP |
| `03-registro-empresa.png` | Alta de empresa y de su cuenta de administrador |
| `04-fichar-sin-jornada.png` | "Mi jornada" sin jornada abierta |
| `05-fichar-trabajando.png` | Jornada iniciada, con la hora de entrada |
| `06-fichar-en-pausa.png` | Jornada en pausa |
| `07-mi-historial.png` | Historial propio, con el total neto ya descontada la pausa |
| `08-mis-ausencias.png` | Ausencias propias, con estado y respuesta del gestor |
| `09-solicitar-ausencia.png` | Formulario de solicitud |
| `10-solicitar-ausencia-validacion.png` | Validación en cliente: no sale ninguna petición a la red |
| `11-menu-empleado.png` | Menú de un empleado: sin acceso a gestión |
| `12-cambiar-contrasena.png` | Cambio de contraseña |

## El calendario, antes y después

| Captura | Qué muestra |
|---|---|
| `13-calendario-antes-en-ingles.png` | El defecto: en un móvil en inglés, el selector de Material salía en inglés dentro de una pantalla en español |
| `14-calendario-despues-en-espanol.png` | Ya arreglado fijando el idioma de la app: además la semana empieza en lunes |

## Gestor

| Captura | Qué muestra |
|---|---|
| `15-panel-gestion.png` | Panel de gestión, visible solo para el rol GESTOR |
| `16-ausencias-pendientes.png` | Peticiones del equipo pendientes de resolver |
| `17-rechazo-motivo-obligatorio.png` | Rechazar exige motivo: sin él, el botón queda deshabilitado |
| `18-historial-equipo.png` | Historial de todo el equipo |
| `19-historial-equipo-filtrado.png` | El mismo historial filtrado por un empleado |
| `20-alta-empleado.png` | Alta de un empleado nuevo |
| `21-ausencias-resueltas.png` | Ausencias ya resueltas, con quién las resolvió |
| `22-ausencias-rechazadas.png` | El estado **Rechazada** en la misma lista: distintivo rojo frente al verde de "Aprobada", y el motivo del rechazo bajo cada petición |

> `22` se tomó el **02/09/2026**, en una sesión posterior, con el entorno
> resembrado desde cero. Las demás son del 29/08.
