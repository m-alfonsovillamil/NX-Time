# Capturas de la app Android

(Las de la web están más abajo, en [La web](#la-web).)

Rehechas el **04/09/2026**, tras aplicar la línea visual "Fichaje". Emulador
`Medium_Phone_API_35` (Android 15) contra el backend local con el perfil `demo`.

La dirección se apoya en cuatro cosas que el proyecto no estaba usando: una
escala de **formas** propia (`Theme.kt` nunca pasaba un `Shapes()`), **Sora** en
titulares y cifras, un **fondo con degradado** pintado una sola vez detrás de
todo, y un **índigo** reservado a la zona de gestión.

Son capturas reales de la aplicación hablando con la API, no maquetas: los datos
que se ven salen de los usuarios y fichajes que siembra `DemoDataSeeder`.

## Empleado

| Captura | Qué muestra |
|---|---|
| `01-login.png` | Pantalla de acceso |
| `02-login-error-credenciales.png` | El `detail` del ProblemDetail del backend llegando a la pantalla, en vez del código HTTP |
| `03-registro-empresa.png` | Alta de empresa y de su cuenta de administrador |
| `04-fichar-sin-jornada.png` | "Mi jornada" sin jornada abierta: el botón es un círculo |
| `05-fichar-trabajando.png` | Jornada iniciada. **Cronómetro en vivo** dentro del botón, que además cambia de forma para que se vea de lejos que está corriendo. Debajo, los totales del día, la semana y el mes, y el saldo de vacaciones |
| `06-fichar-en-pausa.png` | Jornada en pausa: el cronómetro se congela en vez de seguir contando |
| `07-mi-historial.png` | Historial propio, con el total neto ya descontada la pausa |
| `08-mis-ausencias.png` | Ausencias propias, con estado y respuesta del gestor |
| `09-solicitar-ausencia.png` | Formulario de solicitud |
| `10-solicitar-ausencia-validacion.png` | Validación en cliente: no sale ninguna petición a la red |
| `11-menu-empleado.png` | Menú de un empleado. En la barra inferior solo hay tres destinos: **sin "Gestión"** |
| `12-cambiar-contrasena.png` | Cambio de contraseña |

## El calendario, antes y después

| Captura | Qué muestra |
|---|---|
| `13-calendario-antes-en-ingles.png` | El defecto: en un móvil en inglés, el selector de Material salía en inglés dentro de una pantalla en español |
| `14-calendario-despues-en-espanol.png` | Ya arreglado fijando el idioma de la app: además la semana empieza en lunes |

## Gestor

| Captura | Qué muestra |
|---|---|
| `15-panel-gestion.png` | Panel de gestión de un GESTOR. **No aparece "Dar de alta a un gestor"**: esa authority (`gestor:crear`) solo la tiene ADMIN |
| `16-ausencias-pendientes.png` | Peticiones del equipo pendientes de resolver |
| `17-rechazo-motivo-obligatorio.png` | Rechazar exige motivo: sin él, el botón queda deshabilitado |
| `18-historial-equipo.png` | Historial de todo el equipo |
| `19-historial-equipo-filtrado.png` | El mismo historial filtrado por un empleado |
| `20-alta-empleado.png` | Alta de un empleado nuevo |
| `21-ausencias-resueltas.png` | Ausencias ya resueltas, con quién las resolvió |
| `22-ausencias-rechazadas.png` | El estado **Rechazada** en la misma lista: distintivo rojo frente al verde de "Aprobada", y el motivo del rechazo bajo cada petición |

## RRHH y ADMIN

Lo que exige `empleado:gestionar`, `informe:exportar`, `fichaje:corregir` y
`fichaje:auditoria`. Un GESTOR no ve nada de esto.

| Captura | Qué muestra |
|---|---|
| `23-panel-empresa.png` | Indicadores del mes: empleados activos, horas, ausencias por aprobar e **incidencias abiertas** (jornadas que cerró el proceso nocturno y nadie ha corregido), más las horas por empleado |
| `24-plantilla-informes.png` | Plantilla con el interruptor de alta/baja y la descarga de informes: Excel de la empresa y PDF por empleado |
| `25-historial-equipo-rrhh.png` | El mismo historial del equipo, pero con las acciones de cumplimiento. **"Corregir" solo aparece en jornadas cerradas**: sobre una abierta el backend responde 409, así que el botón ni se ofrece |
| `26-corregir-fichaje.png` | Formulario de corrección, precargado con las horas del fichaje y con motivo obligatorio |
| `27-traza-auditoria.png` | La traza de un fichaje corregido dos veces: qué cambió (`Salida: 17:27 h → 18:27 h`), el motivo, quién y desde qué IP. El registro es inmutable: un trigger en la base impide `UPDATE` y `DELETE` |

## Adaptación a pantalla grande

| Captura | Qué muestra |
|---|---|
| `28-tablet-rail.png` | La misma app a 2560x1600: la barra inferior se convierte en raíl lateral. No hay un segundo layout escrito en ninguna parte, lo resuelve `NavigationSuiteScaffold` |

---

`13-calendario-antes-en-ingles.png` **no se ha rehecho**: documenta un defecto ya
corregido y no se puede volver a reproducir. Las 27 restantes son del 04/09/2026.

## La web

En [`web/`](web/). Sacadas el **28/09/2026** contra el backend local con el
perfil `demo` y sobre una base recién creada. A diferencia de las de Android, **se
repiten con un comando**: `npm run capturas` en `nx-time-frontend-web/`
(`e2e/capturas.spec.ts`), con el build de producción si se lanza con `CI=1`.

| Captura | Qué muestra |
|---|---|
| `web-01-mi-jornada.png` | Mi jornada en escritorio, con la jornada en marcha: cronómetro, el resumen del día, la semana, el mes y las vacaciones, y las horas de la semana contra la jornada esperada (gráfico en SVG propio, sin librería) |
| `web-02-historial-equipo.png` | Historial del equipo para RRHH, en tabla: la jornada en curso sin «Corregir» (el backend respondería 409) y la auditoría de cada una |
| `web-03-plantilla.png` | La plantilla entera, gestores incluidos, con su departamento: desde aquí se arregla a quien la app no dejaba poner departamento |
| `web-04-analitica.png` | Analítica de absentismo y puntualidad, que solo existe en la web (ADR 022) |
| `web-05-editor-cuadrantes.png` | El editor de plantillas de cuadrante, también solo de la web: un turno partido con dos tramos por día (sin guardar: la demo no siembra cuadrantes) |
| `web-06-movil.png` | La misma jornada en un móvil: barra inferior con «Más» en vez del menú lateral |
| `web-07-movil-oscuro.png` | En tema oscuro, que sigue al del sistema |
| `web-08-menu-por-apartados.png` | El menú lateral de un ADMIN por apartados plegables (octubre de 2026): siete apartados en vez de 28 entradas seguidas, con el de la página abierta desplegado |
| `web-09-kiosco.png` | El kiosco de fichaje en una tablet (ADR 033): espera una tarjeta QR por la cámara, o que alguien busque su nombre y teclee su PIN. La imagen es la cámara de prueba de Chromium |
